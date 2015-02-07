package dpf.sp.gpinf.indexer.process;

import gpinf.dev.data.CaseData;

import java.io.File;
import java.util.Date;

import javax.swing.JOptionPane;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.search.App;

/*
 * Classe que armazena estatísticas diversas, como número de itens processados, volume processado,
 * número de timeouts, duplicados ignorados, etc.
 * Contém métodos para enviar as estatísticas para arquivo de log.
 */
public class Statistics {

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
	int duplicatesIgnored = 0;
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

	synchronized public int getDuplicatesIgnored() {
		return duplicatesIgnored;
	}

	synchronized public void incDuplicatesIgnored() {
		duplicatesIgnored++;
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

	public void logarEstatisticas() throws Exception {

		int processed = getProcessed();
		int extracted = ExportFileTask.getSubitensExtracted();
		int activeFiles = getActiveProcessed();
		int carvedIgnored = getCorruptCarveIgnored();
		int duplicatesIgnored = getDuplicatesIgnored();
		
		System.out.println(new Date() + "\t[INFO]\t" + "DivisÃµes de arquivo: " + getSplits());
		System.out.println(new Date() + "\t[INFO]\t" + "Timeouts: " + getTimeouts());
		System.out.println(new Date() + "\t[INFO]\t" + "ExceÃ§Ãµes de parsing: " + IndexerDefaultParser.parsingErrors);
		System.out.println(new Date() + "\t[INFO]\t" + "Subitens descobertos: " + ExpandContainerTask.getSubitensDiscovered());
		System.out.println(new Date() + "\t[INFO]\t" + "Itens extraÃ­dos: " + extracted);
		System.out.println(new Date() + "\t[INFO]\t" + "Itens de Carving: " + CarveTask.getItensCarved());
		System.out.println(new Date() + "\t[INFO]\t" + "Carvings corrompidos ignorados: " + carvedIgnored);
		System.out.println(new Date() + "\t[INFO]\t" + "Duplicados descartados: " + duplicatesIgnored);

		if (caseData.getAlternativeFiles() > 0)
			System.out.println(new Date() + "\t[INFO]\t" + "Processadas " + caseData.getAlternativeFiles() + " versÃµes de visualizaÃ§Ã£o dos itens ao invÃ©s das originais.");

		IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir));
		int indexed = reader.numDocs() - getSplits() - previousIndexedFiles;
		reader.close();

		if (indexed != processed && ExportFileTask.hasCategoryToExtract())
			System.out.println(new Date() + "\t[INFO]\t" + "Itens indexados: " + indexed);

		long processedVolume = getVolume() / (1024 * 1024);
		
		if (activeFiles != processed)
			System.out.println(new Date() + "\t[INFO]\t" + "Itens ativos processados: " + activeFiles);

		System.out.println(new Date() + "\t[INFO]\t" + "Total processado: " + processed + " itens em " + 
				((new Date()).getTime() - start.getTime()) / 1000 + " segundos (" + processedVolume + " MB)");

		int discovered = caseData.getDiscoveredEvidences();
		if (processed != discovered)
			throw new Exception("Processados " + processed + " itens de " + discovered);

		if(!ExportFileTask.hasCategoryToExtract()){
			if (indexed + carvedIgnored + duplicatesIgnored != discovered)
				throw new Exception("Indexados " + indexed + " itens de " + discovered);
		}/*else 
			if (indexed != extracted)
				throw new Exception("Indexados " + indexed + " itens de " + extracted);
		*/
	}
	
	public void printSystemInfo() throws Exception {

		System.out.println(new Date() + "\t[INFO]\t" + "Sistema Operacional: " + System.getProperty("os.name"));
		System.out.println(new Date() + "\t[INFO]\t" + "VersÃ£o Java: " + System.getProperty("java.version"));
		System.out.println(new Date() + "\t[INFO]\t" + "Arquitetura: " + System.getProperty("os.arch"));
		System.out.println(new Date() + "\t[INFO]\t" + "Processadores: " + Runtime.getRuntime().availableProcessors());
		System.out.println(new Date() + "\t[INFO]\t" + "numThreads: " + Configuration.numThreads);

		int minMemPerThread = 200;
		long maxMemory = Runtime.getRuntime().maxMemory() / 1000000;
		System.out.println(new Date() + "\t[INFO]\t" + "MemÃ³ria disponÃ­vel: " + maxMemory + " MB");

		/*
		 * System.out.println(new Date() + "\t[INFO]\t" + "ConfiguraÃ§Ãµes:");
		 * for(Entry<Object, Object> entry :
		 * Configuration.properties.entrySet()){ System.out.println(new Date() +
		 * "\t[INFO]\t" + entry.getKey() + " = " + entry.getValue()); }
		 */
		if (maxMemory / Configuration.numThreads < minMemPerThread) {
			String memoryAlert = "Pouca memÃ³ria disponÃ­vel: menos de " + minMemPerThread + "MB por thread de indexaÃ§Ã£o." + "\nIsso pode causar lentidÃ£o e erros de parsing de arquivos complexos."
					+ "\n\tUtilize uma JVM x64 (preferencial), " + "\n\taumente a memÃ³ria da JVM via parÃ¢metro -Xmx " + "\n\tou diminua o parÃ¢metro numThreads em IndexerConfig.txt";
			JOptionPane.showMessageDialog(App.get(), memoryAlert, "Alerta de MemÃ³ria", JOptionPane.WARNING_MESSAGE);
			throw new Exception(memoryAlert);
		}

	}
	
}
