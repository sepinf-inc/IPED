package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Date;

import javax.swing.JOptionPane;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.search.App;
import gpinf.dev.data.CaseData;

/**
 * Classe que armazena estatísticas diversas, como número de itens processados, volume processado,
 * número de timeouts, duplicados ignorados, etc.
 * Contém métodos para enviar as estatísticas para arquivo de log.
 */
public class Statistics {
	
	private static Logger LOGGER = LoggerFactory.getLogger(Statistics.class);
	
	CaseData caseData;
	File indexDir;
	
	//EstatÃ­sticas
	Date start = new Date();
	int splits = 0;
	int timeouts = 0;
	int processed = 0;
	int activeProcessed = 0;
	long volumeIndexed = 0;
	int lastId = -1;
	int corruptCarveIgnored = 0;
	int ignored = 0;
	int previousIndexedFiles = 0;
	
	public Statistics(CaseData caseData, File indexDir){
		this.caseData = caseData;
		this.indexDir = indexDir;
	}
	
	synchronized public int getSplits() {
		return splits;
	}

	synchronized public void incSplits() {
		splits++;
	}

	synchronized public int getTimeouts() {
		return timeouts;
	}

	synchronized public void incTimeouts() {
		timeouts++;
	}

	synchronized public void incProcessed() {
		processed++;
	}

	synchronized public int getProcessed() {
		return processed;
	}

	synchronized public void incActiveProcessed() {
		activeProcessed++;
	}

	synchronized public int getActiveProcessed() {
		return activeProcessed;
	}

	synchronized public void addVolume(long volume) {
		volumeIndexed += volume;
	}

	synchronized public long getVolume() {
		return volumeIndexed;
	}

	synchronized public int getCorruptCarveIgnored() {
		return corruptCarveIgnored;
	}

	synchronized public void incCorruptCarveIgnored() {
		corruptCarveIgnored++;
	}

	synchronized public int getIgnored() {
		return ignored;
	}

	synchronized public void incIgnored() {
		ignored++;
	}

	synchronized public void updateLastId(int id) {
		if (id > lastId)
			lastId = id;
	}

	synchronized public int getLastId() {
		return lastId;
	}
	
	synchronized public void setLastId(int id) {
		lastId = id;
	}

	public void logarEstatisticas(Manager manager) throws Exception {

		int processed = getProcessed();
		int extracted = ExportFileTask.getSubitensExtracted();
		int activeFiles = getActiveProcessed();
		int carvedIgnored = getCorruptCarveIgnored();
		int ignored = getIgnored();
		
		long totalTime = 0;
		Worker[] workers = manager.getWorkers();
		long[] taskTimes = new long[workers[0].tasks.size()];
		for(Worker worker : workers)
			for(int i = 0; i < taskTimes.length; i++){
				taskTimes[i] += worker.tasks.get(i).getTaskTime();
				totalTime += taskTimes[i];
			}
		totalTime = totalTime / (1000000 * Configuration.numThreads);
		for(int i = 0; i < taskTimes.length; i++){
			long sec = taskTimes[i] / (1000000 * Configuration.numThreads);
			LOGGER.info(workers[0].tasks.get(i).getClass().getSimpleName() + "\t-\tTempo de execução: " + sec + "s (" + (100 * sec)/totalTime + "%)");
		}
		
		LOGGER.info("Divisões de arquivo: {}", getSplits());
		LOGGER.info("Timeouts: {}", getTimeouts());
		LOGGER.info("Exceções de parsing: {}", IndexerDefaultParser.parsingErrors);
		LOGGER.info("Subitens descobertos: {}", ParsingTask.getSubitensDiscovered());
		LOGGER.info("Itens extraídos: {}", extracted);
		LOGGER.info("Itens de Carving: {}", CarveTask.getItensCarved());
		LOGGER.info("Carvings corrompidos ignorados: {}", carvedIgnored);
		LOGGER.info("Itens ignorados: {}", ignored);

		if (caseData.getAlternativeFiles() > 0)
			LOGGER.info("Processadas {} versões de visualização dos itens ao invés das originais.", caseData.getAlternativeFiles());

		IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir));
		int indexed = reader.numDocs() - getSplits() - previousIndexedFiles;
		reader.close();

		if (indexed != processed && ExportFileTask.hasCategoryToExtract())
			LOGGER.info("Itens indexados: {}", indexed);

		long processedVolume = getVolume() / (1024 * 1024);
		
		if (activeFiles != processed)
			LOGGER.info("Itens ativos processados: {}", activeFiles);

		LOGGER.info("Total processado: {} itens em {} segundos ({} MB)", processed, ((new Date()).getTime() - start.getTime()) / 1000, processedVolume );

		int discovered = caseData.getDiscoveredEvidences();
		if (processed != discovered)
		    LOGGER.error("Alerta: Processados " + processed + " itens de " + discovered);

		if(!ExportFileTask.hasCategoryToExtract()){
			if (indexed != discovered - carvedIgnored - ignored)
			    LOGGER.error("Alerta: Indexados " + indexed + " itens de " + (discovered - carvedIgnored - ignored));
		}/*else 
			if (indexed != extracted)
				throw new Exception("Indexados " + indexed + " itens de " + extracted);
		*/
	}
	
	public void printSystemInfo() throws Exception {
		LOGGER.info("Sistema Operacional: {}", System.getProperty("os.name"));
		LOGGER.info("Versão Java: {}", System.getProperty("java.version"));
		LOGGER.info("Arquitetura: {}", System.getProperty("os.arch"));
		LOGGER.info("Processadores: {}", Runtime.getRuntime().availableProcessors());
		LOGGER.info("numThreads: {}", Configuration.numThreads);

		long maxMemory = Runtime.getRuntime().maxMemory() / 1000000;
		LOGGER.info("Memória disponível: {} MB", maxMemory);
		
		StringBuilder args = new StringBuilder();
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
		for (String arg : bean.getInputArguments())
			args.append(arg + " ");
		LOGGER.info("Argumentos: {}{}", args.toString(), System.getProperty("sun.java.command"));

		/*
		 * System.out.println(new Date() + "\t[INFO]\t" + "ConfiguraÃ§Ãµes:");
		 * for(Entry<Object, Object> entry :
		 * Configuration.properties.entrySet()){ System.out.println(new Date() +
		 * "\t[INFO]\t" + entry.getKey() + " = " + entry.getValue()); }
		 */
		int minMemPerThread = 200;
		if (maxMemory / Configuration.numThreads < minMemPerThread) {
			String memoryAlert = "Pouca memória disponível: menos de " + minMemPerThread + "MB por thread de processamento." + "\nIsso pode causar lentidão e erros de parsing de arquivos complexos."
					+ "\n\tUtilize uma JVM x64 (preferencial), " + "\n\taumente a memória da JVM via parâmetro -Xmx " + "\n\tou diminua o parâmetro numThreads na configuração avançada.";
			JOptionPane.showMessageDialog(App.get(), memoryAlert, "Alerta de Memória", JOptionPane.WARNING_MESSAGE);
			throw new Exception(memoryAlert);
		}

	}
	
}
