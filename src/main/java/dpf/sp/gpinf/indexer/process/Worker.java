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

import java.io.File;
import java.io.InterruptedIOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.lucene.index.IndexWriter;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.Parser;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.io.TimeoutException;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.process.task.ExportCSVTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.util.IOUtil;

/*
 * Classe responsável pelo processamento de cada item, chamando as diversas tarefas de processamento:
 * análise de assinatura, hash, expansão de itens, indexação, carving, etc.
 */
public class Worker extends Thread {

	LinkedBlockingDeque<EvidenceFile> evidences;

	public IndexWriter writer;
	String baseFilePath;
	public boolean containsReport;

	public IndexerDefaultParser autoParser;
	public Detector detector;
	public TikaConfig config;
	
	public volatile AbstractTask runningTask;
	public List<AbstractTask> tasks = new ArrayList<AbstractTask>();

	public Manager manager;
	public File output;
	public CaseData caseData;
	public volatile Exception exception;
	public volatile EvidenceFile evidence;

	public static class IdLenPair {
		int id, length;

		public IdLenPair(int id, long len) {
			this.id = id;
			this.length = (int) (len / 1000);
		}

	}

	public static void resetStaticVariables() {
		IndexerDefaultParser.parsingErrors = 0;
		ExpandContainerTask.subitensDiscovered = 0;
		ExportFileTask.subitensExtracted = 0;
		ExportFileTask.subDirCounter = 0;
		ExportCSVTask.headerWritten = false;
		CarveTask.itensCarved = 0;
		ParsingReader.threadPool = Executors.newCachedThreadPool();
	}

	public Worker(int k, CaseData caseData, IndexWriter writer, File output, Manager manager) throws Exception {
		super("Worker-" + k);
		this.caseData = caseData;
		this.evidences = caseData.getEvidenceFiles();
		this.containsReport = caseData.containsReport();
		this.writer = writer;
		this.output = output;
		this.manager = manager;
		baseFilePath = output.getParentFile().getAbsolutePath();
		
		config = TikaConfig.getDefaultConfig();
		detector = config.getDetector();

		autoParser = new IndexerDefaultParser();
		autoParser.setFallback((Parser) Configuration.fallBackParser.newInstance());
		autoParser.setErrorParser((Parser) Configuration.errorParser.newInstance());
		
		new TaskInstaller().installProcessingTasks(this);
		initTasks();

	}
	
	private void initTasks() throws Exception{
		for(AbstractTask task : tasks)
			task.init(Configuration.properties, new File(Configuration.configPath, "conf"));
	}
	
	private void finishTasks() throws Exception{
		for(AbstractTask task : tasks)
			task.finish();
	}
	
	//Alguns itens ainda não tem um File setado, como report do FTK1
	private void checkFile(EvidenceFile evidence){
		String filePath = evidence.getFileToIndex();
		if (evidence.getFile() == null && !filePath.isEmpty()) {
			File file = IOUtil.getRelativeFile(baseFilePath, filePath);
			evidence.setFile(file);
			evidence.setLength(file.length());
		}
	}
	

	public void process(EvidenceFile evidence) {
		
		EvidenceFile prevEvidence = this.evidence;
		this.evidence = evidence;
		
		try {

			if (IndexFiles.getInstance().verbose)
				System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " Indexando " + evidence.getPath());

			checkFile(evidence);
			
			//Loop principal que executa cada tarefa de processamento
			for(AbstractTask task : tasks)
				if(!evidence.isToIgnore()){
					processTask(evidence, task);
				}
			
			
			// ESTATISTICAS
			manager.stats.incProcessed();
			if ((!evidence.isSubItem() && !evidence.isCarved()) || ItemProducer.indexerReport) {
				manager.stats.incActiveProcessed();
				Long len = evidence.getLength();
				if(len == null)
					len = 0L;
				manager.stats.addVolume(len);
			}

		} catch (Throwable t) {	
			//ABORTA PROCESSAMENTO NO CASO DE QQ OUTRO ERRO
			if (exception == null) {
				exception = new Exception(this.getName() + " Erro durante processamento de " + evidence.getPath() + " (" + evidence.getLength() + "bytes)");
				exception.initCause(t);
			}

		}

		this.evidence = prevEvidence;

	}
	
	private void processTask(EvidenceFile evidence, AbstractTask task) throws Exception{
		AbstractTask prevTask = runningTask;
		runningTask = task;
		try {
			task.process(evidence);
			
		} catch (TimeoutException e) {
			System.out.println(new Date() + "\t[ALERT]\t" + this.getName() + " TIMEOUT ao processar " + evidence.getPath() + " (" + evidence.getLength() + "bytes)\t" + e);
			manager.stats.incTimeouts();
			evidence.setTimeOut(true);
			processTask(evidence, task);

		}catch (Throwable t) {
			//Ignora arquivos recuperados e corrompidos
			if(t.getCause() instanceof TikaException && evidence.isCarved()){
				manager.stats.incCorruptCarveIgnored();
				//System.out.println(new Date() + "\t[AVISO]\t" + this.getName() + " " + "Ignorando arquivo recuperado corrompido " + evidence.getPath() + " (" + length + "bytes)\t" + t.getCause());
				evidence.setToIgnore(true);
				if(evidence.isSubItem()){
					evidence.getFile().delete();
				}
				
			}else
				throw t;
			
		}
		runningTask = prevTask;
	}
	
	public void finish() throws Exception{
		this.interrupt();
		finishTasks();
	}

	@Override
	public void run() {

		System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " iniciada.");
		
		while (!this.isInterrupted() && exception == null) {

			try {
				evidence = null;
				evidence = evidences.takeFirst();
				process(evidence);

			} catch (InterruptedException e) {
				break;
			}
		}

		if (evidence == null)
			System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " finalizada.");
		else
			System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " interrompida com " + evidence.getPath() + " (" + evidence.getLength() + "bytes)");
	}

}
