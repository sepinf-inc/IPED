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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.txt.TXTParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.parsers.external.ExternalParser;
import dpf.sp.gpinf.indexer.parsers.util.BasicProps;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.Item;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.ItemSearcher;
import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.process.ItemSearcherImpl;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.StreamSource;
import gpinf.dev.data.EvidenceFile;

/**
 * TAREFA DE PARSING DE ALGUNS TIPOS DE ARQUIVOS. ARMAZENA O TEXTO EXTRAÍDO, CASO PEQUENO, PARA
 * REUTILIZAR DURANTE INDEXAÇÃO, ASSIM O ARQUIVO NÃO É DECODIFICADO NOVAMENTE. O PARSING É EXECUTADO
 * EM OUTRA THREAD, SENDO POSSÍVEL MONITORAR E RECUPERAR DE HANGS, ETC.
 *
 * É REALIZADO O PARSING NOS SEGUINTES CASOS: - ITENS DO TIPO CONTAINER, PARA EXTRAÇÃO DE SUBITENS.
 * - ITENS DE CARVING PARA IGNORAR CORROMPIDOS, CASO A INDEXAÇÃO ESTEJA DESABILITADA. - CATEGORIAS
 * QUE POSSAM CONTER ITENS CIFRADOS, ASSIM PODEM SER ADICIONADOS A CATEGORIA ESPECÍFICA.
 *
 * O PARSING DOS DEMAIS ITENS É REALIADO DURANTE A INDEXAÇÃO, ASSIM ITENS GRANDES NÃO TEM SEU TEXTO
 * EXTRAÍDO ARMAZENADO EM MEMÓRIA, O QUE PODERIA CAUSAR OOM.
 */
public class ParsingTask extends AbstractTask implements EmbeddedDocumentExtractor {

  private static Logger LOGGER = LoggerFactory.getLogger(ParsingTask.class);

  public static final String EXPAND_CONFIG = "CategoriesToExpand.txt"; //$NON-NLS-1$
  public static final String ENABLE_PARSING = "enableFileParsing"; //$NON-NLS-1$
  public static final String ENCRYPTED = "encrypted"; //$NON-NLS-1$
  public static final String HAS_SUBITEM = "hasSubitem"; //$NON-NLS-1$

  public static boolean expandContainers = false;
  private static boolean enableFileParsing = true;

  // Utilizado para restringir tamanho mÃ¡ximo do nome de subitens de zips corrompidos
  private static int NAME_MAX_LEN = 256;

  public static int subitensDiscovered = 0;
  private static HashSet<String> categoriesToExpand = new HashSet<String>();
  public static AtomicLong totalText = new AtomicLong();
  public static Map<String, AtomicLong> times = Collections.synchronizedMap(new TreeMap<String, AtomicLong>());

  private EvidenceFile evidence;
  private ParseContext context;
  private boolean extractEmbedded;
  private ParsingEmbeddedDocumentExtractor embeddedParser;
  private volatile ParsingReader reader;
  private boolean hasTitle = false;
  private String firstParentPath = null;
  
  private IndexerDefaultParser autoParser;

  public ParsingTask(ParseContext context) {
    setContext(context);
  }
  
  public ParsingTask(ParseContext context, EvidenceFile evidence) {
      setContext(context);
      this.evidence = evidence;
    }
  
  @Override
  public boolean isEnabled() {
    return enableFileParsing;
  }

  public ParsingTask() {
    this.autoParser = new IndexerDefaultParser();
    this.autoParser.setFallback(Configuration.fallBackParser);
    this.autoParser.setErrorParser(Configuration.errorParser);
  }
  
  public ParsingTask(Worker worker, IndexerDefaultParser parser) {
      this.setWorker(worker);
	  this.autoParser = parser;
  }

  private void setContext(ParseContext context) {
    this.context = context;
    this.embeddedParser = new ParsingEmbeddedDocumentExtractor(context);
    ItemInfo appContext = context.get(ItemInfo.class);
    extractEmbedded = isToBeExpanded(appContext.getBookmarks());
  }

  private void configureTikaContext(EvidenceFile evidence) {
    // DEFINE CONTEXTO: PARSING RECURSIVO, ETC
    context = new ParseContext();
    context.set(Parser.class, this.autoParser);
    ItemInfo itemInfo = ItemInfoFactory.getItemInfo(evidence);
    context.set(ItemInfo.class, itemInfo);
    context.set(EmbeddedDocumentExtractor.class, this);
    context.set(StreamSource.class, evidence);
    if (CarveTask.ignoreCorrupted && !caseData.isIpedReport()) {
      context.set(IgnoreCorruptedCarved.class, new IgnoreCorruptedCarved());
    }

    // Tratamento p/ acentos de subitens de ZIP
    ArchiveStreamFactory factory = new ArchiveStreamFactory();
    factory.setEntryEncoding("Cp850"); //$NON-NLS-1$
    context.set(ArchiveStreamFactory.class, factory);
    
    // Indexa conteudo de todos os elementos de HTMLs, como script, etc
    context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
    
    context.set(OCROutputFolder.class, new OCROutputFolder(output));
    context.set(Item.class, evidence);
    context.set(ItemSearcher.class, new ItemSearcherImpl(output.getParentFile(), worker.writer));

    setContext(context);
  }

  private void fillMetadata(EvidenceFile evidence) {
	  fillMetadata(evidence, evidence.getMetadata());
  }
  
  public static void fillMetadata(EvidenceFile evidence, Metadata metadata){
	Long len = evidence.getLength();
	if (len != null)
	    metadata.set(Metadata.CONTENT_LENGTH, len.toString());
	metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
	if(evidence.getMediaType() != null) {
	    metadata.set(Metadata.CONTENT_TYPE, evidence.getMediaType().toString());
	    metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, evidence.getMediaType().toString());
	}
	if (evidence.isTimedOut()) {
	  metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true"); //$NON-NLS-1$
	}
  }

  public static void load(File file) throws FileNotFoundException, IOException {
    categoriesToExpand = new HashSet<String>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")); //$NON-NLS-1$
    String line = reader.readLine();
    while ((line = reader.readLine()) != null) {
      if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
        continue;
      }
      categoriesToExpand.add(line.trim());
    }
    reader.close();
  }

  private static boolean isToBeExpanded(HashSet<String> categories) {

    if (!expandContainers) {
      return false;
    }

    boolean result = false;
    for (String category : categories) {
      if (categoriesToExpand.contains(category)) {
        result = true;
        break;
      }
    }
    return result;
  }

  private static synchronized void incSubitensDiscovered() {
    subitensDiscovered++;
  }

  public static int getSubitensDiscovered() {
    return subitensDiscovered;
  }
  
  public void process(EvidenceFile evidence) throws IOException {

    if (!enableFileParsing) {
      return;
    }

    fillMetadata(evidence);
    
    Parser parser = getLeafParser(autoParser, evidence);
    
    AtomicLong time = times.get(getParserName(parser));
    if(time == null){
    	time = new AtomicLong();
    	times.put(getParserName(parser), time);
    }
    long start = System.nanoTime()/1000;
    
    if (evidence.getParsedTextCache() == null && !evidence.isTimedOut() && ((evidence.getLength() == null || 
    		evidence.getLength() < Configuration.minItemSizeToFragment) ||
    		isSpecificParser(parser) )) {
        try{
            new ParsingTask(worker, autoParser).safeProcess(evidence);
            
        }finally{
            time.addAndGet(System.nanoTime()/1000 - start);
        }
      
    }
    
  }
  
  private String getParserName(Parser parser) {
      if(parser instanceof ExternalParser)
          return ((ExternalParser)parser).getParserName();
      else
          return parser.getClass().getSimpleName();
  }
  
  private static Parser getLeafParser(IndexerDefaultParser autoParser, EvidenceFile evidence) {
	  Parser parser = autoParser.getBestParser(evidence.getMetadata());
	    while(parser instanceof CompositeParser || parser instanceof ParserDecorator){
	    	if(parser instanceof CompositeParser)
	    		parser = getParser((CompositeParser)parser, evidence.getMetadata());
	    	else
	    		parser = ((ParserDecorator)parser).getWrappedParser();
	    }
	    return parser;

  }
  
  public static boolean hasSpecificParser(IndexerDefaultParser autoParser, EvidenceFile evidence) {
	  Parser p = getLeafParser(autoParser, evidence);
	  return isSpecificParser(p);
  }
  
  private static boolean isSpecificParser(Parser parser) {
    if (parser instanceof RawStringParser || parser instanceof TXTParser)
      return false;
    else
      return true;
  }
  
  private static Parser getParser(CompositeParser comp, Metadata metadata) {
      Map<MediaType, Parser> map = comp.getParsers();
      MediaType type = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
      if (type != null)
         type = comp.getMediaTypeRegistry().normalize(type);
      while (type != null) {
          Parser parser = map.get(type);
          if (parser != null)
              return parser;
          type = comp.getMediaTypeRegistry().getSupertype(type);
      }
      return comp.getFallback();
  }

  private void safeProcess(EvidenceFile evidence) throws IOException {

    this.evidence = evidence;

    TikaInputStream tis = null;
    try {
      tis = evidence.getTikaStream();

    } catch (IOException e) {
      LOGGER.warn("{} Error opening: {} {}", Thread.currentThread().getName(), evidence.getPath(), e.toString()); //$NON-NLS-1$
      return;
    }

    configureTikaContext(evidence);
    Metadata metadata = evidence.getMetadata();
    
    reader = new ParsingReader(this.autoParser, tis, metadata, context);
    reader.startBackgroundParsing();

    try {
      StringWriter writer;
      char[] cbuf = new char[128 * 1024];
      int len = 0;
      int numFrags = 0;
      
      /*ContentHandler handler = new ToTextContentHandler(writer);
      try {
        numFrags++;
		this.autoParser.parse(tis, handler, metadata, context);
	  } catch (Throwable e) {
		//e.printStackTrace();
	  }
      /*/
      do {
        numFrags++;
        writer = new StringWriter();
        if(numFrags > 1 && metadata.get(IndexerDefaultParser.PARSER_EXCEPTION) != null)
        	break;
        while ((len = reader.read(cbuf)) != -1 && !Thread.currentThread().isInterrupted()) {
          writer.write(cbuf, 0, len);
        }

      } while (reader.nextFragment());
	
      /**
       * Armazena o texto extraído em cache até o limite de 1 fragmento, 
       * o que totaliza ~100MB com o tamanho padrão de fragmento
       */
      if (numFrags == 1) {
        evidence.setParsedTextCache(writer.toString());
        totalText.addAndGet(evidence.getParsedTextCache().length());
      }

      if (extractEmbedded) {
        evidence.setParsed(true);
      }

    } finally {
      //IOUtil.closeQuietly(tis);
      //do nothing
      reader.close();
      reader.reallyClose();
      
      metadataToExtraAttribute(evidence);
    }
    
  }
  
  private static final void metadataToExtraAttribute(EvidenceFile evidence){
      //Ajusta metadados:
      Metadata metadata = evidence.getMetadata();
      if (metadata.get(IndexerDefaultParser.ENCRYPTED_DOCUMENT) != null) {
        evidence.setExtraAttribute(ParsingTask.ENCRYPTED, "true"); //$NON-NLS-1$
      }
      metadata.remove(IndexerDefaultParser.ENCRYPTED_DOCUMENT);

      String value = metadata.get(OCRParser.OCR_CHAR_COUNT);
      if (value != null) {
        int charCount = Integer.parseInt(value);
        evidence.setExtraAttribute(OCRParser.OCR_CHAR_COUNT, charCount);
        metadata.remove(OCRParser.OCR_CHAR_COUNT);
        if (charCount >= 100 && evidence.getMediaType().getType().equals("image")) { //$NON-NLS-1$
          evidence.setCategory(SetCategoryTask.SCANNED_CATEGORY);
        }
      }
      
  }
  
  @Override
  public boolean shouldParseEmbedded(Metadata subitemMeta) {
      
    //do not extract images from html generated previews
    if(evidence != null && !MetadataUtil.isHtmlMediaType(evidence.getMediaType()) && MetadataUtil.isHtmlSubType(evidence.getMediaType())) {
        String type = subitemMeta == null ? null : subitemMeta.get(Metadata.CONTENT_TYPE);
        if(type != null && type.startsWith("image")) //$NON-NLS-1$
            return false;
    }
    return true;
  }

  private String getName(Metadata metadata, int child) {
    hasTitle = false;
    String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
    if (name == null || name.isEmpty()) {
      name = metadata.get(ExtraProperties.MESSAGE_SUBJECT);
      if (name == null || name.isEmpty()) {
        name = metadata.get(TikaCoreProperties.TITLE);
      }
      if (name != null) {
        hasTitle = true;
      }
    }
    if (name == null || name.isEmpty()) {
      name = metadata.get(TikaMetadataKeys.EMBEDDED_RELATIONSHIP_ID);
    }

    if (name == null || name.isEmpty()) {
      name = Messages.getString("ParsingTask.UnNamed") + child; //$NON-NLS-1$
    }

    if (name.length() > NAME_MAX_LEN) {
      name = name.substring(0, NAME_MAX_LEN);
    }

    if (!hasTitle) {
      int i = name.lastIndexOf('/');
      if (i != -1) {
        name = name.substring(i + 1);
      }
    }
    return name;
  }

  @Override
  public void parseEmbedded(InputStream inputStream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

    if (!this.shouldParseEmbedded(metadata)) {
      return;
    }

    TemporaryResources tmp = new TemporaryResources();
    String subitemPath = null;
    try {
      ItemInfo itemInfo = context.get(ItemInfo.class);
      itemInfo.incChild();

      String name = getName(metadata, itemInfo.getChild());
      String parentPath = itemInfo.getPath();
      if (firstParentPath == null) {
        firstParentPath = parentPath;
      }

      EvidenceFile parent = evidence;
      if (context.get(EmbeddedParent.class) != null) {
        parent = (EvidenceFile) context.get(EmbeddedParent.class).getObj();
        parentPath = parent.getPath();
        subitemPath = parentPath + "/" + name; //$NON-NLS-1$
      } else {
        subitemPath = parentPath + ">>" + name; //$NON-NLS-1$
      }

      EvidenceFile subItem = new EvidenceFile();
      subItem.setPath(subitemPath);
      context.set(EmbeddedItem.class, new EmbeddedItem(subItem));

      String embeddedPath = subitemPath.replace(firstParentPath + ">>", ""); //$NON-NLS-1$ //$NON-NLS-2$
      char[] nameChars = (embeddedPath + "\n\n").toCharArray(); //$NON-NLS-1$
      handler.characters(nameChars, 0, nameChars.length);

      if (extractEmbedded && output == null) {
        return;
      }

      if (!extractEmbedded) {
        itemInfo.setPath(subitemPath);
        try {
          embeddedParser.parseEmbedded(inputStream, handler, metadata, false);
        } finally {
          itemInfo.setPath(parentPath);
        }
        return;
      }

      subItem.setMetadata(metadata);

      String contentTypeStr = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
      if (contentTypeStr != null) {
        subItem.setMediaType(MediaType.parse(contentTypeStr));
      }

      subItem.setName(name);
      if (hasTitle) {
        subItem.setExtension(""); //$NON-NLS-1$
      }

      subItem.setParent(parent);
      
      //sometimes do not work, because parent may be already processed and 
      //stored in database/index so setting it later has no effect
      parent.setHasChildren(true);
      
      //parsers should set this property to let created items be displayed in file tree
      if (metadata.get(BasicProps.HASCHILD) != null) {
          metadata.remove(BasicProps.HASCHILD);
          subItem.setHasChildren(true);
      }
      
      if (metadata.get(OutlookPSTParser.HAS_ATTACHS) != null)
          subItem.setHasChildren(true);
      
      //indica se o conteiner tem subitens (mais específico que filhos genéricos)
      evidence.setExtraAttribute(HAS_SUBITEM, "true"); //$NON-NLS-1$

      if (metadata.get(ExtraProperties.EMBEDDED_FOLDER) != null) {
          metadata.remove(ExtraProperties.EMBEDDED_FOLDER);
          subItem.setIsDir(true);
      }

      subItem.setCreationDate(metadata.getDate(TikaCoreProperties.CREATED));
      subItem.setModificationDate(metadata.getDate(TikaCoreProperties.MODIFIED));
      subItem.setAccessDate(metadata.getDate(ExtraProperties.ACCESSED));
      
      removeMetadataAndDuplicates(metadata, TikaCoreProperties.CREATED);
      removeMetadataAndDuplicates(metadata, TikaCoreProperties.MODIFIED);
      removeMetadataAndDuplicates(metadata, ExtraProperties.ACCESSED);
      
      subItem.setDeleted(parent.isDeleted());
      if (metadata.get(ExtraProperties.DELETED) != null) {
          metadata.remove(ExtraProperties.DELETED);
          subItem.setDeleted(true);
      }

      //causa problema de subitens corrompidos de zips carveados serem apagados, mesmo sendo referenciados por outros subitens
      //subItem.setCarved(parent.isCarved());
      subItem.setSubItem(true);
      subItem.setSumVolume(false);
      
      ExportFileTask extractor = new ExportFileTask();
      extractor.setWorker(worker);
      extractor.extractFile(inputStream, subItem, evidence.getLength());

      // pausa contagem de timeout do pai antes de extrair e processar subitem
      if (reader.setTimeoutPaused(true)) {
        try {
          worker.processNewItem(subItem);
          incSubitensDiscovered();

        } finally {
          //despausa contador de timeout do pai somente após processar subitem
          reader.setTimeoutPaused(false);
        }
      }

    } catch (SAXException e) {
      // TODO Provavelmente PipedReader foi interrompido, interrompemos
      // aqui tb, deve ser melhorado...
      if (e.toString().contains("Error writing")) { //$NON-NLS-1$
        Thread.currentThread().interrupt();
      }

      //e.printStackTrace();
      LOGGER.warn("{} SAX error while extracting subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, e.toString()); //$NON-NLS-1$

    } catch (Exception e) {
      LOGGER.warn("{} Error while extracting subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, e.toString()); //$NON-NLS-1$
      //e.printStackTrace();

    } finally {
      tmp.close();
    }

  }
  
  private static void removeMetadataAndDuplicates(Metadata metadata, Property prop){
      metadata.remove(prop.getName());
      Property[] props = prop.getSecondaryExtractProperties();
      if(props != null)
          for(Property p : props)
              metadata.remove(p.getName());
  }

  @Override
  public void init(Properties confProps, File confDir) throws Exception {

    load(new File(confDir, EXPAND_CONFIG));

    String value = confProps.getProperty("expandContainers"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      expandContainers = Boolean.valueOf(value);
    }

    value = confProps.getProperty(ENABLE_PARSING);
    if (value != null & !value.trim().isEmpty()) {
      enableFileParsing = Boolean.valueOf(value.trim());
    }

    subitensDiscovered = 0;

  }

  @Override
  public void finish() throws Exception {
      if(totalText != null)
          LOGGER.info("Total extracted text size: " + totalText.get()); //$NON-NLS-1$
      totalText = null;

  }

}
