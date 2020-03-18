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
package dpf.sp.gpinf.indexer.desktop;

import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.IViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;

public class InicializarBusca extends SwingWorker<Void, Integer> {

    private static Logger LOGGER = LoggerFactory.getLogger(InicializarBusca.class);

    private boolean updateItems;

    private AppSearchParams appSearchParams = null;
    private TreeViewModel treeModel;
    private Manager manager;

    public InicializarBusca(AppSearchParams params, Manager manager) {
        this(params, manager, false);
    }

    public InicializarBusca(AppSearchParams params, Manager manager, boolean updateItems) {
        this.appSearchParams = params;
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

    @SuppressWarnings("unchecked")
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
                App.get().appCase = new IPEDMultiSource(Collections.singletonList(singleCase));
            } else
                App.get().appCase = new IPEDMultiSource(App.get().casesPathFile);

            App.get().appCase.checkImagePaths();

            if (!updateItems) {
                LOGGER.info("Loading Columns"); //$NON-NLS-1$
                App.get().resultsModel.initCols();
                App.get().resultsTable.setRowSorter(new ResultTableRowSorter());

                IndexerDefaultParser autoParser = new IndexerDefaultParser();

                AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                        .findObjects(AdvancedIPEDConfig.class).iterator().next();
                autoParser.setFallback(new RawStringParser(advancedConfig.isEntropyTest()));
                autoParser.setErrorParser(new RawStringParser(advancedConfig.isEntropyTest()));

                App.get().setAutoParser(autoParser);

                FileProcessor exibirAjuda = new FileProcessor(-1, false);

                IViewerControl viewerControl = ViewerControl.getInstance();
                viewerControl.createViewers(this.appSearchParams, exibirAjuda);
                this.appSearchParams.textViewer = this.appSearchParams.compositeViewer.getTextViewer();
                App.get().setTextViewer((TextViewer) this.appSearchParams.textViewer);

                LOGGER.info("Listing all items"); //$NON-NLS-1$
                PesquisarIndice pesquisa = new PesquisarIndice(new MatchAllDocsQuery());
                pesquisa.execute();
                LOGGER.info("Listing all items Finished"); //$NON-NLS-1$
            }

            treeModel = new TreeViewModel();

        } catch (Throwable e) {
            e.printStackTrace();
            showErrorDialog(e);
        }

        return null;
    }

    private void showErrorDialog(final Throwable e) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
            	String msg = e.getMessage();
            	if(msg == null && e.getCause() != null) {
            		msg = e.getCause().getMessage();
            	}
                JOptionPane.showMessageDialog(App.get(), Messages.getString("AppLazyInitializer.errorMsg.line1") //$NON-NLS-1$
                        + Messages.getString("AppLazyInitializer.errorMsg.line2") //$NON-NLS-1$
                        + Messages.getString("AppLazyInitializer.errorMsg.line3") + msg, //$NON-NLS-1$
                        Messages.getString("AppLazyInitializer.errorTitle"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$

                App.get().dialogBar.setVisible(false);
            }
        });
    }

    @Override
    public void done() {
        CategoryTreeModel.install();
        App.get().menu = new MenuClass();
        App.get().filterManager.loadFilters();
        MarcadoresController.get().atualizarGUIandHistory();

        if (!App.get().appCase.isFTKReport()) {
            App.get().tree.setModel(treeModel);
            App.get().tree.setLargeModel(true);
            App.get().tree.setCellRenderer(new TreeCellRenderer());
        }

        if (updateItems) {
            App.get().appletListener.updateFileListing();
            ColumnsManager.getInstance().dispose();
            App.get().dialogBar.setVisible(false);
        }
    }

}
