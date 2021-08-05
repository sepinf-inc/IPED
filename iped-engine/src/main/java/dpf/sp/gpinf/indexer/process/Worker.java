/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.localization.Messages;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.TaskInstaller;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.ICaseData;
import iped3.IItem;

/**
 * Responsável por retirar um item da fila e enviá-lo para cada tarefa de
 * processamento instalada: análise de assinatura, hash, expansão de itens,
 * indexação, carving, etc.
 *
 * São executados vários Workers paralelamente. Cada Worker possui instâncias
 * próprias das tarefas, para evitar problemas de concorrência.
 *
 * Caso haja uma exceção não esperada, ela é armazenada para que possa ser
 * detectada pelo manager.
 */
public class Worker extends Thread {

    private static Logger LOGGER = LoggerFactory.getLogger(Worker.class);

    private static String workerNamePrefix = "Worker-"; //$NON-NLS-1$

    public IndexWriter writer;
    String baseFilePath;

    public volatile AbstractTask runningTask;
    public List<AbstractTask> tasks = new ArrayList<AbstractTask>();
    public AbstractTask firstTask;
    public volatile int itensBeingProcessed = 0;

    public enum STATE {
        RUNNING, PAUSING, PAUSED
    }

    public volatile STATE state = STATE.RUNNING;

    public Manager manager;
    public Statistics stats;
    public File output;
    public ICaseData caseData;
    public volatile Exception exception;
    public volatile IItem evidence;
    public final int id;

    public Worker(int k, ICaseData caseData, IndexWriter writer, File output, Manager manager) throws Exception {
        super(new ThreadGroup(workerNamePrefix + k), workerNamePrefix + k); // $NON-NLS-1$
        id = k;
        this.caseData = caseData;
        this.writer = writer;
        this.output = output;
        this.manager = manager;
        this.stats = manager.stats;
        baseFilePath = output.getParentFile().getAbsolutePath();

        if (k == 0) {
            LOGGER.info("Starting Tika"); //$NON-NLS-1$
        }

        TaskInstaller taskInstaller = new TaskInstaller();
        taskInstaller.installProcessingTasks(this);
        doTaskChaining();
        initTasks();

    }

    private void doTaskChaining() {
        firstTask = tasks.get(0);
        for (int i = 0; i < tasks.size() - 1; i++) {
            tasks.get(i).setNextTask(tasks.get(i + 1));
        }
    }

    private void initTasks() throws Exception {
        for (AbstractTask task : tasks) {
            if (this.getName().equals(workerNamePrefix + 0)) {
                LOGGER.info("Starting " + task.getName()); //$NON-NLS-1$
                WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.getString("Worker.Starting") + task.getName()); //$NON-NLS-1$
            }
            task.init(ConfigurationManager.get());
        }

    }

    private void finishTasks() throws Exception {
        for (AbstractTask task : tasks) {
            task.finish();
        }
    }

    public void finish() throws Exception {
        this.interrupt();
        synchronized (this) {
            this.wait();
        }
        if (exception != null) {
            throw exception;
        }
    }

    public void processNextQueue() {
        synchronized(this) {
            this.notifyAll();
        }
    }

    /**
     * Alguns itens ainda não tem um File setado, como report do FTK1.
     *
     * @param evidence
     */
    private void checkFile(IItem evidence) {
        String filePath = evidence.getFileToIndex();
        if (evidence.getFile() == null && !filePath.isEmpty()) {
            File file = Util.getResolvedFile(baseFilePath, filePath);
            evidence.setFile(file);
            evidence.setLength(file.length());
        }
    }

    /**
     * Processa o item em todas as tarefas instaladas. Caso ocorra exceção não
     * esperada, armazena exceção para abortar processamento.
     *
     * @param evidence
     *            Item a ser processado
     */
    public void process(IItem evidence) {

        IItem prevEvidence = this.evidence;
        if (!evidence.isQueueEnd()) {
            this.evidence = evidence;
        }

        try {

            LOGGER.debug("{} Processing {} ({} bytes)", getName(), evidence.getPath(), evidence.getLength()); //$NON-NLS-1$

            checkFile(evidence);

            // Loop principal que executa cada tarefa de processamento
            /*
             * for(AbstractTask task : tasks) if(!evidence.isToIgnore()){
             * processTask(evidence, task); }
             */
            firstTask.processAndSendToNextTask(evidence);

        } catch (Throwable t) {
            // ABORTA PROCESSAMENTO NO CASO DE QQ OUTRO ERRO
            if (exception == null) {
                if (t instanceof IPEDException)
                    exception = (IPEDException) t;
                else {
                    exception = new Exception(this.getName() + " Error while processing " + evidence.getPath() + " (" //$NON-NLS-1$ //$NON-NLS-2$
                            + evidence.getLength() + "bytes)"); //$NON-NLS-1$
                    exception.initCause(t);
                }
            }

        }

        this.evidence = prevEvidence;

    }

    /**
     * Processa ou enfileira novo item criado (subitem de zip, pst, carving, etc).
     *
     * @param evidence
     *            novo item a ser processado.
     */
    public void processNewItem(IItem evidence) {
        processNewItem(evidence, ProcessTime.AUTO);
    }

    public enum ProcessTime {
        AUTO, NOW, LATER
    }

    public void processNewItem(IItem evidence, ProcessTime time) {
        caseData.incDiscoveredEvidences(1);
        // Se a fila está pequena, enfileira
        if (time == ProcessTime.LATER
                || (time == ProcessTime.AUTO && caseData.getItemQueue().size() < 10 * manager.getWorkers().length)) {
            caseData.getItemQueue().addFirst(evidence);
        } // caso contrário processa o item no worker atual
        else {
            long t = System.nanoTime() / 1000;
            process(evidence);
            runningTask.addSubitemProcessingTime(System.nanoTime() / 1000 - t);
        }

    }

    @Override
    public void run() {

        LOGGER.info("{} started.", getName()); //$NON-NLS-1$

        while (!this.isInterrupted() && exception == null) {

            try {
                evidence = null;
                evidence = caseData.getItemQueue().takeFirst();

                if (!evidence.isQueueEnd()) {
                    process(evidence);

                } else {
                    IItem queueEnd = evidence;
                    if (manager.numItensBeingProcessed() == 0 && caseData.getItemQueue().size() == 0) {
                        caseData.getItemQueue().addLast(queueEnd);
                        process(queueEnd);
                        evidence = null;
                        synchronized(this) {
                            LOGGER.debug(this.getName() + " going to wait notify!");
                            this.wait();
                        }
                    } else {
                        LOGGER.debug(this.getName() + " Queue size = " + caseData.getItemQueue().size()
                                + " itemsInThisWorker = " + itensBeingProcessed + " itemsInAllWorkers = "
                                + manager.numItensBeingProcessed());

                        caseData.getItemQueue().addLast(queueEnd);
                        if (itensBeingProcessed > 0) {
                            process(queueEnd);
                        } else {
                            // no items accumulated in this worker, wait some time to increase
                            // the chance of other worker taking the queue-end
                            // Thread.sleep(1000);
                        }
                    }
                }

            } catch (InterruptedException e) {
                if (caseData.getCurrentQueuePriority() == null) {
                    try {
                        finishTasks();
                    } catch (Exception e1) {
                        if (exception == null) {
                            exception = e1;
                        }
                    } finally {
                        synchronized (this) {
                            this.notify();
                        }
                    }
                    break;
                }
            }
        }

        if (evidence == null) {
            LOGGER.info("{} finished.", getName()); //$NON-NLS-1$
        } else {
            AbstractTask task = runningTask;
            if (task != null)
                task.interrupted();
            LOGGER.info("{} interrupted on {} ({} bytes)", getName(), evidence.getPath(), evidence.getLength()); //$NON-NLS-1$
        }
    }

}
