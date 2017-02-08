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

import javax.swing.SwingWorker;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.IViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;

public class InicializarBusca extends SwingWorker<Void, Integer> {
	
  private static Logger LOGGER = LoggerFactory.getLogger(InicializarBusca.class);

  private AppSearchParams appSearchParams = null;
  private TreeViewModel treeModel;

  public InicializarBusca(AppSearchParams params) {
    this.appSearchParams = params;
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
      IViewerControl viewerControl = ViewerControl.getInstance();
      
      if(!App.get().isMultiCase){
    	  IPEDSource singleCase = new IPEDSource(App.get().casesPathFile);
    	  App.get().appCase = new IPEDMultiSource(Collections.singletonList(singleCase));
      }else
    	  App.get().appCase = new IPEDMultiSource(App.get().casesPathFile);
		
      LOGGER.info("Loading Columns");
      App.get().resultsModel.initCols();
		
      if(App.get().appCase.getTotalItens() > 100000000)
    	  RowComparator.setLoadDocValues(false);
      App.get().resultsTable.setRowSorter(new ResultTableRowSorter());

      ParsingReader.setTextSplitSize(Long.MAX_VALUE);
      OCRParser.EXECTESS = false;

      IndexerDefaultParser autoParser = new IndexerDefaultParser();
      autoParser.setFallback(Configuration.fallBackParser);
      autoParser.setErrorParser(Configuration.errorParser);
      App.get().setAutoParser(autoParser);

      //Load viewers
      FileProcessor exibirAjuda = new FileProcessor(-1, false);
      viewerControl.createViewers(this.appSearchParams, exibirAjuda);
      this.appSearchParams.textViewer = this.appSearchParams.compositeViewer.getTextViewer();
      App.get().setTextViewer((TextViewer) this.appSearchParams.textViewer);
      
      LOGGER.info("Listing items");

      // lista todos os itens
      PesquisarIndice pesquisa = new PesquisarIndice(new MatchAllDocsQuery());
      pesquisa.execute();
      
      LOGGER.info("Listing items Finished");
      
      treeModel = new TreeViewModel();

    } catch (Throwable e) {
      e.printStackTrace();
    }

    return null;
  }

  @Override
  public void done() {
	  App.get().categoryTree.setModel(new CategoryTreeModel());
	  App.get().menu = new MenuClass();
	  App.get().filterManager.loadFilters();
	  MarcadoresController.get().atualizarGUIandHistory();

    if (!App.get().appCase.isFTKReport()) {
      App.get().tree.setModel(treeModel);
      App.get().tree.setLargeModel(true);
      App.get().tree.setCellRenderer(new TreeCellRenderer());
    }
  }

}
