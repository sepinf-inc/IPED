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

import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.StreamSource;

/**
 * TAREFA DE PARSING DE ALGUNS TIPOS DE ARQUIVOS. ARMAZENA O TEXTO EXTRAÍDO, CASO PEQUENO, 
 * PARA REUTILIZAR DURANTE INDEXAÇÃO, ASSIM O ARQUIVO NÃO É DECODIFICADO NOVAMENTE. O PARSING
 * É EXECUTADO EM OUTRA THREAD, SENDO POSSÍVEL MONITORAR E RECUPERAR DE HANGS, ETC.
 * 
 * É REALIZADO O PARSING NOS SEGUINTES CASOS:
 * - ITENS DO TIPO CONTAINER, PARA EXTRAÇÃO DE SUBITENS. 
 * - ITENS DE CARVING PARA IGNORAR CORROMPIDOS, CASO A INDEXAÇÃO ESTEJA DESABILITADA.
 * - CATEGORIAS QUE POSSAM CONTER ITENS CIFRADOS, ASSIM PODEM SER ADICIONADOS A CATEGORIA ESPECÍFICA.
 * 
 * O PARSING DOS DEMAIS ITENS É REALIADO DURANTE A INDEXAÇÃO, ASSIM ITENS GRANDES
 * NÃO TEM SEU TEXTO EXTRAÍDO ARMAZENADO EM MEMÓRIA, O QUE PODERIA CAUSAR OOM.
 */
public class ParsingTask extends AbstractTask implements EmbeddedDocumentExtractor {

	public static String EXPAND_CONFIG = "CategoriesToExpand.txt";
	public static boolean expandContainers = false;
	
	// Utilizado para restringir tamanho mÃ¡ximo do nome de subitens de zips corrompidos
	private static int NAME_MAX_LEN = 1024;

	public static int subitensDiscovered = 0;
	private static HashSet<String> categoriesToExpand = new HashSet<String>();
	private static HashSet<String> categoriesToTestEncryption = getCategoriesToTestEncryption();

	private Detector detector;
	
	private EvidenceFile evidence;
	private ParseContext context;
	private boolean extractEmbedded;
	private ParsingEmbeddedDocumentExtractor embeddedParser;
	private ParsingReader reader;

	public ParsingTask(ParseContext context) {
		super(null);
		setContext(context);
	}

	public ParsingTask(Worker worker) {
		super(worker);
		this.detector = worker.detector;
	}
	
	private void setContext(ParseContext context){
		this.context = context;
		this.embeddedParser = new ParsingEmbeddedDocumentExtractor(context);
		ItemInfo appContext = context.get(ItemInfo.class);
		extractEmbedded = isToBeExpanded(appContext.getBookmarks());
	}
	
	private void configureTikaContext(EvidenceFile evidence) {
		// DEFINE CONTEXTO: PARSING RECURSIVO, ETC
		context = new ParseContext();
		context.set(Parser.class, worker.autoParser);
		ItemInfo itemInfo = ItemInfoFactory.getItemInfo(evidence);
		context.set(ItemInfo.class, itemInfo);
		context.set(EmbeddedDocumentExtractor.class, this);
		context.set(StreamSource.class, evidence);
		if(CarveTask.ignoreCorrupted)
			context.set(IgnoreCorruptedCarved.class, new IgnoreCorruptedCarved());

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
		if (evidence.isTimedOut())
			metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true");
		
		return metadata;
	}
	
	private static HashSet<String> getCategoriesToTestEncryption(){
		HashSet<String> set = new HashSet<String>();
		set.add("Arquivos Compactados");
		set.add("Documentos PDF");
		set.add("Documentos de Texto");
		set.add("Planilhas");
		set.add("Apresentações");
		set.add("Outros Documentos");
		return set;
	}
	
	private boolean isToTestEncryption(HashSet<String> categories) {
		
		boolean result = false;
		for (String category : categories) {
			if (categoriesToTestEncryption.contains(category)) {
				result = true;
				break;
			}
		}
		return result;
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
		if (!evidence.isTimedOut() && (isToBeExpanded(evidence)
			|| isToTestEncryption(evidence.getCategorySet()) 
			|| (CarveTask.ignoreCorrupted && evidence.isCarved() && (ExportFileTask.hasCategoryToExtract() || !IndexTask.indexFileContents) ))){
		    new ParsingTask(worker).safeProcess(evidence);
		}
		
	}
	
	
	private void safeProcess(EvidenceFile evidence) throws IOException{	
		
		this.evidence = evidence;
		
		TikaInputStream tis = null;
		try {
			tis = evidence.getTikaStream();
			
		} catch (IOException e) {
			System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Erro ao abrir: " + evidence.getPath() + " " + e.toString());
			return;
		}
		
		configureTikaContext(evidence);
		Metadata metadata = getMetadata(evidence);
		
		reader = new ParsingReader(worker.autoParser, tis, metadata, context);
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
			evidence.setParsed(true);
			if(metadata.get("EncryptedDocument") != null || evidence.isEncrypted()){
				evidence.setEncrypted(true);
				evidence.addCategory(SetCategoryTask.ENCRYPTED_CATEGORY);
			}

		}finally{
			//do nothing
			reader.close();
			reader.reallyClose();
		}
		
	}
	
	@Override
	public boolean shouldParseEmbedded(Metadata arg0) {
		return true;
	}
	
	private String getName(Metadata metadata, int child, Boolean hasTitle){
		String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
		if (name == null || name.isEmpty()) {
			name = metadata.get(TikaCoreProperties.TITLE);
			if(name != null)
				hasTitle = true;
		}
		if (name == null || name.isEmpty())
			name = metadata.get(TikaMetadataKeys.EMBEDDED_RELATIONSHIP_ID);
		
		if (name == null || name.isEmpty())
			name = "[Sem Nome]-" + child;
		
		if(name.length() > NAME_MAX_LEN)
			name = name.substring(0, NAME_MAX_LEN);
		
		return name;
	}

	@Override
	public void parseEmbedded(InputStream inputStream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

		if(!this.shouldParseEmbedded(metadata))
			return;
		
		TemporaryResources tmp = new TemporaryResources();
		String filePath = null;
		try {
			ItemInfo itemInfo = context.get(ItemInfo.class);
			itemInfo.incChild();

			Boolean hasTitle = false;
			String name = getName(metadata, itemInfo.getChild(), hasTitle);

			filePath = metadata.get(ExtraProperties.EMBEDDED_PATH);
			String parentPath = itemInfo.getPath();
			if (filePath == null)
				filePath = parentPath + ">>" + name;
			else
				filePath = parentPath + ">>" + filePath + ">>" + name;
			
			int i = name.lastIndexOf('/');
			if (i != -1)
				name = name.substring(i + 1);

			char[] nameChars = (name + "\n\n").toCharArray();
			handler.characters(nameChars, 0, nameChars.length);
			
			incSubitensDiscovered();
			
			if (extractEmbedded && output == null)
				return;

			if (!extractEmbedded) {
				itemInfo.setPath(filePath);
				embeddedParser.parseEmbedded(inputStream, handler, metadata, false);
				itemInfo.setPath(parentPath);
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

			EvidenceFile subItem = new EvidenceFile();
			subItem.setName(name);
			if (hasTitle)
				subItem.setExtension("");
			subItem.setPath(filePath);
			subItem.setMediaType(contentType);
			
			EvidenceFile parent = evidence;
			if(itemInfo.getEvidence() != null)
				parent = (EvidenceFile)itemInfo.getEvidence();
			
			String parentId = String.valueOf(parent.getId());
			subItem.setParentId(parentId);
			subItem.addParentIds(parent.getParentIds());
			subItem.addParentId(parent.getId());
			parent.setHasChildren(true);
			
			Date created = metadata.getDate(TikaCoreProperties.CREATED);
			Date modified = metadata.getDate(TikaCoreProperties.MODIFIED);
			subItem.setCreationDate(created);
			subItem.setModificationDate(modified);
			subItem.setDeleted(parent.isDeleted());
			subItem.setCarved(parent.isCarved());
			subItem.setSubItem(true);
			
			if(metadata.get(OutlookPSTParser.HAS_ATTACHS) != null && OutlookPSTParser.OUTLOOK_MSG_MIME.equals(contentTypeStr)){
				//subItem.setHasChildren(true);
				itemInfo.setEvidence(subItem);
			}

			ExportFileTask extractor = new ExportFileTask(worker);
			extractor.extractFile(tis, subItem);

			new SetCategoryTask(worker).process(subItem);

			// teste para extraÃ§Ã£o de anexos de emails de PSTs
			if (!ExportFileTask.hasCategoryToExtract() || ExportFileTask.isToBeExtracted(subItem) || metadata.get(ExtraProperties.TO_EXTRACT) != null) {
				subItem.setToExtract(true);
				metadata.set(ExtraProperties.TO_EXTRACT, "true");
				//int id = evidence.getId();
				//metadata.set(EmbeddedFileParser.INDEXER_ID, Integer.toString(id));
			}

			// pausa contagem de timeout enquanto processa subitem
			reader.setTimeoutPaused(true);
			worker.processNewItem(subItem);
			reader.setTimeoutPaused(false);

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
		
		subitensDiscovered = 0;
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

}
