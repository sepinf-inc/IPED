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
package dpf.sp.gpinf.indexer.process;

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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.Worker.IdLenPair;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;
import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;
import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.IndexerSimilarity;
import dpf.sp.gpinf.indexer.search.InicializarBusca;
import dpf.sp.gpinf.indexer.search.PesquisarIndice;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.VersionsMap;

/**
 * Classe responsável pela preparação do processamento, inicialização do contador, produtor e 
 * consumidores (workers) dos itens, monitoramento do processamento e pelas etapas pós-processamento.
 * 
 * O contador apenas enumera e soma o tamanho dos itens que serão processados,
 * permitindo que seja estimado o progresso e término do processamento.
 * 
 * O produtor obtém os itens a partir de uma fonte de dados específica (relatório do FTK, diretório, imagem),
 * inserindo-os numa fila de processamento com tamanho limitado (para limitar o uso de memória).
 * 
 * Os consumidores (workers) retiram os itens da fila e são responsáveis pelo seu processamento.
 * Cada worker executa em uma thread diferente, permitindo o processamento em paralelo dos itens.
 * Por padrão, o número de workers é igual ao número de processadores disponíveis.
 * 
 * Após inicializar o processamento, o manager realiza o monitoramento, verificando se alguma exceção ocorreu,
 * informando a interface sobre o estado do processamento e verificando se os workers processaram todos os itens.
 * 
 * O pós-processamento inclui a pré-ordenação das propriedades dos itens, o armazenamento do volume de texto 
 * indexado de cada item, do mapeamento indexId->id, dos ids dos itens fragmentados, a filtragem de categorias
 * e palavras-chave e o log de estatísticas do processamento.
 * 
 */
public class Manager {

	private static int QUEUE_SIZE = 100000;

	private Date start;
	private CaseData caseData;
	private List<File> reports;
	private List<String> caseNames;
	private File output, indexDir, indexTemp, palavrasChave;

	private Thread contador, produtor;
	private Worker[] workers;
	private IndexWriter writer;

	public Statistics stats;
	public Exception exception;
	

	public List<IdLenPair> textSizes = Collections.synchronizedList(new ArrayList<IdLenPair>());
	public HashMap<HashValue, HashValue> hashMap = new HashMap<HashValue, HashValue>();
	private HashSet<Integer> splitedIds = new HashSet<Integer>();

	public Manager(List<File> reports, List<String> caseNames, File output, File palavras) {
		this.indexTemp = Configuration.indexTemp;
		this.caseNames = caseNames;
		this.reports = reports;
		this.output = output;
		this.palavrasChave = palavras;

		this.caseData = new CaseData(QUEUE_SIZE);
		if (caseNames.size() > 0)
			caseData.setContainsReport(true);

		Worker.resetStaticVariables();
		EvidenceFile.setStartID(0);

		indexDir = new File(output, "index");
		if (indexTemp == null || IndexFiles.getInstance().appendIndex)
			indexTemp = indexDir;

		stats = new Statistics(caseData, indexDir);
		
		OCRParser.OUTPUT_BASE = output;

	}
	
	synchronized public void remindSplitedDoc(int id) {
		splitedIds.add(id);
	}

	public void process() throws Exception {

		stats.printSystemInfo();

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

		stats.logarEstatisticas();

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

		int[] textSizesArray = (int[]) in.readObject();
		for (int i = 0; i < textSizesArray.length; i++)
			if (textSizesArray[i] != 0)
				textSizes.add(new IdLenPair(i, textSizesArray[i] * 1000L));
	
		in.close();
		fileIn.close();

		stats.setLastId(textSizesArray.length - 1);
		EvidenceFile.setStartID(textSizesArray.length);

		fileIn = new FileInputStream(new File(output, "data/splits.ids"));
		in = new ObjectInputStream(fileIn);

		HashMap<Integer, Integer> splitedDocs = (HashMap<Integer, Integer>) in.readObject();
		for (Integer id : splitedDocs.values())
			splitedIds.add(id);

		in.close();
		fileIn.close();

		IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
		stats.previousIndexedFiles = reader.numDocs();

		synchronized (hashMap) {
			for (int i = 0; i < reader.maxDoc(); i++) {
				Document doc = reader.document(i);
				String hash = doc.get(IndexItem.HASH);
				if (hash != null){
					HashValue hValue = new HashValue(hash);
					hashMap.put(hValue, hValue);
				}
					
			}

		}

		reader.close();

		if (new File(output, "data/containsReport.flag").exists())
			caseData.setContainsReport(true);

	}

	private void iniciarIndexacao() throws Exception {
		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Indexação iniciada...");
		System.out.println(new Date() + "\t[INFO]\t" + "Indexação iniciada...");

		IndexWriterConfig conf = new IndexWriterConfig(Versao.current, AppAnalyzer.get());
		conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
		conf.setMaxThreadStates(Configuration.numThreads);
		conf.setSimilarity(new IndexerSimilarity());
		ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
		//Merges concorrentes degradam mto em discos rotacionais
		//mergeScheduler.setMaxMergesAndThreads(4, 2);
		conf.setMergeScheduler(mergeScheduler);
		conf.setRAMBufferSizeMB(32);
		TieredMergePolicy tieredPolicy = new TieredMergePolicy();
		/*
		 * Seta tamanho máximo dos subíndices. Padrão é 5GB.
		 * Poucos subíndices grandes impactam processamento devido a merges parciais demorados.
		 * Muitos subíndices pequenos aumentam tempo e memória necessários p/ pesquisas.
		 */
		//tieredPolicy.setMaxMergedSegmentMB(1024);
		conf.setMergePolicy(tieredPolicy);

		writer = new IndexWriter(FSDirectory.open(indexTemp), conf);

		workers = new Worker[Configuration.numThreads];
		for (int k = 0; k < workers.length; k++)
			workers[k] = new Worker(k, caseData, writer, output, this);

		//Execução dos workers após todos terem sido instanciados e terem inicializado suas tarefas
		for (int k = 0; k < workers.length; k++)
			workers[k].start();

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
			IndexFiles.getInstance().firePropertyChange("processed", -1, stats.getProcessed());
			IndexFiles.getInstance().firePropertyChange("progresso", 0, (int)(stats.getVolume()/1000000));

			someWorkerAlive = false;
			for (int k = 0; k < workers.length; k++) {
				if (workers[k].exception != null && exception == null)
					exception = workers[k].exception;

				/*
				 *  TODO sincronizar teste, pois pode ocorrer condiÃ§Ã£o de corrida e o teste nÃ£o detectar um Ãºltimo item sendo processado
				 *  NÃ£o Ã© demasiado grave pois serÃ¡ detectado o problema no log de estatÃ­sticas e o usuÃ¡rio serÃ¡ informado do erro. 
				 */
				if (caseData.getEvidenceFiles().size() > 0 || workers[k].evidence != null || produtor.isAlive() ) {
					someWorkerAlive = true;
				}

				// TODO verificar se algum worker morreu e reiniciÃ¡-lo? (Nao deve ocorrer...)
			}

			if (exception != null)
				throw exception;

		}

		for (int k = 0; k < workers.length; k++) {
			workers[k].finish();
			//workers[k].join(5);
		}
		ParsingReader.shutdownTasks();

	}

	private void finalizarIndexacao() throws IOException {

		if (Configuration.forceMerge) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Otimizando Ã­ndice...");
			System.out.println(new Date() + "\t[INFO]\t" + "Otimizando Ã­ndice...");
			try {
				writer.forceMerge(1);
			} catch (Throwable e) {
				System.out.println(new Date() + "\t[ALERTA]\t" + "Erro durante otimizaÃ§Ã£o: " + e);
			}

		}

		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Fechando Ã­ndice...");
		System.out.println(new Date() + "\t[INFO]\t" + "Fechando Ã­ndice...");
		writer.close();

		if (!indexTemp.getCanonicalPath().equalsIgnoreCase(indexDir.getCanonicalPath())) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Copiando Ã­ndice...");
			System.out.println(new Date() + "\t[INFO]\t" + "Copiando Ã­ndice...");
			IOUtil.copiaDiretorio(indexTemp, indexDir);			
		}
		
		try {
			IOUtil.deletarDiretorio(Configuration.indexerTemp);
		} catch (IOException e) {
			System.out.println(new Date() + "\t[AVISO]\t" + "NÃ£o foi possÃ­vel apagar " + Configuration.indexerTemp.getPath());
		}

		if (caseData.containsReport() || ExportFileTask.hasCategoryToExtract())
			new File(output, "data/containsReport.flag").createNewFile();

	}

	private void configurarCategorias() throws Exception {
		System.out.println(new Date() + "\t[INFO]\t" + "Configurando categorias...");
		TreeSet<String> categories = IOUtil.loadKeywordSet(output.getAbsolutePath() + "/categorias.txt", "UTF-8");

		if (caseData.getBookmarks().size() > 0) {
			for (FileGroup bookmark : caseData.getBookmarks())
				categories.add(bookmark.getName().replaceAll("\"", "\\\""));
		}
		categories.addAll(SetCategoryTask.getCategories());

		// filtra categorias vazias
		if (categories.size() != 0) {
			InicializarBusca.inicializar(output.getAbsolutePath() + "/index");
			ArrayList<String> palavrasFinais = new ArrayList<String>();
			for (String categoria : categories) {
				if (Thread.interrupted()) {
					App.get().destroy();
					throw new InterruptedException("IndexaÃ§Ã£o cancelada!");
				}

				String query = "categoria:\"" + categoria.replace("\"", "\\\"") + "\"";
				PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(query));
				SearchResult result = pesquisa.pesquisarTodos();

				if (result.length > 0)
					palavrasFinais.add(categoria);

			}
			// fecha o Ã­ndice
			App.get().destroy();

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
					throw new InterruptedException("IndexaÃ§Ã£o cancelada!");
				}

				PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(palavra));
				if (pesquisa.pesquisarTodos().length > 0)
					palavrasFinais.add(palavra);
			}
			// fecha o Ã­ndice
			App.get().destroy();
			IOUtil.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/palavras-chave.txt", "UTF-8");
			int filtradas = palavras.size() - palavrasFinais.size();
			System.out.println(new Date() + "\t[INFO]\t" + "Filtradas " + filtradas + " palavras-chave.");
		} else
			System.out.println(new Date() + "\t[INFO]\t" + "Nenhuma palavra-chave prÃ©-configurada para filtrar.");

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
				throw new InterruptedException("IndexaÃ§Ã£o cancelada!");
			}
			if (liveDocs != null && !liveDocs.get(i))
				continue;
			int id = Integer.parseInt(reader.document(i).get(IndexItem.ID));
			if (splitedIds.contains(id))
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
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Obtendo mapeamento de versÃµes de visualizaÃ§Ã£o para originais...");
			System.out.println(new Date() + "\t[INFO]\t" + "Obtendo mapa versÃµes de visualizaÃ§Ã£o -> originais...");

			InicializarBusca.inicializar(output.getAbsolutePath() + "/index");
			String query = IndexItem.EXPORT + ":(files && (\"AD html\" \"AD rtf\"))";
			PesquisarIndice pesquisa = new PesquisarIndice(PesquisarIndice.getQuery(query));
			ScoreDoc[] alternatives = pesquisa.filtrarFragmentos1(pesquisa.pesquisarTodos1());

			HashMap<String, Integer> viewMap = new HashMap<String, Integer>();
			for (int i = 0; i < alternatives.length; i++) {
				if (Thread.interrupted()) {
					App.get().destroy();
					throw new InterruptedException("IndexaÃ§Ã£o cancelada!");
				}
				Document doc = App.get().searcher.doc(alternatives[i].doc);
				String ftkId = doc.get(IndexItem.FTKID);
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
				String ftkId = doc.get(IndexItem.FTKID);
				String export = doc.get(IndexItem.EXPORT);

				Integer viewDocId = viewMap.get(ftkId);
				if (viewDocId != null && viewDocId != i && !viewToRaw.isView(viewDocId) && !export.contains(".[AD]."))
					viewToRaw.put(viewDocId, i);

			}
			reader.close();

			System.out.println(new Date() + "\t[INFO]\t" + "Obtidos " + viewToRaw.getMappings() + " mapeamentos de versÃµes de visualizaÃ§Ã£o para originais.");
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
			ids[i] = Integer.parseInt(doc.get(IndexItem.ID));
		}
		
		reader.close();
		IOUtil.writeObject(ids, output.getAbsolutePath() + "/data/ids.map");
	}

	private void salvarTamanhoTextosExtraidos() throws Exception {

		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Salvando tamanho dos textos extraÃ­dos...");
		System.out.println(new Date() + "\t[INFO]\t" + "Salvando tamanho dos textos extraÃ­dos...");

		int[] textSizesArray = new int[stats.getLastId() + 1];

		for (int i = 0; i < textSizes.size(); i++) {
			IdLenPair pair = textSizes.get(i);
			textSizesArray[pair.id] = pair.length;
		}

		IOUtil.writeObject(textSizesArray, output.getAbsolutePath() + "/data/texts.size");
	}
	

	private void prepararReport() throws Exception {
		if (output.exists() && !IndexFiles.getInstance().appendIndex) {
			IndexFiles.getInstance().firePropertyChange("mensagem", "", "Apagando " + output.getAbsolutePath());
			System.out.println(new Date() + "\t[INFO]\t" + "Apagando " + output.getAbsolutePath());
			IOUtil.deletarDiretorio(output);
		}

		File export = new File(output.getParentFile(), ExportFileTask.EXTRACT_DIR);
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
			throw new IOException("NÃ£o foi possÃ­vel criar diretÃ³rio " + output.getAbsolutePath());

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
				throw new IOException("NÃ£o foi possÃ­vel criar diretÃ³rio " + dataDir.getAbsolutePath());

	}

}
