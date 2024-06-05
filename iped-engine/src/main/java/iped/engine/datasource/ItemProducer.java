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
package iped.engine.datasource;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.localization.Messages;
import iped.engine.task.SkipCommitedTask;
import iped.engine.util.UIPropertyListenerProvider;
import iped.utils.HashValue;

/**
 * Responsável por instanciar e executar o contador e o produtor de itens do
 * caso que adiciona os itens a fila de processamento. Podem obter os itens de
 * diversas fontes de dados: pastas, relatórios do UFED, imagens forenses ou
 * casos do IPED.
 *
 */
public class ItemProducer extends Thread implements Closeable {

    private static Logger LOGGER = LoggerFactory.getLogger(ItemProducer.class);

    private final ICaseData caseData;
    private final boolean listOnly;
    private List<File> datasources;
    private File output;
    private Manager manager;
    private DataSourceReader currentReader;
    private ArrayList<DataSourceReader> supportedReaders = new ArrayList<DataSourceReader>();
    private ArrayList<DataSourceReader> instantiatedReaders = new ArrayList<DataSourceReader>();

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

        Class<? extends DataSourceReader>[] readerList = new Class[] { SleuthkitReader.class,
                IPEDReader.class, UfedXmlReader.class, AD1DataSourceReader.class,
                FolderTreeReader.class // deve ser o último
        };

        for (Class<? extends DataSourceReader> srcReader : readerList) {
            Constructor<? extends DataSourceReader> constr = srcReader.getConstructor(ICaseData.class, File.class,
                    boolean.class);
            supportedReaders.add(constr.newInstance(caseData, output, listOnly));
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
    public void close() throws IOException {
        for (DataSourceReader reader : instantiatedReaders) {
            reader.close();
        }
    }

    @Override
    public void run() {
        File currSource = null;
        try {
            for (File source : datasources) {
                currSource = source;
                if (Thread.interrupted()) {
                    throw new InterruptedException(Thread.currentThread().getName() + " interrupted."); //$NON-NLS-1$
                }

                if (listOnly) {
                    UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", 0, //$NON-NLS-1$
                            Messages.getString("ItemProducer.Adding") + source.getAbsolutePath() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                    LOGGER.info("Adding '{}'", source.getAbsolutePath()); //$NON-NLS-1$
                }

                for (DataSourceReader srcReader : supportedReaders) {
                    if (srcReader.isSupported(source)) {
                        Constructor<? extends DataSourceReader> constr = srcReader.getClass()
                                .getConstructor(ICaseData.class, File.class, boolean.class);
                        srcReader = constr.newInstance(caseData, output, listOnly);
                        instantiatedReaders.add(srcReader);
                        currentReader = srcReader;
                        srcReader.read(source);
                        break;
                    }

                }

                // executed only when restarting interrupted processing
                Set<HashValue> parentsWithLostSubitems = (Set<HashValue>) caseData
                        .getCaseObject(SkipCommitedTask.PARENTS_WITH_LOST_SUBITEMS);
                if (parentsWithLostSubitems != null && parentsWithLostSubitems.size() > 0) {
                    try (IPEDReader reader = new IPEDReader(caseData, output, listOnly)) {
                    	reader.read(parentsWithLostSubitems, manager);	
                    }
                }

            }
            if (!listOnly) {
                Item evidence = new Item();
                evidence.setPath("[queue-end]");
                evidence.setQueueEnd(true);
                Manager.getInstance().addItemToQueue(evidence);

            } else {
                LOGGER.info("Total items found: {}", caseData.getDiscoveredEvidences()); //$NON-NLS-1$
            }
            UIPropertyListenerProvider.getInstance().firePropertyChange("discoverEnded", 0, 0);

        } catch (Throwable e) {
            if (manager.exception == null) {
                String source = currSource != null ? currSource.getAbsolutePath() : "";
                Exception e1 = new Exception("Error decoding datasource " + source);
                e1.initCause(e);
                manager.exception = e1;
            }
        }

    }

}
