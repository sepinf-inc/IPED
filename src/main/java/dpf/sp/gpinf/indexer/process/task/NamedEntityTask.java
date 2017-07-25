package dpf.sp.gpinf.indexer.process.task;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ner.NamedEntityParser;
import org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreContentHandler;
import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

public class NamedEntityTask extends AbstractTask {
    
    public static final String NER_PREFIX = NamedEntityParser.MD_KEY_PREFIX;
    
    public static final String ENABLE_PARAM = "enableNamedEntityRecogniton";
    
    private static final int MAX_TEXT_LEN = 100000;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NamedEntityTask.class);
    
    private static volatile NamedEntityParser nep;
    
    private static volatile boolean isEnabled = false;
    
    private static AtomicBoolean inited = new AtomicBoolean();

    public NamedEntityTask(Worker worker) {
        super(worker);
    }
    
    @Override
    public boolean isEnabled(){
        return isEnabled;
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        if(inited.getAndSet(true))
            return;
        
        String param = confParams.getProperty(ENABLE_PARAM);
        if(param != null && !param.trim().isEmpty())
            isEnabled = Boolean.valueOf(param.trim());
        
        if(!isEnabled)
            return;
        
        try{
            Class.forName("edu.stanford.nlp.ie.crf.CRFClassifier");
            
        }catch(ClassNotFoundException e){
            isEnabled = false;
            LOGGER.error("StanfordCoreNLP not found. Did you put the jar in the optional lib folder?");
            return;
        }
        
        
        String nerImpl = "org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser";
        //String modelPath = "edu/stanford/nlp/models/ner/spanish.ancora.distsim.s512.crf.ser.gz";
        String modelPath = "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz";
        
        URL resource = this.getClass().getResource("/" + modelPath);
        if(resource == null){
            isEnabled = false;
            LOGGER.error(modelPath + " not found. Did you download and put the model on classpath?");
            return;
        }
        
        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, nerImpl);
        System.setProperty(CoreNLPNERecogniser.MODEL_PROP_NAME, modelPath);
        
        nep = new NamedEntityParser();
        
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        
        if(!isEnabled)
            return;
        
        String text = evidence.getParsedTextCache();
        String mime = evidence.getMediaType().toString();
        String categories = evidence.getCategories(); 
        
        if(text == null || mime.startsWith("audio") || mime.startsWith("video")
                || categories.contains("Programas e Bibliotecas") || categories.contains("Outros Arquivos") || categories.contains("Discos Virtuais") 
                || MediaType.OCTET_STREAM.toString().equals(mime) || CarveTask.UNALLOCATED_MIMETYPE.toString().equals(mime))
            return;
        
        int i = text.lastIndexOf(IndexerDefaultParser.METADATA_HEADER);
        if(i != -1)
            text = text.substring(0, i);
        
        Metadata metadata = evidence.getMetadata();
        String originalContentType = metadata.get(Metadata.CONTENT_TYPE);
        
        for(int off = 0; off < text.length(); off += MAX_TEXT_LEN){
            int end = off + MAX_TEXT_LEN;
            end = end <= text.length() ? end : text.length();
            metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
            String textFrag = text.substring(off, end);
            try(InputStream is = new ByteArrayInputStream(textFrag.getBytes(StandardCharsets.UTF_8))){
                
                nep.parse(is, new IgnoreContentHandler(), metadata, new ParseContext());
                
            }finally{
                metadata.set(Metadata.CONTENT_TYPE, originalContentType);
            }
        }
        
        
        
    }

}
