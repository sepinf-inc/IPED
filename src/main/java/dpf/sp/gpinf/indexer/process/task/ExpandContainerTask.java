/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
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
package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.tika.detect.Detector;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.io.FastPipedReader;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IndexerContext;

/*
 * EXTRAÃ‡Ã‚O DE SUBITENS DE CONTAINERS USANDO PARSINGREADER PARA MONITORAR TIMEOUTS
 * ARMAZENA O TEXTO EXTRAÃ�DO, CASO PEQUENO, PARA REUTILIZAR DURANTE INDEXAÃ‡ÃƒO,
 * ASSIM O ARQUIVO NÃƒO Ã‰ DECODIFICADO NOVAMENTE.
 * 
 * TAMBÃ‰M REALIZA O PARSING DE ITENS DE CARVING PARA IGNORAR CORROMPIDOS, CASO
 * A INDEXAÃ‡ÃƒO (QUE TB FAZ O PARSING) ESTEJA DESABILITADA.
 */
public class ExpandContainerTask extends AbstractTask implements EmbeddedDocumentExtractor {

	public static String COMPLETE_PATH = "INDEXER_COMPLETE_PATH";
	//public static String INDEXER_ID = "INDEXER_ID";
	public static String TO_EXTRACT = "Indexer-To-Extract";
	public static String EXPAND_CONFIG = "CategoriesToExpand.txt";
	public static boolean expandContainers = false;
	
	// Utilizado para restringir tamanho mÃ¡ximo do nome de subitens de zips corrompidos
	private static int NAME_MAX_LEN = 1024;

	public static int subitensDiscovered = 0;
	private static HashSet<String> categoriesToExpand = new HashSet<String>();

	private File outputBase;
	private CaseData caseData;
	private Detector detector;
	
	private ParseContext context;
	private boolean extractEmbedded;
	private ParsingEmbeddedDocumentExtractor embeddedParser;

	public ExpandContainerTask(ParseContext context) {
		setContext(context);
	}

	public ExpandContainerTask(Worker worker) {
		this.worker = worker;
		this.outputBase = worker.output;
		this.caseData = worker.caseData;
		this.detector = worker.detector;
	}
	
	private void setContext(ParseContext context){
		this.context = context;
		this.embeddedParser = new ParsingEmbeddedDocumentExtractor(context);
		IndexerContext appContext = context.get(IndexerContext.class);
		extractEmbedded = isToBeExpanded(appContext.getBookmarks());
	}
	
	private void configureTikaContext(EvidenceFile evidence) {
		// DEFINE CONTEXTO: PARSING RECURSIVO, ETC
		context = new ParseContext();
		context.set(Parser.class, worker.autoParser);
		IndexerContext indexerContext = new IndexerContext(evidence);
		context.set(IndexerContext.class, indexerContext);
		context.set(EmbeddedDocumentExtractor.class, this);
		context.set(EvidenceFile.class, evidence);

		// Tratamento p/ acentos de subitens de ZIP
		ArchiveStreamFactory factory = new ArchiveStreamFactory();
		factory.setEntryEncoding("Cp850");
		context.set(ArchiveStreamFactory.class, factory);
					
		/*PDFParserConfig config = new PDFParserConfig();
		config.setExtractInlineImages(true);
		context.set(PDFParserConfig.class, config);
		*/
		
		setContext(context);
	}
	
	private Metadata getMetadata(EvidenceFile evidence) {
		Metadata metadata = new Metadata();
		Long len = evidence.getLength();
		if(len == null)
			len = 0L;
		metadata.set(Metadata.CONTENT_LENGTH, len.toString());
		metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
		metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, evidence.getMediaType().toString());
		if (evidence.timeOut)
			metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true");
		
		return metadata;
	}

	public static void load(File file) throws FileNotFoundException, IOException {
		categoriesToExpand = new HashSet<String>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "windows-1252"));
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.trim().startsWith("#") || line.trim().isEmpty())
				continue;
			categoriesToExpand.add(line.trim());
		}
		reader.close();
	}

	private static boolean isToBeExpanded(HashSet<String> categories) {
		
		if(!expandContainers)
			return false;
		
		boolean result = false;
		for (String category : categories) {
			if (categoriesToExpand.contains(category)) {
				result = true;
				break;
			}
		}
		return result;
	}

	public static boolean isToBeExpanded(EvidenceFile evidence) {
		return isToBeExpanded(evidence.getCategorySet());
	}

	private static synchronized void incSubitensDiscovered() {
		subitensDiscovered++;
	}

	public static int getSubitensDiscovered() {
		return subitensDiscovered;
	}
	
	public void process(EvidenceFile evidence) throws IOException{
		if (!((isToBeExpanded(evidence) && !evidence.timeOut) ||
			(CarveTask.ignoreCorrupted && evidence.isCarved() &&
			(ExportFileTask.hasCategoryToExtract() || !IndexTask.indexFileContents) )))
				return;
		
		new ExpandContainerTask(worker).safeProcess(evidence);
	}
	
	
	private void safeProcess(EvidenceFile evidence) throws IOException{	
		
		TikaInputStream tis = null;
		try {
			tis = evidence.getTikaStream();
			
		} catch (IOException e) {
			System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Erro ao abrir: " + evidence.getPath() + " " + e.toString());
			return;
		}
		
		configureTikaContext(evidence);
		
		Metadata metadata = getMetadata(evidence);
		
		ParsingReader reader = new ParsingReader(worker.autoParser, tis, metadata, context);
		try{
			StringWriter writer;
			char[] cbuf = new char[128*1024];
			int len = 0;
			int numFrags = 0;
			do {
				numFrags++;
				writer = new StringWriter();
				while ((len = reader.read(cbuf)) != -1 && !Thread.currentThread().isInterrupted())
					writer.write(cbuf, 0, len);

			} while (reader.nextFragment());
			
			if (numFrags == 1){
				evidence.setParsedTextCache(writer.toString());
			}

		}finally{
			reader.close2();
		}
		
		evidence.setParsed(true);
		
	}
	
	@Override
	public boolean shouldParseEmbedded(Metadata arg0) {
		return true;
	}

	@Override
	public void parseEmbedded(InputStream inputStream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

		if(!this.shouldParseEmbedded(metadata))
			return;
		
		TemporaryResources tmp = new TemporaryResources();
		String filePath = null;
		try {
			IndexerContext appContext = context.get(IndexerContext.class);
			appContext.incChild();

			String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
			boolean hasTitle = false;
			if (name == null || name.isEmpty()) {
				name = metadata.get(TikaCoreProperties.TITLE);
				if(name != null){
					//Workaround para permitir listagem recursiva por caminho, devido a emails com mesmo assunto
					//name += " [Msg" + appContext.getChild() + "]";
					hasTitle = true;
				}
			}
			if (name == null || name.isEmpty())
				name = metadata.get(TikaMetadataKeys.EMBEDDED_RELATIONSHIP_ID);
			
			if (name == null || name.isEmpty())
				name = "[Sem Nome]-" + appContext.getChild();
			
			if(name.length() > NAME_MAX_LEN)
				name = name.substring(0, NAME_MAX_LEN);
			

			filePath = metadata.get(COMPLETE_PATH);
			String parentPath = appContext.getPath();
			if (filePath == null) {
				filePath = parentPath + ">>" + name;
				int i = name.lastIndexOf('/');
				if (i != -1)
					name = name.substring(i + 1);
			} else
				filePath += ">>" + name;

			char[] nameChars = (name + "\n\n").toCharArray();
			handler.characters(nameChars, 0, nameChars.length);
			
			incSubitensDiscovered();
			
			if (extractEmbedded && outputBase == null)
				return;

			if (!extractEmbedded) {
				appContext.setPath(filePath);
				embeddedParser.parseEmbedded(inputStream, handler, metadata, false);
				appContext.setPath(parentPath);
				return;
			}
			
			
			MediaType contentType;
			String contentTypeStr = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
			TikaInputStream tis = TikaInputStream.get(inputStream, tmp);
			if(contentTypeStr == null)
				try {
					if(SignatureTask.processFileSignatures)
						contentType = detector.detect(tis, metadata).getBaseType();
					else
						contentType = detector.detect(null, metadata).getBaseType();
					
				} catch (Exception e) {
					contentType = MediaType.OCTET_STREAM;
				}
			else
				contentType = MediaType.parse(contentTypeStr);

			EvidenceFile evidence = new EvidenceFile();
			evidence.setName(name);
			if (hasTitle)
				evidence.setExtension("");
			evidence.setPath(filePath);
			evidence.setMediaType(contentType);
			
			String parentId = String.valueOf(appContext.getEvidence().getId());
			evidence.setParentId(parentId);
			evidence.addParentIds(appContext.getEvidence().getParentIds());
			evidence.addParentId(parentId);
			appContext.getEvidence().setHasChildren(true);
			
			Date created = metadata.getDate(TikaCoreProperties.CREATED);
			Date modified = metadata.getDate(TikaCoreProperties.MODIFIED);
			evidence.setCreationDate(created);
			evidence.setModificationDate(modified);
			evidence.setDeleted(appContext.getEvidence().isDeleted());
			evidence.setCarved(appContext.getEvidence().isCarved());
			evidence.setSubItem(true);
			
			if(metadata.get(OutlookPSTParser.HAS_ATTACHS) != null && OutlookPSTParser.OUTLOOK_MSG_MIME.equals(contentTypeStr)){
				evidence.setHasChildren(true);
				appContext.setEvidence(evidence);
			}

			ExportFileTask extractor = new ExportFileTask(worker);
			extractor.extractFile(tis, evidence);

			new SetCategoryTask(worker).process(evidence);

			// teste para extraÃ§Ã£o de anexos de emails de PSTs
			if (!ExportFileTask.hasCategoryToExtract() || ExportFileTask.isToBeExtracted(evidence) || metadata.get(TO_EXTRACT) != null) {
				evidence.setToExtract(true);
				metadata.set(TO_EXTRACT, "true");
				//int id = evidence.getId();
				//metadata.set(EmbeddedFileParser.INDEXER_ID, Integer.toString(id));
			}
			
			caseData.incDiscoveredEvidences(1);
			//caseData.incDiscoveredVolume(evidence.getLength());

			// se nÃ£o hÃ¡ item na fila, enfileira para outro worker processar
			if (caseData.getEvidenceFiles().size() == 0)
				caseData.getEvidenceFiles().addFirst(evidence);

			// caso contrÃ¡rio processa o item no worker atual
			else {
				// pausa contagem de timeout enquanto processa subitem
				FastPipedReader pipedReader = context.get(FastPipedReader.class);
				if(pipedReader != null)
					pipedReader.setTimeoutPaused(true);
				worker.process(evidence);
				if(pipedReader != null)
					pipedReader.setTimeoutPaused(false);
			}

		} catch (SAXException e) {
			// TODO Provavelmente PipedReader foi interrompido, interrompemos
			// aqui tb, deve ser melhorado...
			if (e.toString().contains("Error writing"))
				Thread.currentThread().interrupt();

			//e.printStackTrace();
			System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Erro ao extrair subitem " + filePath + "\t\t" + e.toString());

		} catch (Exception e) {
			System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Erro ao extrair Subitem " + filePath + "\t\t" + e.toString());
			//e.printStackTrace();

		} finally {
			tmp.close();
		}

	}

	@Override
	public void init(Properties confProps, File confDir) throws Exception {
		
		load(new File(confDir, EXPAND_CONFIG));
		
		String value = confProps.getProperty("expandContainers");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			expandContainers = Boolean.valueOf(value);
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

}
