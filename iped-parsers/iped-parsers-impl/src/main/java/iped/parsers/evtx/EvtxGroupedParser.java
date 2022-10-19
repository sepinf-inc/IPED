package iped.parsers.evtx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.evtx.model.EvtxElement;
import iped.parsers.evtx.model.EvtxFile;
import iped.parsers.evtx.model.EvtxParseExeption;
import iped.parsers.evtx.model.EvtxRecord;
import iped.parsers.evtx.model.EvtxRecordConsumer;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.properties.ExtraProperties;
import iped.utils.EmptyInputStream;

/*
 * Parser that extract event records grouped by EventID  
 */
public class EvtxGroupedParser extends AbstractParser {
	private static Logger LOGGER = LoggerFactory.getLogger(EvtxGroupedParser.class);
    
	private static final long serialVersionUID = 9091294620647570196L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-elf-file")); //$NON-NLS-1$

    public static final String EVTX_RECORD_MIME_TYPE = "application/x-elf-record"; //$NON-NLS-1$

	private static final String EVTX_METADATA_PREFIX = "WinEvtx";

	private static final Property RECCOUNT_PROP = Property.internalInteger(EVTX_METADATA_PREFIX+":recordCount");
	private static final Property RECID_PROP = Property.internalIntegerSequence(EVTX_METADATA_PREFIX+":eventRecordID");
	static String timeCreated = EVTX_METADATA_PREFIX+":timeCreated";
    
	private String[] groupBy;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }
    
    @Field
    public void setGroupBy(String value) {
    	if(value.trim()!="") {
    		value=";"+value;
    	}
        groupBy = ("Event/System/Provider@Name"+value).split(";");//always groups by Provider@Name
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
            evtxFile.setName(filePath);

            TreeMap<String, ArrayList<EvtxRecord>> subItens = new TreeMap<String, ArrayList<EvtxRecord>>();

            EvtxRecordConsumer co = new EvtxRecordConsumer() {
    			@Override
    			public void accept(EvtxRecord evtxRecord) {
    				try {
        				String groupValue = "";
        				if(groupBy!=null) {
            				for(int i=0; i < groupBy.length; i++) {
            					if(groupBy[i].contains("@")) {
            						String[] terms = groupBy[i].split("@");
            						EvtxElement el = evtxRecord.getElement(terms[0]);
            						groupValue += groupBy[i]+":"+el.getAttributeByName(terms[1]);
            					}else {
                					groupValue += groupBy[i]+":"+evtxRecord.getElementValue(groupBy[i]);
            					}
            					if(i < groupBy.length-1) {
            						groupValue += ";";
            					}
            				}
        				}

                        ArrayList<EvtxRecord> recs = subItens.get(groupValue);
                        if(recs==null) {
                        	recs=new ArrayList<EvtxRecord>();
                        	subItens.put(groupValue, recs);
                        }
                        recs.add(evtxRecord);
    				}catch (Exception e) {
						e.printStackTrace();
					}
    			}
    		};

    		evtxFile.setEvtxRecordConsumer(co);
            evtxFile.processFile();

            try {
            	int totalRecordCount=0;
            	String lastProvider = "";
            	int providerVid = 0;
                for (Iterator<String> iterator = subItens.keySet().iterator(); iterator.hasNext();) {
                	String subKey = (String) iterator.next();

                	ArrayList<EvtxRecord> recs = subItens.get(subKey);
                	String currentProvider = subKey.substring(0,subKey.indexOf(";"));
                	if(!currentProvider.equals(lastProvider)) {
                		lastProvider=currentProvider;
                		Metadata providerMetadata = new Metadata();
                		providerMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, EVTX_RECORD_MIME_TYPE);
                		providerMetadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                        providerMetadata.set(ExtraProperties.EMBEDDED_FOLDER, "true");
                        providerMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(-1));                        
                        providerMetadata.set(TikaCoreProperties.TITLE, currentProvider.substring(currentProvider.lastIndexOf(":")+1));//eventtype
                        providerVid++;
                        providerMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(providerVid));
                        if(currentProvider.contains("BTHUSB")) {
                        	System.out.print(1);
                        }
                        extractor.parseEmbedded(new EmptyInputStream(), handler, providerMetadata, false);
                	}

                	Metadata recordMetadata = new Metadata();
                    recordMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, EVTX_RECORD_MIME_TYPE);
                    recordMetadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                    recordMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(providerVid));
                    String name = subKey;
                    recordMetadata.set(TikaCoreProperties.TITLE, name.substring(currentProvider.length()+1));//eventtype

                    StringBuffer content = new StringBuffer();
                    int groupRecordCount=0;
                    for (Iterator iterator2 = recs.iterator(); iterator2.hasNext();) {
    					EvtxRecord evtxRecord = (EvtxRecord) iterator2.next();
    					String recContent = evtxRecord.getBinXml().toString();
    	                String date = evtxRecord.getEventDateTime();

    	                recordMetadata.add("WinEvt:"+ evtxRecord.getEventProviderName()+":" + evtxRecord.getEventId(), date);
    	                recordMetadata.add(RECID_PROP, (int) evtxRecord.getEventRecordId());
    	                content.append(recContent);

    	                HashMap<String, String> datas = evtxRecord.getEventData();
    	                if(datas!=null && datas.size()>0) {
    	                	try {
            	                for(Iterator<Entry<String,String>> iterator3 = datas.entrySet().iterator(); iterator3.hasNext();) {
            	                	Entry<String,String> data =  iterator3.next();
            						recordMetadata.add(EVTX_METADATA_PREFIX+":"+data.getKey(), data.getValue());
            					}
    	                	}catch(Exception e) {
    	                		//logs an error but continue
    	                    	System.out.println("Evtx File Parser error:"+filePath);
    	                		e.printStackTrace();
    	                	}
    	                }
    	                groupRecordCount++;
    				}

                    recordMetadata.set(RECCOUNT_PROP, groupRecordCount);
                    totalRecordCount+=groupRecordCount;

                    if (extractor.shouldParseEmbedded(recordMetadata)) {
                        try {
                            ByteArrayInputStream chatStream = new ByteArrayInputStream(content.toString().getBytes());
                            extractor.parseEmbedded(chatStream, handler, recordMetadata, false);
                        }catch(Exception e) {
                        	e.printStackTrace();
                        }
                    }
    			}

                metadata.set(RECCOUNT_PROP, totalRecordCount);
            }catch (Exception e) {
            	System.out.println("Evtx File Parser error:"+filePath);
				e.printStackTrace();
			}
        }
    }
}
