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

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.swing.SwingWorker;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import dpf.sp.gpinf.indexer.util.VersionsMap;

import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.IViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;

public class InicializarBusca extends SwingWorker<Void, Integer> {

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

      Configuration.getConfiguration(App.get().codePath + "/..");
      ParsingReader.setTextSplitSize(Long.MAX_VALUE);
      
      if(App.get().casesPathFile == null)
			App.get().appCase = new IPEDSource(new File(App.get().codePath).getParentFile().getParentFile());
		else
			App.get().appCase = new IPEDMultiSource(App.get().casesPathFile);
		
		System.out.println("Loading Columns " + new Date());
		App.get().resultsModel.initCols();
		
		System.out.println("Loading Column Sorters " + new Date());
		if(App.get().appCase.getTotalItens() > 10000000)
			RowComparator.setLoadDocValues(false);
		App.get().resultsTable.setRowSorter(new ResultTableRowSorter());
      

      OCRParser.OUTPUT_BASE = new File(App.get().codePath + "/..");
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
      
      System.out.println("Listing items " + new Date());

      // lista todos os itens
      PesquisarIndice pesquisa = new PesquisarIndice(new MatchAllDocsQuery());
      pesquisa.execute();
      App.get().appCase.setTotalItens(pesquisa.get().getLength());
      
      System.out.println("Finished " + new Date());
      
      treeModel = new TreeViewModel();

    } catch (Throwable e) {
      e.printStackTrace();
    }

    return null;
  }

  @Override
  public void done() {
	  App.get().appCase.getMarcadores().loadState();
	  App.get().appCase.getMarcadores().atualizarGUI();
	  App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(App.get().results.getLength());
	  App.get().resultsTable.getTableHeader().repaint();
	  App.get().categoryTree.setModel(new CategoryTreeModel());

    if (!App.get().appCase.isFTKReport()) {
      App.get().tree.setModel(treeModel);
      App.get().tree.setLargeModel(true);
      App.get().tree.setCellRenderer(new TreeCellRenderer());
    }
  }

}
