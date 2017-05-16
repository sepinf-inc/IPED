package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.lucene.document.Document;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.ItemSearcher;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.ItemSearcherImpl;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.StreamSource;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.EvidenceFile;

/**
 * Tarefa de indexação dos itens. Indexa apenas as propriedades, caso a indexação do conteúdo esteja
 * desabilitada. Reaproveita o texto dos itens caso tenha sido extraído por tarefas anteriores.
 *
 * Indexa itens grandes dividindo-os em fragmentos, pois a lib de indexação consome mta memória com
 * documentos grandes.
 *
 */
public class IndexTask extends BaseCarveTask {

  private static Logger LOGGER = LoggerFactory.getLogger(IndexTask.class);
  private static String TEXT_SIZES = IndexTask.class.getSimpleName() + "TEXT_SIZES";

  public static boolean indexFileContents = true;
  public static boolean indexUnallocated = false;

  public static final String extraAttrFilename = "extraAttributes.dat";

  private IndexerDefaultParser autoParser;
  private List<IdLenPair> textSizes;

  public IndexTask(Worker worker) {
    super(worker);
    this.autoParser = new IndexerDefaultParser();
    this.autoParser.setFallback(Configuration.fallBackParser);
    this.autoParser.setErrorParser(Configuration.errorParser);
        
  }

  public static class IdLenPair {

    int id, length;

    public IdLenPair(int id, long len) {
      this.id = id;
      this.length = (int) (len / 1000);
    }

  }

  public void process(EvidenceFile evidence) throws IOException {

    if (evidence.isQueueEnd()) {
      return;
    }

    String textCache = evidence.getParsedTextCache();

    if (!evidence.isToAddToCase()) {
      if (evidence.isDir() || evidence.isRoot() || evidence.hasChildren() || caseData.isIpedReport()) {
        textCache = "";
        evidence.setSleuthId(null);
        evidence.setExportedFile(null);
        evidence.setExtraAttribute(IndexItem.TREENODE, "true");
        evidence.getCategorySet().clear();
      } else {
        return;
      }
    }

    stats.updateLastId(evidence.getId());
    
    //Fragmenta itens grandes indexados via strings
    if (evidence.getLength() >= Configuration.minItemSizeToFragment && !caseData.containsReport()
            && (!ParsingTask.hasSpecificParser(autoParser, evidence) || evidence.isTimedOut())
    		&& (evidence.getSleuthFile() != null || evidence.getFile() != null)){
    	
    	int fragNum = 0;
    	int overlap = 1024;
    	long fragSize = 10 * 1024 * 1024;
    	for (long offset = 0; offset < evidence.getLength(); offset += fragSize - overlap) {
            long len = offset + fragSize < evidence.getLength() ? fragSize : evidence.getLength() - offset;
            this.addFragmentFile(evidence, offset, len, fragNum++);
            if(Thread.currentThread().isInterrupted())
            	return;
        }
    	//if(evidence.getMediaType().equals(CarveTask.UNALLOCATED_MIMETYPE))
    		textCache = "";
    }

    if (textCache != null) {
      Document doc;
      if (indexFileContents) {
        doc = IndexItem.Document(evidence, new StringReader(textCache));
      } else {
        doc = IndexItem.Document(evidence, null);
      }

      try{
    	  worker.writer.addDocument(doc);
    	  
      }catch(IOException e){
    	  if(e.toString().toLowerCase().contains("espaço insuficiente no disco") ||
    	     e.toString().toLowerCase().contains("no space left on device"))
    		  throw new IPEDException("Espaço insuficiente para o indice em " + worker.manager.getIndexTemp().getAbsolutePath());
    	  else
    		  throw e;
      }
      
      textSizes.add(new IdLenPair(evidence.getId(), textCache.length()));

    } else{
      Metadata metadata = getMetadata(evidence);
      ParseContext context = getTikaContext(evidence, evidence.isParsed());

      ParsingReader reader = null;
      if (indexFileContents && (indexUnallocated || !CarveTask.UNALLOCATED_MIMETYPE.equals(evidence.getMediaType()))) {
    	TikaInputStream tis = null;
        try {
            tis = evidence.getTikaStream();
        } catch (IOException e) {
            LOGGER.warn("{} Erro ao abrir: {} {}", Thread.currentThread().getName(), evidence.getPath(), e.toString());
        }
        if(tis != null){
        	reader = new ParsingReader(this.autoParser, tis, metadata, context);
            reader.startBackgroundParsing();
        }
      }

      Document doc = IndexItem.Document(evidence, reader);
      int fragments = 0;
      try {
        /* Indexa os arquivos dividindo-os em fragmentos, pois a lib de
         * indexação consome mta memória com documentos grandes
         */
        do {
          if (++fragments > 1) {
            stats.incSplits();
            LOGGER.debug("{} Dividindo texto de {}", Thread.currentThread().getName(), evidence.getPath());
          }

          worker.writer.addDocument(doc);

        } while (!Thread.currentThread().isInterrupted() && reader != null && reader.nextFragment());

      }catch(IOException e){
    	  if(e.toString().toLowerCase().contains("espaço insuficiente no disco") ||
    	     e.toString().toLowerCase().contains("no space left on device"))
    		  throw new IPEDException("Espaço insuficiente para o indice em " + worker.manager.getIndexTemp().getAbsolutePath());
    	  else
    	  	  throw e;
      }finally {
        if (reader != null) {
          reader.reallyClose();
        }
        //comentado pois provoca problema de concorrência com temporaryResources
        //Já é fechado na thread de parsing do parsingReader
        //IOUtil.closeQuietly(tis);
      }

      if (reader != null) {
        textSizes.add(new IdLenPair(evidence.getId(), reader.getTotalTextSize()));
      } else {
        textSizes.add(new IdLenPair(evidence.getId(), 0));
      }

    }

  }

  private Metadata getMetadata(EvidenceFile evidence) {
	//new metadata to prevent ConcurrentModificationException while indexing
    Metadata metadata = new Metadata();
    ParsingTask.fillMetadata(evidence, metadata);
    return metadata;
  }

  private ParseContext getTikaContext(EvidenceFile evidence, final boolean parsed) {
    // DEFINE CONTEXTO: PARSING RECURSIVO, ETC
    ParseContext context = new ParseContext();
    context.set(Parser.class, this.autoParser);
    ItemInfo itemInfo = ItemInfoFactory.getItemInfo(evidence);
    context.set(ItemInfo.class, itemInfo);
    context.set(StreamSource.class, evidence);
    if (CarveTask.ignoreCorrupted) {
      context.set(IgnoreCorruptedCarved.class, new IgnoreCorruptedCarved());
    }
    context.set(EmbeddedDocumentExtractor.class, new ParsingTask(context) {
      @Override
      public boolean shouldParseEmbedded(Metadata arg0) {
        return !parsed;
      }
    });

    // Tratamento p/ acentos de subitens de ZIP
    ArchiveStreamFactory factory = new ArchiveStreamFactory();
    factory.setEntryEncoding("Cp850");
    context.set(ArchiveStreamFactory.class, factory);
    
    //Indexa conteudo de todos os elementos de HTMLs, como script, etc
    context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
    
    context.set(OCROutputFolder.class, new OCROutputFolder(output));
    
    context.set(ItemSearcher.class, new ItemSearcherImpl(output.getParentFile(), worker.writer));

    return context;
  }

  @Override
  public void init(Properties properties, File confDir) throws Exception {

    String value = properties.getProperty("indexFileContents");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      indexFileContents = Boolean.valueOf(value);
    }

    value = properties.getProperty("indexUnallocated");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      indexUnallocated = Boolean.valueOf(value);
    }

    textSizes = (List<IdLenPair>) caseData.getCaseObject(TEXT_SIZES);
    if (textSizes == null) {
      textSizes = Collections.synchronizedList(new ArrayList<IdLenPair>());
      caseData.putCaseObject(TEXT_SIZES, textSizes);

      File prevFile = new File(output, "data/texts.size");
      if (prevFile.exists()) {
        FileInputStream fileIn = new FileInputStream(prevFile);
        ObjectInputStream in = new ObjectInputStream(fileIn);

        int[] textSizesArray = (int[]) in.readObject();
        for (int i = 0; i < textSizesArray.length; i++) {
          if (textSizesArray[i] != 0) {
            textSizes.add(new IdLenPair(i, textSizesArray[i] * 1000L));
          }
        }

        in.close();
        fileIn.close();

        stats.setLastId(textSizesArray.length - 1);
        EvidenceFile.setStartID(textSizesArray.length);
      }
    }

    if(IndexItem.getMetadataTypes().size() == 0){
    	IndexItem.loadMetadataTypes(confDir);
    	IndexItem.loadMetadataTypes(new File(output, "conf"));
    }
    loadExtraAttributes();

  }

  @SuppressWarnings("unchecked")
  @Override
  public void finish() throws Exception {

    textSizes = (List<IdLenPair>) caseData.getCaseObject(TEXT_SIZES);
    if (textSizes != null) {
      salvarTamanhoTextosExtraidos();

      saveExtraAttributes();

      IndexItem.saveMetadataTypes(new File(output, "conf"));
    }
    caseData.putCaseObject(TEXT_SIZES, null);

  }

  private void saveExtraAttributes() throws IOException {
    File extraAttributtesFile = new File(output, "data/" + extraAttrFilename);
    Set<String> extraAttr = EvidenceFile.getAllExtraAttributes();
    Util.writeObject(extraAttr, extraAttributtesFile.getAbsolutePath());
  }

  private void loadExtraAttributes() throws ClassNotFoundException, IOException {

    File extraAttributtesFile = new File(output, "data/" + extraAttrFilename);
    if (extraAttributtesFile.exists()) {
    	Set<String> extraAttributes = (Set<String>)Util.readObject(extraAttributtesFile.getAbsolutePath());
    	EvidenceFile.getAllExtraAttributes().addAll(extraAttributes);
    }
  }

  private void salvarTamanhoTextosExtraidos() throws Exception {

    IndexFiles.getInstance().firePropertyChange("mensagem", "", "Salvando tamanho dos textos extraídos...");
    LOGGER.info("Salvando tamanho dos textos extraídos...");

    int[] textSizesArray = new int[stats.getLastId() + 1];

    for (int i = 0; i < textSizes.size(); i++) {
      IdLenPair pair = textSizes.get(i);
      textSizesArray[pair.id] = pair.length;
    }

    Util.writeObject(textSizesArray, output.getAbsolutePath() + "/data/texts.size");
  }

}
