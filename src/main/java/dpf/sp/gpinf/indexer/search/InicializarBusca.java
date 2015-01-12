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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.swing.SwingWorker;
import javax.swing.tree.DefaultTreeModel;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.parser.Parser;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.search.viewer.CompositeViewerHelper;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.VersionsMap;

public class InicializarBusca extends SwingWorker<Void, Integer> {

	@Override
	protected void process(List<Integer> chunks) {
		// App.get().setSize(1350, 500);

		App.get().dialogBar.setLocationRelativeTo(App.get());

		if (!this.isDone())
			App.get().dialogBar.setVisible(true);

	}

	@SuppressWarnings("unchecked")
	@Override
	protected Void doInBackground() {
		publish(0);

		try {
			//Não funciona fora do eclipse
			//Policy.setPolicy(new BlockInternetPolicy());
			System.setSecurityManager(new AppSecurityManager());

			IOUtil.readFile(new File(App.get().codePath + "/../lib/" + Versao.TIKA_VERSION));

			inicializar(App.get().codePath + "/../index");

			// ImageIO.setUseCache(false);

			Configuration.getConfiguration(App.get().codePath + "/../conf");
			ParsingReader.setTextSplitSize(Long.MAX_VALUE);

			IndexerDefaultParser autoParser = new IndexerDefaultParser();
			autoParser.setFallback((Parser) Configuration.fallBackParser.newInstance());
			autoParser.setErrorParser((Parser) Configuration.errorParser.newInstance());
			App.get().autoParser = autoParser;

			OCRParser.OUTPUT_BASE = new File(App.get().codePath + "/..");
			OCRParser.EXECTESS = false;


			App.get().textSizes = (int[]) IOUtil.readObject(App.get().codePath + "/../data/texts.size");
			App.get().ids = (int[]) IOUtil.readObject(App.get().codePath + "/../data/ids.map");
			App.get().lastId = App.get().textSizes.length - 1;

			HashSet<Integer> splitedIds = new HashSet<Integer>();
			for (Integer id : App.get().splitedDocs.values())
				splitedIds.add(id);
			App.get().totalItens = App.get().reader.numDocs() - App.get().splitedDocs.size() + splitedIds.size() - App.get().viewToRawMap.getMappings();

			new CompositeViewerHelper().addViewers();

			// lista todos os itens
			App.get().query = PesquisarIndice.getQuery("");
			PesquisarIndice pesquisa = new PesquisarIndice(App.get().query);
			// pesquisa.execute();
			App.get().results = pesquisa.pesquisar();
			pesquisa.countVolume(App.get().results);
			App.get().resultsModel.fireTableDataChanged();
						
			
			File sleuthFile = new File(App.get().codePath + "/../../sleuth.db"); 
			if (sleuthFile.exists()) {
				// IOUtil.loadNatLibs(App.get().codePath + "/../lib/sleuth");
				App.get().sleuthCase = SleuthkitCase.openCase(App.get().codePath + "/../../sleuth.db");
				if(sleuthFile.canWrite()){
					char letter = App.get().codePath.charAt(0);
					Map<Long, List<String>> imgPaths = App.get().sleuthCase.getImagePaths();
					for(Long id : imgPaths.keySet()){
						List<String> paths = imgPaths.get(id);
						ArrayList<String> newPaths = new ArrayList<String>(); 
						for(String path : paths){
							if(new File(path).exists())
								break;
							else{
								String newPath = letter + path.substring(1);
								if(new File(newPath).exists())
									newPaths.add(newPath);
								
							}
						}
						if(newPaths.size() > 0)
							App.get().sleuthCase.setImagePaths(id, newPaths);
						
					}
				}
				
			}

		} catch (Throwable e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public void done() {
		App.get().marcadores.loadState();
		App.get().marcadores.atualizarGUI();
		App.get().horizontalSplitPane.setDividerLocation(0.6);
		App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(App.get().results.length);
		App.get().resultsTable.getTableHeader().repaint();
		
		if(!App.get().isReport){
			App.get().tree.setModel(new TreeViewModel());
			App.get().tree.setLargeModel(true);
			App.get().tree.setCellRenderer(new TreeCellRenderer());
		}
	}

	
	public static void inicializar(String index) {
		try {
			Directory directory = FSDirectory.open(new File(index));
			App.get().reader = DirectoryReader.open(directory);
			App.get().searcher = new IndexSearcher(App.get().reader);
			App.get().searcher.setSimilarity(new IndexerSimilarity());
			App.get().analyzer = AppAnalyzer.get();// new
													// StandardASCIIAnalyzer(Versao.current);
			App.get().splitedDocs = (HashMap<Integer, Integer>) IOUtil.readObject(index + "/../data/splits.ids");
			if (new File(index + "/../data/alternativeToOriginals.ids").exists())
				App.get().viewToRawMap = (VersionsMap) IOUtil.readObject(index + "/../data/alternativeToOriginals.ids");
			App.get().marcadores = new Marcadores(index + "/..");

			BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
