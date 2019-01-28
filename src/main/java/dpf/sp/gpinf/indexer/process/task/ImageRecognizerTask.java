package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.apache.tika.dl.imagerec.DL4JInceptionV3Net;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.apache.tika.sax.ToTextContentHandler;
import org.datavec.image.loader.NativeImageLoader;

import gpinf.dev.data.EvidenceFile;

public class ImageRecognizerTask extends AbstractTask { 
    
    private static final HashSet<String> formats = new HashSet<String>();

    private static DL4JInceptionV3Net recogniser;

    private double minConfidence = 0.05;

    private int topN = 3;

    private static final Comparator<RecognisedObject> DESC_CONFIDENCE_SORTER = new Comparator<RecognisedObject>() {
        @Override
        public int compare(RecognisedObject o1, RecognisedObject o2) {
            return Double.compare(o2.getConfidence(), o1.getConfidence());
        }
    };

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        if(recogniser != null)
            return;
        
        recogniser = new DL4JInceptionV3Net();
        recogniser.initialize(Collections.EMPTY_MAP);
        
        formats.addAll(Arrays.asList(NativeImageLoader.ALLOWED_FORMATS));
        
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {

        String subtype = evidence.getMediaType().getSubtype();
        if (!formats.contains(subtype))
            return;

        try {

            List<RecognisedObject> objects = recogniser.recognise(evidence.getTikaStream(), new ToTextContentHandler(), evidence.getMetadata(), new ParseContext());
            // LOG.debug("Found {} objects", objects != null ? objects.size() : 0);
            // LOG.debug("Time taken {}ms", System.currentTimeMillis() - start);
            if (objects != null && !objects.isEmpty()) {

                Collections.sort(objects, DESC_CONFIDENCE_SORTER);
                int count = 0;

                // first process all the MD objects
                for (RecognisedObject object : objects) {
                    if (object.getConfidence() >= minConfidence) {
                        count++;
                        // LOG.debug("Add {}", object);
                        evidence.setExtraAttribute("IMAGE_OBJECT_" + count , object.getLabel());
                        evidence.setExtraAttribute("IMAGE_OBJECT_" + count + "_CONFIDENCE" , object.getConfidence());
                        if (count >= topN)
                            break;
                    } else {
                        // LOG.warn("Object {} confidence {} less than min {}", object, object.getConfidence(), minConfidence);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
