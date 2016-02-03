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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.StreamSource;
import gpinf.dev.data.EvidenceFile;

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

	private static Logger LOGGER = LoggerFactory.getLogger(ParsingTask.class);
	public static String EXPAND_CONFIG = "CategoriesToExpand.txt";
	public static boolean expandContainers = false;
	
	// Utilizado para restringir tamanho mÃ¡ximo do nome de subitens de zips corrompidos
	private static int NAME_MAX_LEN = 256;

	public static int subitensDiscovered = 0;
	private static HashSet<String> categoriesToExpand = new HashSet<String>();
	private static HashSet<String> categoriesToTestEncryption = getCategoriesToTestEncryption();

	private Detector detector;
	
	private EvidenceFile evidence;
	private ParseContext context;
	private boolean extractEmbedded;
	private ParsingEmbeddedDocumentExtractor embeddedParser;
	private volatile ParsingReader reader;
	private boolean hasTitle = false;
	private String firstParentPath = null;

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
	
	//TODO: Externalizar para arquivo de configuração
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
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String line = reader.readLine();
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
			|| checkScanned(evidence.getMediaType())
			|| (CarveTask.ignoreCorrupted && evidence.isCarved() && (ExportFileTask.hasCategoryToExtract() || !IndexTask.indexFileContents) ))){
		    new ParsingTask(worker).safeProcess(evidence);
		}
		
	}
	
	private boolean checkScanned(MediaType mediaType){
	    return OCRParser.getSupportedTypes().contains(mediaType);
	}
	
	
	private void safeProcess(EvidenceFile evidence) throws IOException{	
		
		this.evidence = evidence;
		
		TikaInputStream tis = null;
		try {
			tis = evidence.getTikaStream();
			
		} catch (IOException e) {
			LOGGER.warn("{} Erro ao abrir: {} {}", Thread.currentThread().getName(), evidence.getPath(), e.toString());
			return;
		}
		
		configureTikaContext(evidence);
		Metadata metadata = getMetadata(evidence);
		
		reader = new ParsingReader(worker.autoParser, tis, metadata, context);
		reader.startBackgroundParsing();
		
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
			
			if (numFrags == 1)
				evidence.setParsedTextCache(writer.toString());
			
			if(extractEmbedded)
				evidence.setParsed(true);
			
			if(metadata.get("EncryptedDocument") != null)
				evidence.setExtraAttribute("encrypted", "true");
			
			String value = metadata.get("OCRCharCount");
			if(value != null && evidence.getMediaType().getType().equals("image")){
			    int charCount = Integer.parseInt(value.replace("OCRCharCount", ""));
			    evidence.setExtraAttribute("OCRCharCount", charCount);
			    if(charCount >= 100)
			        evidence.addCategory(SetCategoryTask.SCANNED_CATEGORY);
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
	
	private String getName(Metadata metadata, int child){
		hasTitle = false;
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
		
		if(!hasTitle){
			int i = name.lastIndexOf('/');
			if (i != -1)
				name = name.substring(i + 1);
		}
		return name;
	}
	

	@Override
	public void parseEmbedded(InputStream inputStream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

		if(!this.shouldParseEmbedded(metadata))
			return;
		
		TemporaryResources tmp = new TemporaryResources();
		String subitemPath = null;
		try {
			incSubitensDiscovered();
			
			ItemInfo itemInfo = context.get(ItemInfo.class);
			itemInfo.incChild();
			
			String name = getName(metadata, itemInfo.getChild());
			String parentPath = itemInfo.getPath();
			if(firstParentPath == null) firstParentPath = parentPath;
			
			EvidenceFile parent = evidence;
			if(context.get(EmbeddedParent.class) != null){
			    parent = (EvidenceFile)context.get(EmbeddedParent.class).getObj();
			    parentPath = parent.getPath();
			    subitemPath = parentPath + "/" + name;
			}else
				subitemPath = parentPath + ">>" + name;

			EvidenceFile subItem = new EvidenceFile();
			subItem.setPath(subitemPath);
			context.set(EmbeddedItem.class, new EmbeddedItem(subItem));
			
			String embeddedPath = subitemPath.replace(firstParentPath + ">>", "");
			char[] nameChars = (embeddedPath + "\n\n").toCharArray();
			handler.characters(nameChars, 0, nameChars.length);
			
			if (extractEmbedded && output == null)
				return;

			if (!extractEmbedded) {
				itemInfo.setPath(subitemPath);
				try{
					embeddedParser.parseEmbedded(inputStream, handler, metadata, false);
				}finally{
					itemInfo.setPath(parentPath);
				}
				return;
			}
			
			String contentTypeStr = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
			if(contentTypeStr != null)
				subItem.setMediaType(MediaType.parse(contentTypeStr));

			subItem.setName(name);
			if (hasTitle)
				subItem.setExtension("");
			
			String parentId = String.valueOf(parent.getId());
			subItem.setParentId(parentId);
			subItem.addParentIds(parent.getParentIds());
			subItem.addParentId(parent.getId());
			parent.setHasChildren(true);
			
			if(metadata.get(ExtraProperties.EMBEDDED_FOLDER) != null)
			    subItem.setIsDir(true);
			
			subItem.setCreationDate(metadata.getDate(TikaCoreProperties.CREATED));
			subItem.setModificationDate(metadata.getDate(TikaCoreProperties.MODIFIED));
			subItem.setAccessDate(metadata.getDate(ExtraProperties.ACCESSED));
			subItem.setDeleted(parent.isDeleted());
			if(metadata.get(ExtraProperties.DELETED) != null)
				subItem.setDeleted(true);
			
			//causa problema de subitens corrompidos de zips carveados serem apagados, mesmo sendo referenciados por outros subitens
			//subItem.setCarved(parent.isCarved());
			subItem.setSubItem(true);

			// pausa contagem de timeout do pai antes de extrair e processar subitem
			if(reader.setTimeoutPaused(true))
				try{
					ExportFileTask extractor = new ExportFileTask(worker);
					extractor.extractFile(inputStream, subItem);
					
					// Extração de anexos de emails de PSTs, se emails forem extraídos
					/*if(contentTypeStr != null)
						new SetCategoryTask(worker).process(subItem);
					if (!ExportFileTask.hasCategoryToExtract() || ExportFileTask.isToBeExtracted(subItem) || metadata.get(ExtraProperties.TO_EXTRACT) != null) {
						subItem.setToExtract(true);
						metadata.set(ExtraProperties.TO_EXTRACT, "true");
					}
					*/
					worker.processNewItem(subItem);
	
				}finally{
					//despausa contador de timeout do pai somente após processar subitem
					reader.setTimeoutPaused(false);
				}

		} catch (SAXException e) {
			// TODO Provavelmente PipedReader foi interrompido, interrompemos
			// aqui tb, deve ser melhorado...
			if (e.toString().contains("Error writing"))
				Thread.currentThread().interrupt();

			//e.printStackTrace();
			LOGGER.warn("{} Erro ao extrair subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, e.toString());

		} catch (Exception e) {
			LOGGER.warn("{} Erro ao extrair Subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, e.toString());
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
