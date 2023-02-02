package iped.parsers.evtx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.evtx.model.EvtxFile;
import iped.parsers.evtx.model.EvtxRecord;
import iped.parsers.evtx.model.EvtxRecordConsumer;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;

/*
 * Parser that extract event records   
 */
public class EvtxRecordParser extends AbstractParser {
	private static Logger LOGGER = LoggerFactory.getLogger(EvtxRecordParser.class);
    
	private static final long serialVersionUID = 9091294620647570196L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-elf-file")); //$NON-NLS-1$

    public static final MediaType EVTX_MIME_TYPE = MediaType.application("x-elf-file"); //$NON-NLS-1$
    public static final String EVTX_RECORD_MIME_TYPE = "application/x-elf-record"; //$NON-NLS-1$
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        TemporaryResources tmp = new TemporaryResources();

        String filePath = ""; //$NON-NLS-1$
        ItemInfo itemInfo = context.get(ItemInfo.class);
        if (itemInfo != null)
            filePath = itemInfo.getPath();

        final TikaInputStream tis = TikaInputStream.get(stream, tmp);

    	if (extractor.shouldParseEmbedded(metadata)) {
            EvtxFile evtxFile = new EvtxFile(tis);
            EvtxRecordConsumer co = new EvtxRecordConsumer() {
        		@Override
        		public void accept(EvtxRecord evtxRecord) {
                    String name = Long.toString(evtxRecord.getId());
                    String eventId = evtxRecord.getEventId();
                    
                	Metadata recordMetadata = new Metadata();
                    recordMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, "text/plain");
                    recordMetadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                    recordMetadata.set(TikaCoreProperties.TITLE, "EventRecordId "+name);//eventtype
                    String date = evtxRecord.getEventDateTime();
                    recordMetadata.add("WinEvtID:"+eventId, date);

                    StringBuffer content = new StringBuffer();
                    content.append(evtxRecord.getBinXml().toString());
                    
                    HashMap<String, String> datas = evtxRecord.getEventData();
	                if(datas!=null && datas.size()>0) {
	                	try {
	                        for (Iterator<Entry<String,String>> iterator = datas.entrySet().iterator(); iterator.hasNext();) {
	                        	Entry<String,String> data =  iterator.next();
	            				recordMetadata.add(data.getKey(), data.getValue());
	            			}
	                	}catch (Exception e) {
	                		e.printStackTrace();
						}
	                }

                    if (extractor.shouldParseEmbedded(recordMetadata)) {
                        try {
                            ByteArrayInputStream chatStream = new ByteArrayInputStream(content.toString().getBytes());
                            extractor.parseEmbedded(chatStream, handler, recordMetadata, false);
                        }catch(Exception e) {
                        	e.printStackTrace();
                        }
                    }
        		}
        	};
    		evtxFile.setEvtxRecordConsumer(co);
            evtxFile.processFile();
            
        }

    }
}
