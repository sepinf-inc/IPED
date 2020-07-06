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
package dpf.sp.gpinf.indexer.datasource;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.task.SkipCommitedTask;
import dpf.sp.gpinf.indexer.util.HashValue;
import gpinf.dev.data.Item;
import iped3.ICaseData;

/**
 * Responsável por instanciar e executar o contador e o produtor de itens do
 * caso que adiciona os itens a fila de processamento. Podem obter os itens de
 * diversas fontes de dados: pastas, relatórios do FTK, imagens forenses ou
 * casos do IPED.
 *
 */
public class ItemProducer extends Thread {

    private static Logger LOGGER = LoggerFactory.getLogger(ItemProducer.class);

    private final ICaseData caseData;
    private final boolean listOnly;
    private List<File> datasources;
    private File output;
    private Manager manager;
    private DataSourceReader currentReader;
    private ArrayList<DataSourceReader> sourceReaders = new ArrayList<DataSourceReader>();

    public ItemProducer(Manager manager, ICaseData caseData, boolean listOnly, List<File> datasources, File output)
            throws Exception {
        this.caseData = caseData;
        this.listOnly = listOnly;
        this.datasources = datasources;
        this.output = output;
        this.manager = manager;

        installDataSourceReaders();
    }

    private void installDataSourceReaders() throws Exception {

        Class<? extends DataSourceReader>[] readerList = new Class[] { FTK3ReportReader.class, SleuthkitReader.class,
                IPEDReader.class, UfedXmlReader.class, AD1DataSourceReader.class, IpedCaseReader.class,
                FolderTreeReader.class // deve ser o último
        };

        for (Class<? extends DataSourceReader> srcReader : readerList) {
            Constructor<? extends DataSourceReader> constr = srcReader.getConstructor(ICaseData.class, File.class,
                    boolean.class);
            sourceReaders.add(constr.newInstance(caseData, output, listOnly));
        }
    }

    public String currentDirectory() {
        if (currentReader != null) {
            return currentReader.currentDirectory();
        } else {
            return null;
        }
    }

    @Override
    public void run() {
        try {
            for (File source : datasources) {
                if (Thread.interrupted()) {
                    throw new InterruptedException(Thread.currentThread().getName() + " interrupted."); //$NON-NLS-1$
                }

                if (listOnly) {
                    WorkerProvider.getInstance().firePropertyChange("mensagem", 0, //$NON-NLS-1$
                            Messages.getString("ItemProducer.Adding") + source.getAbsolutePath() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                    LOGGER.info("Adding '{}'", source.getAbsolutePath()); //$NON-NLS-1$
                }

                int alternativeFiles = 0;
                for (DataSourceReader srcReader : sourceReaders) {
                    if (srcReader.isSupported(source)) {
                        currentReader = srcReader;
                        alternativeFiles += srcReader.read(source);
                        break;
                    }

                }
                caseData.incAlternativeFiles(alternativeFiles);

                // executed only when restarting interrupted processing
                Set<HashValue> parentsWithLostSubitems = (Set<HashValue>) caseData
                        .getCaseObject(SkipCommitedTask.PARENTS_WITH_LOST_SUBITEMS);
                if (parentsWithLostSubitems != null && parentsWithLostSubitems.size() > 0) {
                    IPEDReader reader = new IPEDReader(caseData, output, listOnly);
                    reader.read(parentsWithLostSubitems, manager);
                }

            }
            if (!listOnly) {
                Item evidence = new Item();
                evidence.setQueueEnd(true);
                // caseData.addEvidenceFile(evidence);

            } else {
                LOGGER.info("Total items found: {}", caseData.getDiscoveredEvidences()); //$NON-NLS-1$
            }
            WorkerProvider.getInstance().firePropertyChange("taskSize", 0, //$NON-NLS-1$
                    (int) (caseData.getDiscoveredVolume() / 1000000));

        } catch (Throwable e) {
            if (manager.exception == null) {
                Exception e1 = new Exception();
                e1.initCause(e);
                manager.exception = e1;
            }
        }

    }

}
