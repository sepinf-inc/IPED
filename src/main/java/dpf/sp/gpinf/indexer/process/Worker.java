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
import gpinf.dev.filetypes.UnknownFileType;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.io.TimeoutException;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;
import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.FileDocument;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.process.task.ExportCSVTask;
import dpf.sp.gpinf.indexer.process.task.ComputeHashTask;
import dpf.sp.gpinf.indexer.process.task.ComputeHashTask.HashValue;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IndexerContext;

/*
 * Classe responsável pelo processamento de cada item, chamando as diversas etapas de processamento:
 * análise de assinatura, hash, expansão de itens, indexação, carving, etc.
 */
public class Worker extends Thread {

	LinkedBlockingDeque<EvidenceFile> evidences;

	IndexWriter writer;
	String baseFilePath;
	boolean containsReport;

	private static int splits = 0;
	private static int timeouts = 0;
	private static int processed = 0;
	private static int activeProcessed = 0;
	private static long volumeIndexed = 0;
	private static int lastId = -1;
	private static int corruptCarveIgnored = 0;
	private static int duplicatesIgnored = 0;
	// public static ConcurrentSkipListMap<Integer, Long> textSizes;
	public static List<IdLenPair> textSizes;
	public static HashMap<HashValue, HashValue> hashMap;
	public static HashSet<Integer> splitedIds;

	// public Object lock = new Object();

	// private ParseContext context;
	private IndexerDefaultParser autoParser;
	private Detector detector;
	public TikaConfig config;
	private SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	public ComputeHashTask hasher;
	private ExportCSVTask fileLister;
	private ExportFileTask extractor;
	private CarveTask carver;

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
		splits = 0;
		timeouts = 0;
		processed = 0;
		activeProcessed = 0;
		volumeIndexed = 0;
		// textSizes = new ConcurrentSkipListMap<Integer, Long>();
		textSizes = Collections.synchronizedList(new ArrayList<IdLenPair>());
		lastId = -1;
		hashMap = new HashMap<HashValue, HashValue>();
		splitedIds = new HashSet<Integer>();
		IndexerDefaultParser.parsingErrors = 0;
		ExpandContainerTask.subitensDiscovered = 0;
		ExportFileTask.subitensExtracted = 0;
		ExportFileTask.subDirCounter = 0;
		ExportCSVTask.headerWritten = false;
		CarveTask.itensCarved = 0;
		corruptCarveIgnored = 0;
		duplicatesIgnored = 0;
		ParsingReader.threadPool = Executors.newCachedThreadPool();
	}

	public Worker(int k, CaseData caseData, IndexWriter writer, File output) throws Exception {
		super("Worker-" + k);
		this.caseData = caseData;
		this.evidences = caseData.getEvidenceFiles();
		this.containsReport = caseData.containsReport();
		this.writer = writer;
		this.output = output;
		baseFilePath = output.getParentFile().getAbsolutePath();
		if (Configuration.hashAlgorithm != null)
			hasher = new ComputeHashTask(Configuration.hashAlgorithm, output);
		if (Configuration.exportFileProps)
			fileLister = new ExportCSVTask(output);
		config = TikaConfig.getDefaultConfig();
		detector = config.getDetector();
		extractor = new ExportFileTask(config, output, hasher, hashMap);

		autoParser = new IndexerDefaultParser();
		autoParser.setFallback((Parser) Configuration.fallBackParser.newInstance());
		autoParser.setErrorParser((Parser) Configuration.errorParser.newInstance());

	}

	synchronized public static int getSplits() {
		return splits;
	}

	synchronized public static void incSplits() {
		splits++;
	}

	synchronized public static void remindSplitedDoc(int id) {
		splitedIds.add(id);
	}

	synchronized public static int getTimeouts() {
		return timeouts;
	}

	synchronized public static void incTimeouts() {
		timeouts++;
	}

	synchronized public static void incProcessed() {
		processed++;
	}

	synchronized public static int getProcessed() {
		return processed;
	}

	synchronized public static void incActiveProcessed() {
		activeProcessed++;
	}

	synchronized public static int getActiveProcessed() {
		return activeProcessed;
	}

	synchronized public static void addVolume(long volume) {
		volumeIndexed += volume;
	}

	synchronized public static long getVolume() {
		return volumeIndexed;
	}

	synchronized public static int getCorruptCarveIgnored() {
		return corruptCarveIgnored;
	}

	synchronized public static void incCorruptCarveIgnored() {
		Worker.corruptCarveIgnored++;
	}

	synchronized public static int getDuplicatesIgnored() {
		return duplicatesIgnored;
	}

	synchronized public static void incDuplicatesIgnored() {
		Worker.duplicatesIgnored++;
	}

	synchronized private static void updateLastId(int id) {
		if (id > lastId)
			lastId = id;
	}

	synchronized public static int getLastId() {
		return lastId;
	}
	
	synchronized public static void setLastId(int id) {
		lastId = id;
	}

	public void process(EvidenceFile evidence) {
		EvidenceFile prevEvidence = this.evidence;
		this.evidence = evidence;
		long length = 0;
		ParsingReader reader = null;
		TikaInputStream tis = null;
		try {

			if (IndexFiles.getInstance().verbose)
				System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " Indexando " + evidence.getPath());

			String filePath = evidence.getFileToIndex();
			if (evidence.getFile() == null && !filePath.isEmpty()) {
				File file = IOUtil.getRelativeFile(baseFilePath, filePath);
				evidence.setFile(file);
				length = file.length();
			}

			Long lenObj = evidence.getLength();
			if (lenObj != null)
				length = lenObj;

			Metadata metadata = new Metadata();
			metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
			metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
			if (evidence.timeOut)
				metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true");

			try {
				tis = evidence.getTikaStream();
				
			} catch (IOException e) {
				System.out.println(new Date() + "\t[ALERTA]\t" + this.getName() + " Erro ao abrir: " + evidence.getPath() + " " + e.toString());
			}
			

			// CALCULA HASH
			if (hasher != null && tis != null)
				hasher.compute(evidence);
			String hash = evidence.getHash();
			
			
			// Verificação de duplicados
			if(!evidence.timeOut){
				if (hash == null)
					evidence.setPrimaryHash(true);
				else{
					HashValue hashValue = new HashValue(hash);
					synchronized (hashMap) {
						if(!hashMap.containsKey(hashValue)){
							hashMap.put(hashValue, hashValue);
							evidence.setPrimaryHash(true);
						}else
							evidence.setPrimaryHash(false);
							
					}
				}
			}
			
			
			//Ignora duplicados
			if(Configuration.ignoreDuplicates && !evidence.isPrimaryHash() && !evidence.isDir() && !ItemProducer.indexerReport){
				incProcessed();
				incDuplicatesIgnored();
				if (fileLister != null)
					fileLister.save(evidence);
				if (!evidence.isSubItem() && !evidence.isCarved()) {
					incActiveProcessed();
					addVolume(length);
				}
				this.evidence = prevEvidence;
				return;
			}

			// ANALISE DE ASSINATURA
			MediaType type = evidence.getMediaType();
			if (type == null) {
				try {
					if(Configuration.processFileSignatures)
						type = detector.detect(tis, metadata).getBaseType();
					else
						type = detector.detect(null, metadata).getBaseType();

				} catch (Exception e) {
					type = MediaType.OCTET_STREAM;
					System.out.println(new Date() + "\t[ALERTA]\t" + this.getName() + " Detecção do tipo abortada: " + evidence.getPath() + " (" + length + " bytes)\t\t" + e.toString());
				}
				evidence.setMediaType(type);
			}
			String typeStr = type.toString();
			metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, typeStr);

			
			// DEFINIÇÃO DO TIPO, CASO NÃO ESTEJA DEFINIDO
			if (evidence.getType() == null) {
				String ext = ExportFileTask.getExtBySig(config, evidence);
				if (!ext.isEmpty()){
					if(ext.length() > 1 && evidence.isCarved() && evidence.getName().startsWith("Carved-")){
						evidence.setName(evidence.getName() + ext);
						evidence.setPath(evidence.getPath() + ext);
					}
					ext = ext.substring(1);
				}
				evidence.setType(new UnknownFileType(ext));
			}

			// DEFINIÇÃO DE CATEGORIA
			if (evidence.getCategorySet().size() == 0)
				if (!containsReport || ExportFileTask.hasCategoryToExtract()) {
					SetCategoryTask.setCategories(evidence);
				} else
					evidence.addCategory(Configuration.defaultCategory);
			
			
			
			// DEFINE CONTEXTO: PARSING RECURSIVO, ETC
			ParseContext context = new ParseContext();
			context.set(Parser.class, autoParser);
			IndexerContext indexerContext = new IndexerContext(evidence);
			context.set(IndexerContext.class, indexerContext);
			context.set(EmbeddedDocumentExtractor.class, new ExpandContainerTask(context, this));
			context.set(EvidenceFile.class, evidence);

			// Tratamento p/ acentos de subitens de ZIP
			ArchiveStreamFactory factory = new ArchiveStreamFactory();
			factory.setEntryEncoding("Cp850");
			context.set(ArchiveStreamFactory.class, factory);
			
			
			/*PDFParserConfig config = new PDFParserConfig();
			config.setExtractInlineImages(true);
			context.set(PDFParserConfig.class, config);
			*/
			
			
			/*
			 * EXTRAÇÂO DE SUBITENS USANDO PARSINGREADER PARA MONITORAR TIMEOUTS
			 * ARMAZENA O TEXTO EXTRAÍDO, CASO PEQUENO, PARA REUTILIZAR DURANTE INDEXAÇÃO,
			 * ASSIM O ARQUIVO NÃO É DECODIFICADO NOVAMENTE
			 * 
			 * TAMBÉM REALIZA O PARSING DE ITENS DE CARVING PARA IGNORAR CORROMPIDOS, CASO
			 * A INDEXAÇÃO (QUE TB FAZ O PARSING) ESTEJA DESABILITADA.
			 */
			StringReader textCacheReader = null;
			long textLength = 0;
			if ((ExpandContainerTask.isToBeExpanded(evidence) && tis != null && !evidence.timeOut)
			|| ((ExportFileTask.hasCategoryToExtract() || !Configuration.indexFileContents) && CarveTask.ignoreCorrupted && evidence.isCarved())) {

				reader = new ParsingReader(autoParser, tis, metadata, context);
				StringWriter writer;
				char[] cbuf = new char[128*1024];
				int len = 0;
				int numFrags = 0;
				do {
					numFrags++;
					writer = new StringWriter();
					while ((len = reader.read(cbuf)) != -1 && !this.isInterrupted())
						writer.write(cbuf, 0, len);

				} while (reader.nextFragment());

				reader.close2();

				if (Configuration.indexFileContents)
					if (numFrags == 1) {
						String text = writer.toString();
						textLength = text.length();
						textCacheReader = new StringReader(text);

					} else {
						metadata = new Metadata();
						tis = evidence.getTikaStream();
						metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, typeStr);
						metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
						if (evidence.timeOut)
							metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true");

						context.set(EvidenceFile.class, evidence);
						context.set(EmbeddedDocumentExtractor.class, new ExpandContainerTask(context) {
							@Override
							public boolean shouldParseEmbedded(Metadata arg0) {
								return false;
							}
						});
					}

			}
			
			

			// EXPORTA ARQUIVO CASO CONFIGURADO
			if (ExportFileTask.hasCategoryToExtract() || evidence.isToExtract()) {
				if (!evidence.isSubItem() && !evidence.isExtracted() && (ExportFileTask.isToBeExtracted(evidence) || evidence.isToExtract())) {
					evidence.setToExtract(true);
					extractor.extractFile(evidence);
					evidence.setExtracted(true);
					ExportFileTask.incSubitensExtracted();
				}
				
			}
			if (evidence.isSubItem() && evidence.isToExtract() && !evidence.isExtracted()) {
				IOUtil.closeQuietly(tis);
				tis = null;

				extractor.renameToHash(evidence);
				evidence.setExtracted(true);
				ExportFileTask.incSubitensExtracted();

				if (Configuration.indexFileContents)
					try {
						tis = evidence.getTikaStream();
					} catch (IOException e) {
						System.out.println(new Date() + "\t[ALERTA]\t" + this.getName() + " Erro ao abrir: " + evidence.getFileToIndex() + " " + e.toString());
					}
			}
			
			
			//DATA CARVING
			new CarveTask(evidence, this).parse();
			
			
			//Ignora itens não exportados, caso habilitada exportação
			boolean toIndex = true;
			if (ExportFileTask.hasCategoryToExtract() && !evidence.isToExtract()) {
				toIndex = false;
				IOUtil.closeQuietly(tis);
				if (evidence.isSubItem()) {
					if (!evidence.getFile().delete())
						System.out.println(new Date() + "\t[AVISO]\t" + this.getName() + " Falha ao deletar " + evidence.getFile().getAbsolutePath());
				}
			}
			
			
			// INDEXAÇÃO
			if (toIndex) {
				updateLastId(evidence.getId());

				if (textCacheReader != null) {
					Document doc;
					if (Configuration.indexFileContents)
						doc = FileDocument.Document(evidence, textCacheReader, df);
					else
						doc = FileDocument.Document(evidence, null, df);
					
					writer.addDocument(doc);
					// textSizes.put(evidence.getId(), textLength);
					textSizes.add(new IdLenPair(evidence.getId(), textLength));

				} else {
					if (Configuration.indexFileContents && tis != null && 
						(Configuration.indexUnallocated || !CarveTask.UNALLOCATED_MIMETYPE.equals(type)))
							reader = new ParsingReader(autoParser, tis, metadata, context);
					else
							reader = null;

					Document doc = FileDocument.Document(evidence, reader, df);
					int fragments = 0;
					//Indexa os arquivos dividindo-os em fragmentos devido
					//a alto uso de RAM pela lib de indexação com docs gigantes
					do {
						if (++fragments > 1) {
							incSplits();
							if (fragments == 2)
								remindSplitedDoc(evidence.getId());

							if (IndexFiles.getInstance().verbose)
								System.out.println(new Date() + "\t[INFO]\t" + this.getName() + "  Dividindo texto de " + evidence.getPath());
						}

						writer.addDocument(doc);

					} while (!this.isInterrupted() && reader != null && reader.nextFragment());

					if (reader != null) {
						// textSizes.put(evidence.getId(),
						// reader.getTotalTextSize());
						textSizes.add(new IdLenPair(evidence.getId(), reader.getTotalTextSize()));
						reader.close2();

					} else {
						// textSizes.put(evidence.getId(), 0L);
						textSizes.add(new IdLenPair(evidence.getId(), 0));
						IOUtil.closeQuietly(tis);
					}

				}

			}
			
			
			//Não adianta setar propriedades dps de indexar, pois elas já foram armazenadas no índice
			//TODO: Utilizar banco de dados permitirá alterar atributos após indexação
			//if(evidence.isEncrypted())
			//	evidence.addCategory("Arquivos Criptografados");
			

			// EXPORTA PROPRIEDADES
			if (fileLister != null)
				fileLister.save(evidence);

			
			// ESTATISTICAS
			incProcessed();
			if ((!evidence.isSubItem() && !evidence.isCarved()) || ItemProducer.indexerReport) {
				incActiveProcessed();
				addVolume(length);
			}
			
			

		} catch (TimeoutException e) {
			System.out.println(new Date() + "\t[ALERT]\t" + this.getName() + " TIMEOUT ao processar " + evidence.getPath() + " (" + length + "bytes)\t" + e);
			incTimeouts();
			if (reader != null)
				reader.closeAndInterruptParsingTask();

			evidence.timeOut = true;
			process(evidence);

			// Processamento de subitem pode ser interrompido por TIMEOUT no
			// processamento do pai, no bloco acima (em outra Thread), devendo ser
			// reprocessado
		} catch (InterruptedIOException e1) {
			System.out.println(new Date() + "\t[AVISO]\t" + this.getName() + " " + "Interrompido processamento de " + evidence.getPath() + " (" + length + "bytes)\t" + e1);
			if (reader != null)
				reader.closeAndInterruptParsingTask();
			process(evidence);

			
		} catch (Throwable t) {
			
			//Ignora arquivos recuperados e corrompidos
			if(t.getCause() instanceof TikaException && evidence.isCarved()){
				incProcessed();
				incCorruptCarveIgnored();
				//System.out.println(new Date() + "\t[AVISO]\t" + this.getName() + " " + "Ignorando arquivo recuperado corrompido " + evidence.getPath() + " (" + length + "bytes)\t" + t.getCause());
				if(evidence.isSubItem()){
					IOUtil.closeQuietly(tis);
					evidence.getFile().delete();
				}
				
			//ABORTA PROCESSAMENTO NO CASO DE QQ OUTRO ERRO
			}else{
				// t.printStackTrace();
				if (exception == null) {
					exception = new Exception(this.getName() + " Erro durante processamento de " + evidence.getPath() + " (" + length + "bytes)");
					exception.initCause(t);
				}
				if (reader != null)
					reader.closeAndInterruptParsingTask();
			}

		}

		this.evidence = prevEvidence;

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

		try {
			/*
			 * if(hasher != null) hasher.flush();
			 */
			if (fileLister != null)
				fileLister.flush();

		} catch (IOException e) {
		}

		if (evidence == null)
			System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " finalizada.");
		else
			System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " interrompida com " + evidence.getPath() + " (" + evidence.getLength() + "bytes)");
	}

}
