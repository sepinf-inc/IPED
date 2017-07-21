package dpf.sp.gpinf.indexer.process.task;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ner.NamedEntityParser;
import org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreContentHandler;
import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

public class NamedEntityTask extends AbstractTask {
    
    public static final String NER_PREFIX = NamedEntityParser.MD_KEY_PREFIX;
    
    private static volatile NamedEntityParser nep;

    public NamedEntityTask(Worker worker) {
        super(worker);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        if(nep != null)
            return;
        
        String nerImpl = "org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser";
        //String modelPath = "edu/stanford/nlp/models/ner/spanish.ancora.distsim.s512.crf.ser.gz";
        String modelPath = "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz";
        
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
        metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
        
        try(InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))){
            
            nep.parse(is, new IgnoreContentHandler(), metadata, new ParseContext());
            
        }finally{
            metadata.set(Metadata.CONTENT_TYPE, originalContentType);
        }
        
    }

}
