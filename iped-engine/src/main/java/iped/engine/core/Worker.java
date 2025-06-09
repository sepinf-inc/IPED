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
package iped.engine.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.CaseData;
import iped.engine.localization.Messages;
import iped.engine.task.AbstractTask;
import iped.engine.task.TaskInstaller;
import iped.engine.util.UIPropertyListenerProvider;
import iped.engine.util.Util;
import iped.exception.IPEDException;

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

    private static final long MIN_WAIT_TIME_TO_SEND_QUEUE_END = 1000;
    private static volatile long lastItemProcessingTime = 0;

    public IndexWriter writer;
    String baseFilePath;

    public volatile AbstractTask runningTask;
    public List<AbstractTask> tasks = new ArrayList<AbstractTask>();
    private AbstractTask firstTask;
    private int itemsBeingProcessed = 0;

    public enum STATE {
        RUNNING, PAUSING, PAUSED
    }

    public volatile STATE state = STATE.RUNNING;

    public Manager manager;
    public Statistics stats;
    public File output;
    public CaseData caseData;
    public volatile Exception exception;
    public volatile IItem evidence;
    public final int id;

    private boolean waiting = false;

    private void incItemsBeingProcessed() {
        itemsBeingProcessed++;
        manager.getProcessingQueues().incItemsBeingProcessed();
    }

    public void decItemsBeingProcessed() {
        itemsBeingProcessed--;
        manager.getProcessingQueues().decItemsBeingProcessed();
    }

    public Worker(int k, CaseData caseData, IndexWriter writer, File output, Manager manager) throws Exception {
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
                UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
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
        synchronized (this) {
            this.interrupt();
            this.wait();
        }
        if (exception != null) {
            throw exception;
        }
    }

    public synchronized boolean isWaiting() {
        return this.waiting;
    }

    public void processNextQueue() {
        synchronized(this) {
            this.notifyAll();
        }
    }

    /**
     * Processa o item em todas as tarefas instaladas. Caso ocorra exceção não
     * esperada, armazena exceção para abortar processamento.
     *
     * @param evidence
     *            Item a ser processado
     */
    private void process(IItem evidence) {

        IItem prevEvidence = this.evidence;
        if (!evidence.isQueueEnd()) {
            this.evidence = evidence;
        }

        try {

            LOGGER.debug("{} Processing {} ({} bytes)", getName(), evidence.getPath(), evidence.getLength()); //$NON-NLS-1$

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
                || (time == ProcessTime.AUTO && manager.getProcessingQueues().getCurrentQueueSize() < 100 * manager.getNumWorkers())) {
            manager.getProcessingQueues().addItemFirstNonBlocking(evidence);
        } // caso contrário processa o item no worker atual
        else {
            if (!evidence.isQueueEnd()) {
                incItemsBeingProcessed();
            }
            long t = System.nanoTime() / 1000;

            Util.calctrackIDAndUpdateID(caseData, evidence);

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
                boolean sleep = false;
                while (evidence == null) {
                    if (sleep) {
                        // this should be very rare
                        sleep = false;
                        Thread.sleep(100);
                    }
                    synchronized (manager.getProcessingQueues()) {
                        evidence = manager.getProcessingQueues().pollFromCurrentQueue();
                        if (evidence == null) {
                            sleep = true;
                            continue;
                        }
                        if (!evidence.isQueueEnd()) {
                            incItemsBeingProcessed();
                        }
                    }
                }


                if (!evidence.isQueueEnd()) {
                    lastItemProcessingTime = System.currentTimeMillis();

                    process(evidence);

                } else {
                    IItem queueEnd = evidence;
                    if (manager.getProcessingQueues().isNoItemInQueueOrBeingProcessed()) {
                        manager.getProcessingQueues().addToCurrentQueue(queueEnd);
                        evidence = null;

                        LOGGER.debug(this.getName() + " going to wait queue change.");
                        synchronized(this) {
                            try {
                                waiting = true;
                                this.wait();
                            } finally {
                                waiting = false;
                            }
                        }
                    } else {
                        manager.getProcessingQueues().addToCurrentQueue(queueEnd);
                        long timeSinceLastItemProcessed = System.currentTimeMillis() - lastItemProcessingTime;
                        if (itemsBeingProcessed > 0 && timeSinceLastItemProcessed >= MIN_WAIT_TIME_TO_SEND_QUEUE_END) {
                            LOGGER.debug(
                                    this.getName() + " Queue size = "
                                            + manager.getProcessingQueues().getCurrentQueueSize()
                                    + " itemsInThisWorker = " + itemsBeingProcessed + " itemsInAllWorkers = "
                                            + manager.getProcessingQueues().getItemsBeingProcessed());
                            process(queueEnd);

                        }
                    }
                }

            } catch (InterruptedException e) {
                if (manager.getProcessingQueues().getCurrentQueuePriority() == null) {
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
