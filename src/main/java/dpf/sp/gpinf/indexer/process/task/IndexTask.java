package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
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

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreCorruptedCarved;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.ItemSearcherImpl;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.CloseFilterReader;
import dpf.sp.gpinf.indexer.util.FragmentingReader;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.ItemImpl;
import iped3.Item;
import iped3.io.ItemBase;
import iped3.io.StreamSource;
import iped3.search.ItemSearcher;
import iped3.sleuthkit.SleuthKitItem;

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
  private static String TEXT_SIZES = IndexTask.class.getSimpleName() + "TEXT_SIZES"; //$NON-NLS-1$

  public static boolean indexFileContents = true;
  public static boolean indexUnallocated = false;

  public static final String extraAttrFilename = "extraAttributes.dat"; //$NON-NLS-1$

  private IndexerDefaultParser autoParser;
  private List<IdLenPair> textSizes;

  public IndexTask() {
    this.autoParser = new IndexerDefaultParser();
  }

  public static class IdLenPair {

    int id;
    long length;

    public IdLenPair(int id, long len) {
      this.id = id;
      this.length = len;
    }
  }

  public void process(Item evidence) throws IOException {
    if (evidence.isQueueEnd()) {
      return;
    }

    Reader textReader = null;

    if (!evidence.isToAddToCase()) {
      if (evidence.isDir() || evidence.isRoot() || evidence.hasChildren() || caseData.isIpedReport()) {
        textReader = new StringReader(""); //$NON-NLS-1$
        if(evidence instanceof SleuthKitItem) {
            ((SleuthKitItem)evidence).setSleuthId(null);
        }
        evidence.setExportedFile(null);
        evidence.setExtraAttribute(IndexItem.TREENODE, "true"); //$NON-NLS-1$
        evidence.getCategorySet().clear();
      } else {
        return;
      }
    }

    stats.updateLastId(evidence.getId());

    //Fragmenta itens grandes indexados via strings
    AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance().findObjects(AdvancedIPEDConfig.class).iterator().next();
    if (evidence.getLength() != null && evidence.getLength() >= advancedConfig.getMinItemSizeToFragment() && !caseData.isIpedReport()
            && (!ParsingTask.hasSpecificParser(autoParser, evidence) || evidence.isTimedOut())
    		&& ( ((evidence instanceof SleuthKitItem) && ((SleuthKitItem)evidence).getSleuthFile() != null) || evidence.getFile() != null || evidence.getInputStreamFactory() != null)){
    	
    	int fragNum = 0;
    	int overlap = 1024;
    	long fragSize = 10 * 1024 * 1024;
    	for (long offset = 0; offset < evidence.getLength(); offset += fragSize - overlap) {
            long len = offset + fragSize < evidence.getLength() ? fragSize : evidence.getLength() - offset;
            this.addFragmentFile(evidence, offset, len, fragNum++);
            if(Thread.currentThread().isInterrupted())
            	return;
        }
    	textReader = new StringReader(""); //$NON-NLS-1$
    }

    if(textReader == null) {
      if (indexFileContents && (indexUnallocated || !UNALLOCATED_MIMETYPE.equals(evidence.getMediaType()))) {
        textReader = evidence.getTextReader();
        if(textReader == null) {
            try {
                TikaInputStream tis = evidence.getTikaStream();
                Metadata metadata = getMetadata(evidence);
                final ParseContext context = getTikaContext(evidence);
                textReader = new ParsingReader(this.autoParser, tis, metadata, context) {
                  @Override
                  public void close() throws IOException {
                    IOUtil.closeQuietly(context.get(ItemSearcher.class));
                    super.close();
                  }
                };
                ((ParsingReader)textReader).startBackgroundParsing();
                
            } catch (IOException e) {
                LOGGER.warn("{} Error opening: {} {}", Thread.currentThread().getName(), evidence.getPath(), e.toString()); //$NON-NLS-1$
            }
        }
      }
    }
    
      if(textReader == null)
          textReader = new StringReader(""); //$NON-NLS-1$
    
      FragmentingReader fragReader = new FragmentingReader(textReader);
      CloseFilterReader noCloseReader = new CloseFilterReader(fragReader);
    
      Document doc = IndexItem.Document(evidence, noCloseReader, output);
      int fragments = 0;
      try {
        /* Indexa os arquivos dividindo-os em fragmentos, pois a lib de
         * indexação consome mta memória com documentos grandes
         */
        do {
          if (++fragments > 1) {
            stats.incSplits();
            LOGGER.debug("{} Splitting text of {}", Thread.currentThread().getName(), evidence.getPath()); //$NON-NLS-1$
          }

          worker.writer.addDocument(doc);

        } while (!Thread.currentThread().isInterrupted() && fragReader.nextFragment());

      }catch(IOException e){
    	  if(IOUtil.isDiskFull(e))
    		  throw new IPEDException("Not enough space for the index on " + worker.manager.getIndexTemp().getAbsolutePath()); //$NON-NLS-1$
    	  else
    	  	  throw e;
      }finally {
        noCloseReader.reallyClose();
        //comentado pois provoca problema de concorrência com temporaryResources
        //Já é fechado na thread de parsing do parsingReader
        //IOUtil.closeQuietly(tis);
      }

      textSizes.add(new IdLenPair(evidence.getId(), fragReader.getTotalTextSize()));

  }

  private Metadata getMetadata(Item evidence) {
	//new metadata to prevent ConcurrentModificationException while indexing
    Metadata metadata = new Metadata();
    ParsingTask.fillMetadata(evidence, metadata);
    return metadata;
  }

  private ParseContext getTikaContext(Item evidence) {
    ParsingTask pt = new ParsingTask(evidence, this.autoParser);
    pt.setWorker(worker);
    ParseContext context = pt.getTikaContext();
    //this is to not create new items while indexing
    pt.setExtractEmbedded(false);
    return context;
  }

  @Override
  public void init(Properties properties, File confDir) throws Exception {

    String value = properties.getProperty("indexFileContents"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      indexFileContents = Boolean.valueOf(value);
    }

    value = properties.getProperty("indexUnallocated"); //$NON-NLS-1$
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

      File prevFile = new File(output, "data/texts.size"); //$NON-NLS-1$
      if (prevFile.exists()) {
        FileInputStream fileIn = new FileInputStream(prevFile);
        ObjectInputStream in = new ObjectInputStream(fileIn);

        long[] textSizesArray;
        Object array = (long[]) in.readObject();
        if(array instanceof long[])
            textSizesArray = (long[])array;
        else {
            int i = 0;
            textSizesArray = new long[((int[])array).length];
            for(int size : (int[])array)
                textSizesArray[i++] = size * 1000L;
        }
        for (int i = 0; i < textSizesArray.length; i++) {
          if (textSizesArray[i] != 0) {
            textSizes.add(new IdLenPair(i, textSizesArray[i]));
          }
        }

        in.close();
        fileIn.close();

        stats.setLastId(textSizesArray.length - 1);
        ItemImpl.setStartID(textSizesArray.length);
      }
    }

    if(IndexItem.getMetadataTypes().size() == 0){
    	IndexItem.loadMetadataTypes(confDir);
    	IndexItem.loadMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
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

      IndexItem.saveMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
    }
    caseData.putCaseObject(TEXT_SIZES, null);

  }

  private void saveExtraAttributes() throws IOException {
    File extraAttributtesFile = new File(output, "data/" + extraAttrFilename); //$NON-NLS-1$
    Set<String> extraAttr = ItemImpl.getAllExtraAttributes();
    Util.writeObject(extraAttr, extraAttributtesFile.getAbsolutePath());
  }

  private void loadExtraAttributes() throws ClassNotFoundException, IOException {

    File extraAttributtesFile = new File(output, "data/" + extraAttrFilename); //$NON-NLS-1$
    if (extraAttributtesFile.exists()) {
    	Set<String> extraAttributes = (Set<String>)Util.readObject(extraAttributtesFile.getAbsolutePath());
    	ItemImpl.getAllExtraAttributes().addAll(extraAttributes);
    }
  }

  private void salvarTamanhoTextosExtraidos() throws Exception {
    IndexFiles.getInstance().firePropertyChange("mensagem", "", "Saving extracted text sizes..."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    LOGGER.info("Saving extracted text sizes..."); //$NON-NLS-1$

    long[] textSizesArray = new long[stats.getLastId() + 1];

    for (int i = 0; i < textSizes.size(); i++) {
      IdLenPair pair = textSizes.get(i);
      textSizesArray[pair.id] = pair.length;
    }

    Util.writeObject(textSizesArray, output.getAbsolutePath() + "/data/texts.size"); //$NON-NLS-1$
  }

}
