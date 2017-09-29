package dpf.sp.gpinf.indexer.process.task;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import gpinf.dev.data.EvidenceFile;

public class NamedEntityTask extends AbstractTask {
    
    public static final String NER_PREFIX = NamedEntityParser.MD_KEY_PREFIX;
    
    public static final String ENABLE_PARAM = "enableNamedEntityRecogniton";
    
    private static final String CONF_FILE = "NamedEntityRecognitionConfig.txt";
    
    private static final int MAX_TEXT_LEN = 100000;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NamedEntityTask.class);
    
    private static volatile boolean isEnabled = false;
    
    private static AtomicBoolean inited = new AtomicBoolean();
    
    private static Map<String, NamedEntityParser> nerParserPerLang = new HashMap<String, NamedEntityParser>();
    
    private static Set<String> mimeTypesToIgnore = new HashSet<String>();
    
    private static Set<String> categoriesToIgnore = new HashSet<String>();
    
    private static float minLangScore = 0;

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
        
        File confFile = new File(confDir, CONF_FILE);
        UTF8Properties props = new UTF8Properties();
        props.load(confFile);
        
        String nerImpl = props.getProperty("NERImpl");
        if(nerImpl.contains("CoreNLPNERecogniser"))
            try{
                Class.forName("edu.stanford.nlp.ie.crf.CRFClassifier");
                
            }catch(ClassNotFoundException e){
                isEnabled = false;
                LOGGER.error("StanfordCoreNLP not found. Did you put the jar in the optional lib folder?");
                return;
            }
        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, nerImpl);
        
        String langAndModel;
        int i = 0;
        while((langAndModel = props.getProperty("langModel_" + i++)) != null){
            String[] strs = langAndModel.split(":");
            String lang = strs[0].trim();
            String modelPath = strs[1].trim();
            
            URL resource = this.getClass().getResource("/" + modelPath);
            if(resource == null){
                isEnabled = false;
                LOGGER.error(modelPath + " not found. Did you put the model in the optional lib folder?");
                return;
            }
            
            System.setProperty(CoreNLPNERecogniser.MODEL_PROP_NAME, modelPath);
            NamedEntityParser nerParser = new NamedEntityParser();
            //first call to initialize
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
            nerParser.parse(new EmptyInputStream(), new IgnoreContentHandler(), metadata, new ParseContext());
            nerParserPerLang.put(lang, nerParser);
            System.out.println(lang + ":" + nerParser);
        }
        
        String mimes = props.getProperty("mimeTypesToIgnore");
        for(String mime : mimes.split(";"))
            mimeTypesToIgnore.add(mime.trim());
        
        String categories = props.getProperty("categoriesToIgnore");
        for(String cat : categories.split(";"))
            categoriesToIgnore.add(cat.trim());
        
        minLangScore = Float.valueOf(props.getProperty("minLangScore").trim());
        
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
        
        if(text == null)
            return;
        
        for(String ignore : mimeTypesToIgnore)
            if(mime.startsWith(ignore))
                return;
        
        for(String ignore : categoriesToIgnore)
            if(categories.contains(ignore))
                return;
        
        int i = text.lastIndexOf(IndexerDefaultParser.METADATA_HEADER);
        if(i != -1)
            text = text.substring(0, i);
        
        NamedEntityParser nerParser = null;
        Float langScore = (Float)evidence.getExtraAttribute("language:detected_score_1");
        String lang = (String)evidence.getExtraAttribute("language:detected_1");
        if(langScore != null && langScore >= minLangScore)
            nerParser = nerParserPerLang.get(lang);
        if(nerParser == null){
            langScore = (Float)evidence.getExtraAttribute("language:detected_score_2");
            lang = (String)evidence.getExtraAttribute("language:detected_2");
            if(langScore != null && langScore >= minLangScore)
                nerParser = nerParserPerLang.get(lang);
        }
        if(nerParser == null)
            nerParser = nerParserPerLang.get("default");
        
        Metadata metadata = evidence.getMetadata();
        String originalContentType = metadata.get(Metadata.CONTENT_TYPE);
        
        for(int off = 0; off < text.length(); off += MAX_TEXT_LEN){
            int end = off + MAX_TEXT_LEN;
            end = end <= text.length() ? end : text.length();
            metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
            String textFrag = text.substring(off, end);
            try(InputStream is = new ByteArrayInputStream(textFrag.getBytes(StandardCharsets.UTF_8))){
                
                nerParser.parse(is, new IgnoreContentHandler(), metadata, new ParseContext());
                
            }finally{
                metadata.set(Metadata.CONTENT_TYPE, originalContentType);
            }
        }
        
        
        
    }

}
