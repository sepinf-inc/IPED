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
package iped.app.ui;

import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.columns.ColumnsManagerUI;
import iped.engine.config.ConfigurationManager;
import iped.engine.core.EvidenceStatus;
import iped.engine.core.Manager;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.task.ParsingTask;
import iped.engine.task.SignatureTask;
import iped.parsers.standard.StandardParser;

public class UICaseDataLoader extends SwingWorker<Void, Integer> {

    private static Logger LOGGER = LoggerFactory.getLogger(UICaseDataLoader.class);

    private boolean updateItems;

    private TreeViewModel treeModel;
    private Manager manager;

    public UICaseDataLoader(Manager manager) {
        this(manager, false);
    }

    public UICaseDataLoader(Manager manager, boolean updateItems) {
        this.manager = manager;
        this.updateItems = updateItems;
    }

    @Override
    protected void process(List<Integer> chunks) {
        // App.get().setSize(1350, 500);

        App.get().dialogBar.setLocationRelativeTo(App.get());

        if (!this.isDone()) {
            App.get().dialogBar.setVisible(true);
        }

    }

    @Override
    protected Void doInBackground() {
        publish(0);

        try {
            // ImageIO.setUseCache(false);

            if (updateItems)
                App.get().appCase.close();

            if (!App.get().isMultiCase) {
                IPEDSource singleCase = null;
                if (manager == null)
                    singleCase = new IPEDSource(App.get().casesPathFile);
                else
                    singleCase = new IPEDSource(App.get().casesPathFile, manager.getIndexWriter());

                App.get().appCase = new IPEDMultiSource(singleCase);
            } else
                App.get().appCase = new IPEDMultiSource(App.get().casesPathFile);

            checkIfProcessingFinished(App.get().appCase);

            App.get().appCase.checkImagePaths();
            App.get().appCase.getMultiBookmarks().addSelectionListener(App.get().getViewerController().getHtmlLinkViewer());
            App.get().getViewerController().notifyAppLoaded();

            if (!updateItems) {
                App.get().appGraphAnalytics.initGraphService();

                LOGGER.info("Loading Columns"); //$NON-NLS-1$
                App.get().resultsModel.initCols();
                App.get().resultsTable.setRowSorter(new ResultTableRowSorter());

                SignatureTask.installCustomSignatures();
                ParsingTask.setupParsingOptions(ConfigurationManager.get());
                StandardParser autoParser = new StandardParser();
                App.get().setAutoParser(autoParser);

                FileProcessor exibirAjuda = new FileProcessor(-1, false);
                exibirAjuda.execute();

                LOGGER.info("Listing all items"); //$NON-NLS-1$
                UICaseSearcherFilter pesquisa = new UICaseSearcherFilter(new MatchAllDocsQuery());
                pesquisa.execute();
                LOGGER.info("Listing all items Finished"); //$NON-NLS-1$
            } else {
                App.get().notifyCaseDataChanged();
            }

            treeModel = new TreeViewModel();

        } catch (Throwable e) {
            e.printStackTrace();
            showErrorDialog(e);
        }

        return null;
    }

    private void checkIfProcessingFinished(IPEDMultiSource multiSource) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                HashMap<String, List<String>> failedCases = new HashMap<>();
                for (IPEDSource source : multiSource.getAtomicSources()) {
                    EvidenceStatus status = new EvidenceStatus(source.getCaseDir());
                    List<String> failedEvidences = status.getFailedEvidences();
                    if (manager == null && (failedEvidences == null || !failedEvidences.isEmpty())) {
                        failedCases.put(source.getCaseDir().getAbsolutePath(), failedEvidences);
                    }
                }
                StringBuilder message = new StringBuilder();
                if (!failedCases.isEmpty()) {
                    if (multiSource.getAtomicSources().size() > 1) {
                        message.append(Messages.getString("ProcessingNotFinished.cases"));
                    }
                    int i = 0;
                    for (String failedCase : failedCases.keySet()) {
                        message.append("\n");
                        if (multiSource.getAtomicSources().size() > 1) {
                            message.append("\n" + (++i) + ". " + failedCase);
                        }
                        List<String> evidences = failedCases.get(failedCase);
                        if (evidences != null) {
                            for (int j = 0; j < evidences.size(); j++) {
                                message.append("\n        " + (j + 1) + ". ");
                                message.append(Messages.getString("ProcessingNotFinished.evidence"));
                                message.append(" " + evidences.get(j));
                            }
                        }
                    }
                }
                if (message.length() > 0) {
                    JOptionPane.showMessageDialog(App.get(), Messages.getString("ProcessingNotFinished.message") + message, Messages.getString("ProcessingNotFinished.title"), JOptionPane.WARNING_MESSAGE);
                }
            }
        });
    }

    private void showErrorDialog(final Throwable e) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                App.get().dialogBar.setVisible(false);
                String msg = e.getMessage();
                if (msg == null && e.getCause() != null) {
                    msg = e.getCause().getMessage();
                }
                JOptionPane.showMessageDialog(App.get(), Messages.getString("AppLazyInitializer.errorMsg.line1") //$NON-NLS-1$
                        + Messages.getString("AppLazyInitializer.errorMsg.line2") //$NON-NLS-1$
                        + App.get().getLogConfiguration().getLogFile() + Messages.getString("AppLazyInitializer.errorMsg.line3") + msg, // $NON-NLS-1$
                        Messages.getString("AppLazyInitializer.errorTitle"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            }
        });
    }

    @Override
    public void done() {
        try {
            CategoryTreeModel.install();
            App.get().filterManager.loadFilters();
            BookmarksController.get().updateUIandHistory();

            App.get().tree.setModel(treeModel);
            App.get().tree.setLargeModel(true);
            App.get().tree.setCellRenderer(new TreeCellRenderer());

            if (updateItems) {
                ColumnsManagerUI.getInstance().dispose();
                App.get().appletListener.updateFileListing();
            }
        } finally {
            App.get().dialogBar.setVisible(false);
        }
    }

}
