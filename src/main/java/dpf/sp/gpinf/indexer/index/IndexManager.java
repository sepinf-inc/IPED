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
package dpf.sp.gpinf.indexer.index;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import javax.swing.JOptionPane;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportProcessor;
import dpf.sp.gpinf.indexer.index.HashClass.HashValue;
import dpf.sp.gpinf.indexer.index.IndexWorker.IdLenPair;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.EmbeddedFileParser;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.IndexerSimilarity;
import dpf.sp.gpinf.indexer.search.InicializarBusca;
import dpf.sp.gpinf.indexer.search.PesquisarIndice;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.VersionsMap;

/*
 * Classe responsável pela preparação do processamento, criação do produtor de itens e 
 * consumidores (processadores) dos itens, monitoramento do processamento e pelas etapas pós-processamento. 
 */
public class IndexManager {

	private static int QUEUE_SIZE = 100000;

	private Date start;
	private CaseData caseData;
	private List<File> reports;
	private List<String> caseNames;
	private File output, indexDir, indexTemp, palavrasChave;

	private Thread contador, produtor;
	private IndexWorker[] workers;
	private IndexWriter writer;
	private int previousIndexedFiles = 0;

	public Exception exception;

	public IndexManager(List<File> reports, List<String> caseNames, File output, File palavras) {
		this.indexTemp = Configuration.indexTemp;
		this.caseNames = caseNames;
		this.reports = reports;
		this.output = output;
		this.palavrasChave = palavras;

		this.caseData = new CaseData(QUEUE_SIZE);
		if (caseNames.size() > 0)
			caseData.setContainsReport(true);

		IndexWorker.resetStaticVariables();
		EvidenceFile.setStartID(0);

		indexDir = new File(output, "index");
		if (indexTemp == null || IndexFiles.getInstance().appendIndex)
			indexTemp = indexDir;

		OCRParser.OUTPUT_BASE = output;

	}

	public void process() throws Exception {

		start = new Date();
		printSystemInfo();

		output = output.getCanonicalFile();

		prepararReport();

		if (IndexFiles.getInstance().appendIndex)
			loadExistingData();

		for (File report : reports)
			System.out.println(new Date() + "\t[INFO]\t" + "Adicionando '" + report.getAbsolutePath() + "'");

		// apenas conta o número de arquivos a indexar
		contador = new Thread(new ItemProducer(this, caseData, true, reports, caseNames, output));
		contador.start();

		// produz lista de arquivos e propriedades a indexar
		produtor = new Thread(new ItemProducer(this, caseData, false, reports, caseNames, output));
		produtor.start();

		try {
			iniciarIndexacao();
			monitorarIndexacao();
			finalizarIndexacao();

		} catch (Exception e) {
			interromperIndexacao();
			throw e;
		}
		
		salvarDocIdToIdMap();

		salvarTamanhoTextosExtraidos();

		salvarDocsFragmentados();

		PropertiesSorter sorter = new PropertiesSorter(output, Configuration.numThreads);
		sorter.sort();

		saveViewToOriginalFileMap();

		filtrarPalavrasChave();

		configurarCategorias();

		logarEstatisticas();

	}

	private void interromperIndexacao() throws Exception {
		if (workers != null)
			for (int k = 0; k < workers.length; k++)
				if (workers[k] != null) {
					workers[k].interrupt();
					// workers[k].join(5000);
				}
		ParsingReader.shutdownTasks();
		if (writer != null)
			writer.close();
		if (contador != null) {
			contador.interrupt();
			// contador.join(5000);
		}
		if (produtor != null) {
			produtor.interrupt();
			// produtor.join(5000);
		}
	}

	private void loadExistingData() throws Exception {

		FileInputStream fileIn = new FileInputStream(new File(output, "data/texts.size"));
		ObjectInputStream in = new ObjectInputStream(fileIn);

		int[] textSizes = (int[]) in.readObject();
		for (int i = 0; i < textSizes.length; i++)
			if (textSizes[i] != 0)
				IndexWorker.textSizes.add(new IdLenPair(i, textSizes[i] * 1000L));
	
		in.close();
		fileIn.close();

		IndexWorker.setLastId(textSizes.length - 1);
		EvidenceFile.setStartID(textSizes.length);

		fileIn = new FileInputStream(new File(output, "data/splits.ids"));
		in = new ObjectInputStream(fileIn);

		HashMap<Integer, Integer> splitedDocs = (HashMap<Integer, Integer>) in.readObject();
		for (Integer id : splitedDocs.values())
			IndexWorker.splitedIds.add(id);

		in.close();
		fileIn.close();

		IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
		previousIndexedFiles = reader.numDocs();

		synchronized (IndexWorker.hashMap) {
			for (int i = 0; i < reader.maxDoc(); i++) {
				Document doc = reader.document(i);
				String hash = doc.get("hash");
				if (hash != null){
					HashValue hValue = new HashValue(hash);
					IndexWorker.hashMap.put(hValue, hValue);
				}
					
			}

		}

		reader.close();

		if (new File(output, "data/containsReport.flag").exists())
			caseData.setContainsReport(true);

	}
	

	public void logarEstatisticas() throws Exception {

		int processed = IndexWorker.getProcessed();
		int extracted = FileExtractor.getSubitensExtracted();
		int activeFiles = IndexWorker.getActiveProcessed();
		int carvedIgnored = IndexWorker.getCorruptCarveIgnored();
		int duplicatesIgnored = IndexWorker.getDuplicatesIgnored();
		
		System.out.println(new Date() + "\t[INFO]\t" + "Divisões de arquivo: " + IndexWorker.getSplits());
		System.out.println(new Date() + "\t[INFO]\t" + "Timeouts: " + IndexWorker.getTimeouts());
		System.out.println(new Date() + "\t[INFO]\t" + "Exceções de parsing: " + IndexerDefaultParser.parsingErrors);
		System.out.println(new Date() + "\t[INFO]\t" + "Subitens descobertos: " + EmbeddedFileParser.getSubitensDiscovered());
		System.out.println(new Date() + "\t[INFO]\t" + "Itens extraídos: " + extracted);
		System.out.println(new Date() + "\t[INFO]\t" + "Itens de Carving: " + FileCarver.getItensCarved());
		System.out.println(new Date() + "\t[INFO]\t" + "Carvings corrompidos ignorados: " + carvedIgnored);
		System.out.println(new Date() + "\t[INFO]\t" + "Duplicados descartados: " + duplicatesIgnored);

		if (caseData.getAlternativeFiles() > 0)
			System.out.println(new Date() + "\t[INFO]\t" + "Processadas " + caseData.getAlternativeFiles() + " versões de visualização dos itens ao invés das originais.");

		IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir));
		int indexed = reader.numDocs() - IndexWorker.getSplits() - previousIndexedFiles;
		reader.close();

		if (indexed != processed && FileExtractor.hasCategoryToExtract())
			System.out.println(new Date() + "\t[INFO]\t" + "Itens indexados: " + indexed);

		long processedVolume = IndexWorker.getVolume() / (1024 * 1024);
		
		if (activeFiles != processed)
			System.out.println(new Date() + "\t[INFO]\t" + "Itens ativos processados: " + activeFiles);

		System.out.println(new Date() + "\t[INFO]\t" + "Total processado: " + processed + " itens em " + ((new Date()).getTime() - start.getTime()) / 1000 + " segundos (" + processedVolume + " MB)");

		int discovered = caseData.getDiscoveredEvidences();
		if (processed != discovered)
			throw new Exception("Processados " + processed + " itens de " + discovered);

		if(!FileExtractor.hasCategoryToExtract()){
			if (indexed + carvedIgnored + duplicatesIgnored != discovered)
				throw new Exception("Indexados " + indexed + " itens de " + discovered);
		}/*else 
			if (indexed != extracted)
				throw new Exception("Indexados " + indexed + " itens de " + extracted);
		*/
	}

	private void iniciarIndexacao() throws Exception {
		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Indexação iniciada...");
		System.out.println(new Date() + "\t[INFO]\t" + "Indexação iniciada...");

		IndexWriterConfig conf = new IndexWriterConfig(Versao.current, AppAnalyzer.get());
		conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
		conf.setMaxThreadStates(Configuration.numThreads);
		conf.setSimilarity(new IndexerSimilarity());
		ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
		mergeScheduler.setMaxMergesAndThreads(4, 2);
		conf.setMergeScheduler(mergeScheduler);
		conf.setRAMBufferSizeMB(32);
		TieredMergePolicy tieredPolicy = new TieredMergePolicy();
		//tieredPolicy.setMaxMergedSegmentMB(1024);
		conf.setMergePolicy(tieredPolicy);

		writer = new IndexWriter(FSDirectory.open(indexTemp), conf);

		workers = new IndexWorker[Configuration.numThreads];
		for (int k = 0; k < workers.length; k++) {
			workers[k] = new IndexWorker(k, caseData, writer, output);
			// workers[k].setDaemon(false);
			workers[k].start();
		}

		IndexFiles.getInstance().firePropertyChange("workers", 0, workers);
	}

	private void monitorarIndexacao() throws Exception {

		boolean someWorkerAlive = true;

		while (someWorkerAlive) {
			if (IndexFiles.getInstance().isCancelled())
				exception = new InterruptedException("Indexação cancelada!");

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				exception = new InterruptedException("Indexação cancelada!");
			}

			IndexFiles.getInstance().firePropertyChange("discovered", 0, caseData.getDiscoveredEvidences());
			IndexFiles.getInstance().firePropertyChange("processed", -1, IndexWorker.getProcessed());
			IndexFiles.getInstance().firePropertyChange("progresso", 0, (int)(IndexWorker.getVolume()/1000000));

			someWorkerAlive = false;
			for (int k = 0; k < workers.length; k++) {
				if (workers[k].exception != null && exception == null)
					exception = workers[k].exception;

				/*
				 *  TODO sincronizar teste, pois pode ocorrer condição de corrida e o teste não detectar um último item sendo processado
				 *  Não é demasiado grave pois será detectado o problema no log de estatísticas e o usuário será informado do erro. 
				 */
				if (caseData.getEvidenceFiles().size() > 0 || workers[k].evidence != null || produtor.isAlive() ) {
					someWorkerAlive = true;
				}

				// TODO verificar se algum worker morreu e reiniciá-lo? (Nao deve ocorrer...)
			}

			if (exception != null)
				throw exception;

		}

		for (int k = 0; k < workers.length; k++) {
			workers[k].interrupt();
			//workers[k].join(5);
		}
		ParsingReader.shutdownTasks();

	}

	private void finalizarIndexacao() throws IOException {

		if (Configuration.forceMerge) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Otimizando índice...");
			System.out.println(new Date() + "\t[INFO]\t" + "Otimizando índice...");
			try {
				writer.forceMerge(1);
			} catch (Throwable e) {
				System.out.println(new Date() + "\t[ALERTA]\t" + "Erro durante otimização: " + e);
			}

		}

		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Fechando índice...");
		System.out.println(new Date() + "\t[INFO]\t" + "Fechando índice...");
		writer.close();

		if (!indexTemp.getCanonicalPath().equalsIgnoreCase(indexDir.getCanonicalPath())) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Copiando índice...");
			System.out.println(new Date() + "\t[INFO]\t" + "Copiando índice...");
			IOUtil.copiaDiretorio(indexTemp, indexDir);			
		}
		
		try {
			IOUtil.deletarDiretorio(Configuration.indexerTemp);
		} catch (IOException e) {
			System.out.println(new Date() + "\t[AVISO]\t" + "Não foi possível apagar " + Configuration.indexerTemp.getPath());
		}

		if (caseData.containsReport() || FileExtractor.hasCategoryToExtract())
			new File(output, "data/containsReport.flag").createNewFile();

	}

	private void configurarCategorias1() throws Exception {
		System.out.println(new Date() + "\t[INFO]\t" + "Configurando categorias...");
		TreeSet<String> categories = IOUtil.loadKeywordSet(output.getAbsolutePath() + "/categorias.txt", "UTF-8");

		if (caseData.containsReport() && caseData.getBookmarks().size() > 0) {
			for (FileGroup bookmark : caseData.getBookmarks())
				categories.add(bookmark.getName().replaceAll("\"", "\\\""));
			categories.add(Configuration.defaultCategory);

		} else
			categories = CategoryMapper.getCategories();

		// filtra categorias vazias
		if (categories.size() != 0) {
			InicializarBusca.inicializar(output.getAbsolutePath() + "/index");
			ArrayList<String> palavrasFinais = new ArrayList<String>();
			for (String categoria : categories) {
				if (Thread.interrupted()) {
					App.get().destroy();
					throw new InterruptedException("Indexação cancelada!");
				}

				String query = "categoria:\"" + categoria.replace("\"", "\\\"") + "\"";
				PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(query));
				if (pesquisa.pesquisarTodos().length > 0)
					palavrasFinais.add(categoria);
			}
			// fecha o índice
			App.get().destroy();
			IOUtil.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/categorias.txt", "UTF-8");
			int filtradas = categories.size() - palavrasFinais.size();
			System.out.println(new Date() + "\t[INFO]\t" + "Filtradas " + filtradas + " categorias.");
		} else
			System.out.println(new Date() + "\t[INFO]\t" + "Nenhuma categoria detectada.");

	}

	private void configurarCategorias() throws Exception {
		System.out.println(new Date() + "\t[INFO]\t" + "Configurando categorias...");
		TreeSet<String> categories = IOUtil.loadKeywordSet(output.getAbsolutePath() + "/categorias.txt", "UTF-8");

		if (caseData.containsReport() && caseData.getBookmarks().size() > 0) {
			for (FileGroup bookmark : caseData.getBookmarks())
				categories.add(bookmark.getName().replaceAll("\"", "\\\""));
			categories.add(Configuration.defaultCategory);

		} else
			categories = CategoryMapper.getCategories();

		IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir));
		int maxDoc = reader.maxDoc();
		reader.close();
		int[] categoryMap = new int[maxDoc];

		// filtra categorias vazias
		if (categories.size() != 0) {
			InicializarBusca.inicializar(output.getAbsolutePath() + "/index");
			ArrayList<String> palavrasFinais = new ArrayList<String>();
			int catNum = 0;
			for (String categoria : categories) {
				if (Thread.interrupted()) {
					App.get().destroy();
					throw new InterruptedException("Indexação cancelada!");
				}

				String query = "categoria:\"" + categoria.replace("\"", "\\\"") + "\"";
				PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(query));
				SearchResult result = pesquisa.pesquisarTodos();

				for (int doc : result.docs)
					categoryMap[doc] = catNum;

				if (result.length > 0) {
					palavrasFinais.add(categoria);
					catNum++;
				}

			}
			// fecha o índice
			App.get().destroy();

			/*
			 * FileOutputStream fileOut = new FileOutputStream(new File(output,
			 * "data/category.map")); ObjectOutputStream out = new
			 * ObjectOutputStream(fileOut); out.writeObject(categoryMap);
			 * out.close(); fileOut.close();
			 */
			IOUtil.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/categorias.txt", "UTF-8");
			int filtradas = categories.size() - palavrasFinais.size();
			System.out.println(new Date() + "\t[INFO]\t" + "Filtradas " + filtradas + " categorias.");
		} else
			System.out.println(new Date() + "\t[INFO]\t" + "Nenhuma categoria detectada.");

	}

	private void filtrarPalavrasChave() throws Exception {
		System.out.println(new Date() + "\t[INFO]\t" + "Filtrando palavras-chave...");
		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Filtrando palavras-chave...");
		ArrayList<String> palavras = IOUtil.loadKeywords(output.getAbsolutePath() + "/palavras-chave.txt", Charset.defaultCharset().name());

		if (palavras.size() != 0) {
			InicializarBusca.inicializar(output.getAbsolutePath() + "/index");
			ArrayList<String> palavrasFinais = new ArrayList<String>();
			for (String palavra : palavras) {
				if (Thread.interrupted()) {
					App.get().destroy();
					throw new InterruptedException("Indexação cancelada!");
				}

				PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(palavra));
				if (pesquisa.pesquisarTodos().length > 0)
					palavrasFinais.add(palavra);
			}
			// fecha o índice
			App.get().destroy();
			IOUtil.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/palavras-chave.txt", "UTF-8");
			int filtradas = palavras.size() - palavrasFinais.size();
			System.out.println(new Date() + "\t[INFO]\t" + "Filtradas " + filtradas + " palavras-chave.");
		} else
			System.out.println(new Date() + "\t[INFO]\t" + "Nenhuma palavra-chave pré-configurada para filtrar.");

	}

	private void salvarDocsFragmentados() throws Exception {
		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Salvando IDs dos itens fragmentados...");
		System.out.println(new Date() + "\t[INFO]\t" + "Salvando IDs dos itens fragmentados...");

		File indexDir = new File(output, "index");
		Directory directory = FSDirectory.open(indexDir);
		IndexReader reader = DirectoryReader.open(directory);
		HashMap<Integer, Integer> splitedDocs = new HashMap<Integer, Integer>();
		Bits liveDocs = MultiFields.getLiveDocs(reader);
		for (int i = 0; i < reader.maxDoc(); i++) {
			if (Thread.interrupted()) {
				reader.close();
				throw new InterruptedException("Indexação cancelada!");
			}
			if (liveDocs != null && !liveDocs.get(i))
				continue;
			int id = Integer.parseInt(reader.document(i).get("id"));
			if (IndexWorker.splitedIds.contains(id))
				splitedDocs.put(i, id);
		}
		reader.close();
		FileOutputStream fileOut = new FileOutputStream(new File(output, "data/splits.ids"));
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(splitedDocs);
		out.close();
		fileOut.close();
	}

	private void saveViewToOriginalFileMap() throws Exception {

		VersionsMap viewToRaw = new VersionsMap(0);

		if (FTK3ReportProcessor.wasInstantiated) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Obtendo mapeamento de versões de visualização para originais...");
			System.out.println(new Date() + "\t[INFO]\t" + "Obtendo mapa versões de visualização -> originais...");

			InicializarBusca.inicializar(output.getAbsolutePath() + "/index");
			String query = "export:(files && (\"AD html\" \"AD rtf\"))";
			PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(query));
			ScoreDoc[] alternatives = pesquisa.filtrarFragmentos1(pesquisa.pesquisarTodos1());

			HashMap<String, Integer> viewMap = new HashMap<String, Integer>();
			for (int i = 0; i < alternatives.length; i++) {
				if (Thread.interrupted()) {
					App.get().destroy();
					throw new InterruptedException("Indexação cancelada!");
				}
				Document doc = App.get().searcher.doc(alternatives[i].doc);
				String ftkId = doc.get("ftkId");
				viewMap.put(ftkId, alternatives[i].doc);
			}
			alternatives = null;
			App.get().destroy();

			IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(output, "index")));
			Bits liveDocs = MultiFields.getLiveDocs(reader);
			viewToRaw = new VersionsMap(reader.maxDoc());

			for (int i = 0; i < reader.maxDoc(); i++) {
				if (liveDocs != null && !liveDocs.get(i))
					continue;

				Document doc = reader.document(i);
				String ftkId = doc.get("ftkId");
				String export = doc.get("export");

				Integer viewDocId = viewMap.get(ftkId);
				if (viewDocId != null && viewDocId != i && !viewToRaw.isView(viewDocId) && !export.contains(".[AD]."))
					viewToRaw.put(viewDocId, i);

			}
			reader.close();

			System.out.println(new Date() + "\t[INFO]\t" + "Obtidos " + viewToRaw.getMappings() + " mapeamentos de versões de visualização para originais.");
		}

		FileOutputStream fileOut = new FileOutputStream(new File(output, "data/alternativeToOriginals.ids"));
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(viewToRaw);
		out.close();
		fileOut.close();
	}
	

	private void salvarDocIdToIdMap() throws IOException{
		
		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Salvando mapeamento indexId->id");
		System.out.println(new Date() + "\t[INFO]\t" + "Salvando mapeamento indexId->id");

		IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
		int[] ids = new int[reader.maxDoc()];
		for (int i = 0; i < reader.maxDoc(); i++) {
			Document doc = reader.document(i);
			ids[i] = Integer.parseInt(doc.get("id"));
		}
		
		reader.close();
		IOUtil.writeObject(ids, output.getAbsolutePath() + "/data/ids.map");
	}

	private void salvarTamanhoTextosExtraidos() throws Exception {

		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Salvando tamanho dos textos extraídos...");
		System.out.println(new Date() + "\t[INFO]\t" + "Salvando tamanho dos textos extraídos...");

		int[] textSizes = new int[IndexWorker.getLastId() + 1];

		for (int i = 0; i < IndexWorker.textSizes.size(); i++) {
			IdLenPair pair = IndexWorker.textSizes.get(i);
			textSizes[pair.id] = pair.length;
		}

		IOUtil.writeObject(textSizes, output.getAbsolutePath() + "/data/texts.size");
	}
	

	private void prepararReport() throws Exception {
		if (output.exists() && !IndexFiles.getInstance().appendIndex) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Apagando " + output.getAbsolutePath());
			System.out.println(new Date() + "\t[INFO]\t" + "Apagando " + output.getAbsolutePath());
			IOUtil.deletarDiretorio(output);
		}

		File export = new File(output.getParentFile(), FileExtractor.EXTRACT_DIR);
		if (export.exists() && !IndexFiles.getInstance().appendIndex) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Apagando " + export.getAbsolutePath());
			System.out.println(new Date() + "\t[INFO]\t" + "Apagando " + export.getAbsolutePath());
			IOUtil.deletarDiretorio(export);
		}

		if (indexTemp.exists() && !IndexFiles.getInstance().appendIndex) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Apagando " + output.getAbsolutePath());
			System.out.println(new Date() + "\t[INFO]\t" + "Apagando " + indexTemp.getAbsolutePath());
			IOUtil.deletarDiretorio(indexTemp);
		}

		Thread.sleep(1000);

		if (!output.exists() && !output.mkdir())
			throw new IOException("Não foi possível criar diretório " + output.getAbsolutePath());

		IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib"), new File(output, "lib"), false);
		IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib/lib"), new File(output, "lib/lib"));
		IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib/sleuth"), new File(output, "lib/sleuth"));
		IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib/gm"), new File(output, "lib/gm"));
		IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib/libLO3"), new File(output, "lib/libLO3"));
		IOUtil.copiaDiretorio(new File(Configuration.configPath, "lib/libLO4"), new File(output, "lib/libLO4"));
		if (Configuration.embutirLibreOffice)
			IOUtil.copiaArquivo(new File(Configuration.configPath, "lib/LO4/libreoffice.zip"), new File(output, "lib/libreoffice.zip"));

		IOUtil.copiaDiretorio(new File(Configuration.configPath, "htm"), new File(output, "htm"));
		IOUtil.copiaDiretorio(new File(Configuration.configPath, "conf"), new File(output, "conf"), true);
		IOUtil.copiaArquivo(new File(Configuration.configPath + "/" + Configuration.CONFIG_FILE), new File(output, "conf/" + Configuration.CONFIG_FILE));
		IOUtil.copiaDiretorio(new File(Configuration.configPath, "bin"), output.getParentFile());

		if (palavrasChave != null)
			IOUtil.copiaArquivo(palavrasChave, new File(output, "palavras-chave.txt"));

		File dataDir = new File(output, "data");
		if (!dataDir.exists())
			if (!dataDir.mkdir())
				throw new IOException("Não foi possível criar diretório " + dataDir.getAbsolutePath());

	}

	private void printSystemInfo() throws Exception {

		System.out.println(new Date() + "\t[INFO]\t" + "Sistema Operacional: " + System.getProperty("os.name"));
		System.out.println(new Date() + "\t[INFO]\t" + "Versão Java: " + System.getProperty("java.version"));
		System.out.println(new Date() + "\t[INFO]\t" + "Arquitetura: " + System.getProperty("os.arch"));
		System.out.println(new Date() + "\t[INFO]\t" + "Processadores: " + Runtime.getRuntime().availableProcessors());
		System.out.println(new Date() + "\t[INFO]\t" + "numThreads: " + Configuration.numThreads);

		int minMemPerThread = 200;
		long maxMemory = Runtime.getRuntime().maxMemory() / 1000000;
		System.out.println(new Date() + "\t[INFO]\t" + "Memória disponível: " + maxMemory + " MB");

		/*
		 * System.out.println(new Date() + "\t[INFO]\t" + "Configurações:");
		 * for(Entry<Object, Object> entry :
		 * Configuration.properties.entrySet()){ System.out.println(new Date() +
		 * "\t[INFO]\t" + entry.getKey() + " = " + entry.getValue()); }
		 */
		if (maxMemory / Configuration.numThreads < minMemPerThread) {
			String memoryAlert = "Pouca memória disponível: menos de " + minMemPerThread + "MB por thread de indexação." + "\nIsso pode causar lentidão e erros de parsing de arquivos complexos."
					+ "\n\tUtilize uma JVM x64 (preferencial), " + "\n\taumente a memória da JVM via parâmetro -Xmx " + "\n\tou diminua o parâmetro numThreads em IndexerConfig.txt";
			JOptionPane.showMessageDialog(App.get(), memoryAlert, "Alerta de Memória", JOptionPane.WARNING_MESSAGE);
			throw new Exception(memoryAlert);
		}

	}

}
