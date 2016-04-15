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
package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.swing.SwingWorker;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import dpf.sp.gpinf.indexer.util.VersionsMap;

import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.IViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.control.ViewerControl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;

public class InicializarBusca extends SwingWorker<Void, Integer> {

  private AppSearchParams appSearchParams = null;

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
<<<<<<< HEAD
      IViewerControl viewerControl = ViewerControl.getInstance();
=======
      ViewerControl viewerControl = ViewerControlImpl.getInstance();
>>>>>>> 4855b2f... Versão estável do desmembramento por pacote.

      Configuration.getConfiguration(App.get().codePath + "/..");
      ParsingReader.setTextSplitSize(Long.MAX_VALUE);

      inicializar(App.get().codePath + "/../index");

      App.get().resultsModel.initCols();
      App.get().resultsTable.setRowSorter(new ResultTableRowSorter());

      OCRParser.OUTPUT_BASE = new File(App.get().codePath + "/..");
      OCRParser.EXECTESS = false;

      IndexerDefaultParser autoParser = new IndexerDefaultParser();
      autoParser.setFallback(Configuration.fallBackParser);
      autoParser.setErrorParser(Configuration.errorParser);
      App.get().setAutoParser(autoParser);

      FileProcessor exibirAjuda = new FileProcessor(-1, false);
      viewerControl.createViewers(this.appSearchParams,
          exibirAjuda);
      this.appSearchParams.textViewer = this.appSearchParams.compositeViewer.getTextViewer();
<<<<<<< HEAD
      App.get().setTextViewer((TextViewer) this.appSearchParams.textViewer);
=======
      App.get().setTextViewer(this.appSearchParams.textViewer);
>>>>>>> 4855b2f... Versão estável do desmembramento por pacote.

      // lista todos os itens
      App.get().setQuery(PesquisarIndice.getQuery(""));
      PesquisarIndice pesquisa = new PesquisarIndice(App.get().getQuery());
      App.get().results = pesquisa.pesquisar();
      App.get().totalItens = App.get().results.length;
      pesquisa.countVolume(App.get().results);
      App.get().resultsModel.fireTableDataChanged();

      updateImagePaths();

    } catch (Throwable e) {
      e.printStackTrace();
    }

    return null;
  }

  @Override
  public void done() {
    App.get().marcadores.loadState();
    App.get().marcadores.atualizarGUI();
    App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(App.get().results.length);
    App.get().resultsTable.getTableHeader().repaint();

    if (!App.get().isFTKReport) {
      App.get().tree.setModel(new TreeViewModel());
      App.get().tree.setLargeModel(true);
      App.get().tree.setCellRenderer(new TreeCellRenderer());
    }
  }

  private void updateImagePaths() throws Exception {
    File tmpCase = null, sleuthFile = new File(App.get().codePath + "/../../sleuth.db");
    if (sleuthFile.exists()) {
      App.get().sleuthCase = SleuthkitCase.openCase(sleuthFile.getAbsolutePath());
      char letter = App.get().codePath.charAt(0);
      Map<Long, List<String>> imgPaths = App.get().sleuthCase.getImagePaths();
      for (Long id : imgPaths.keySet()) {
        List<String> paths = imgPaths.get(id);
        ArrayList<String> newPaths = new ArrayList<String>();
        for (String path : paths) {
          if (new File(path).exists()) {
            break;
          } else {
            String newPath = letter + path.substring(1);
            if (new File(newPath).exists()) {
              newPaths.add(newPath);
            } else {
              File file = new File(path);
              String relPath = "";
              do {
                relPath = File.separator + file.getName() + relPath;
                newPath = sleuthFile.getParent() + relPath;
                file = file.getParentFile();

              } while (file != null && !new File(newPath).exists());

              if (new File(newPath).exists()) {
                newPaths.add(newPath);
              }
            }
          }
        }
        if (newPaths.size() > 0) {
          if (tmpCase == null && !sleuthFile.canWrite()) {
            tmpCase = File.createTempFile("iped-", ".db");
            tmpCase.deleteOnExit();
            App.get().sleuthCase.close();
            IOUtil.copiaArquivo(sleuthFile, tmpCase);
            App.get().sleuthCase = SleuthkitCase.openCase(tmpCase.getAbsolutePath());
          }
          App.get().sleuthCase.setImagePaths(id, newPaths);
        }
      }
    }
  }

  public static void inicializar(String index) {
    try {
      Directory directory = FSDirectory.open(new File(index));
      App.get().reader = DirectoryReader.open(directory);

      if (Configuration.searchThreads > 1) {
        App.get().searchExecutorService = Executors.newFixedThreadPool(Configuration.searchThreads);
        App.get().searcher = new IndexSearcher(App.get().reader, App.get().searchExecutorService);
      } else {
        App.get().searcher = new IndexSearcher(App.get().reader);
      }

      App.get().searcher.setSimilarity(new IndexerSimilarity());
      App.get().setAnalyzer(AppAnalyzer.get());
      App.get().splitedDocs = (Set<Integer>) Util.readObject(index + "/../data/splits.ids");
      App.get().setTextSizes((int[]) Util.readObject(index + "/../data/texts.size"));
      App.get().lastId = App.get().getTextSizes().length - 1;
      App.get().setIDs((int[]) Util.readObject(index + "/../data/ids.map"));
      App.get().setDocs(new int[App.get().lastId + 1]);
      for (int i = 0; i < App.get().getIDs().length; i++) {
        App.get().getDocs()[App.get().getIDs()[i]] = i;
      }
      if (new File(index + "/../data/alternativeToOriginals.ids").exists()) {
        App.get().viewToRawMap = (VersionsMap) Util.readObject(index + "/../data/alternativeToOriginals.ids");
      }
      IndexItem.loadMetadataTypes(new File(index + "/../conf"));
      App.get().marcadores = new Marcadores(index + "/..");

      BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
