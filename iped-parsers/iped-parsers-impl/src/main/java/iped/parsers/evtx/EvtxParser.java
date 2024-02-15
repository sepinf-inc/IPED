package iped.parsers.evtx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
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
import iped.parsers.evtx.model.EvtxParseException;
import iped.parsers.evtx.model.EvtxRecord;
import iped.parsers.evtx.model.EvtxRecordConsumer;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.EmptyInputStream;

/*
 * Parser that extract event records grouped by EventID  
 */
public class EvtxParser extends AbstractParser {
    private static Logger LOGGER = LoggerFactory.getLogger(EvtxParser.class);

    private static final long serialVersionUID = 9091294620647570196L;

    private static final MediaType EVTX_MIME_TYPE = MediaType.application("x-elf-file"); //$NON-NLS-1$
    private static final MediaType EVTX_RECORD_MIME_TYPE = MediaType.application("x-elf-record"); //$NON-NLS-1$

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(EVTX_MIME_TYPE); // $NON-NLS-1$

    public static final String EVTX_METADATA_PREFIX = "WinEvt:";

    private static final Property RECCOUNT_PROP = Property.internalInteger(EVTX_METADATA_PREFIX + "recordCount");
    private static final Property RECID_PROP = Property.internalIntegerSequence(EVTX_METADATA_PREFIX + "eventRecordID");

    protected int maxEventPerItem = 50;
    private String[] groupBy;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setGroupBy(String value) {
        if (value.trim() != "") {
            value = ";" + value;
        }
        groupBy = ("Event/System/Provider@Name" + value).split(";");// always groups by Provider@Name
    }

    @Field
    public void setMaxEventPerItem(Integer value) {
        this.maxEventPerItem = value;
    }

    class ProviderIDMap extends HashMap<String, String> {
    }

    class GroupPageCountMap extends HashMap<String, Integer> {
    }

    class IntRef {
        int val = 0;
    }

    class EvtxRecordGroupExtractor {
        String subKey;
        ArrayList<EvtxRecord> recs;
        ParseContext context;
        private ContentHandler handler;
        IntRef maxProviderId;
        IntRef totalRecordCount;

        public EvtxRecordGroupExtractor(String subKey, ArrayList<EvtxRecord> recs, ParseContext context, ContentHandler handler, IntRef maxProviderId, IntRef totalRecordCount) {
            this.subKey = subKey;
            this.recs = recs;
            this.context = context;
            this.handler = handler;
            this.maxProviderId = maxProviderId;
            this.totalRecordCount = totalRecordCount;
        }

        public void run() {
            if (recs.size() <= 0) {
                return;
            }
            try {
                EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

                ProviderIDMap providerIDMap = context.get(ProviderIDMap.class);
                if (providerIDMap == null) {
                    providerIDMap = new ProviderIDMap();
                    context.set(ProviderIDMap.class, providerIDMap);
                }
                GroupPageCountMap groupPageCountMap = context.get(GroupPageCountMap.class);
                if (groupPageCountMap == null) {
                    groupPageCountMap = new GroupPageCountMap();
                    context.set(GroupPageCountMap.class, groupPageCountMap);
                }

                String currentProvider = subKey.substring(0, subKey.indexOf(";"));
                String providerVid = providerIDMap.get(currentProvider);
                String providerGUID = recs.get(0).getEventProviderGUID();
                if (providerVid == null) {
                    maxProviderId.val++;
                    providerVid = Integer.toString(maxProviderId.val);
                    providerIDMap.put(currentProvider, providerVid);

                    Metadata providerMetadata = new Metadata();
                    providerMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, EVTX_RECORD_MIME_TYPE.toString());
                    providerMetadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                    providerMetadata.set(ExtraProperties.EMBEDDED_FOLDER, "true");
                    providerMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(-1));
                    providerMetadata.set(TikaCoreProperties.TITLE, currentProvider.substring(currentProvider.lastIndexOf(":") + 1));// eventtype
                    providerMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, providerVid);
                    providerMetadata.set(EVTX_METADATA_PREFIX + "ProviderGUID", providerGUID);
                    extractor.parseEmbedded(new EmptyInputStream(), handler, providerMetadata, false);
                }

                String groupTitle = subKey.substring(currentProvider.length() + 1);
                Integer page = groupPageCountMap.get(subKey);
                if (page == null) {
                    page = 1;
                } else {
                    page++;
                }
                groupPageCountMap.put(subKey, page);
                String pageStr = Integer.toString(page);
                groupTitle += "_" + "0".repeat(8 - pageStr.length()) + pageStr;

                Metadata recordMetadata = new Metadata();
                recordMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, EVTX_RECORD_MIME_TYPE.toString());
                recordMetadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                recordMetadata.set(ExtraProperties.PARENT_VIRTUAL_ID, providerVid);
                recordMetadata.set(TikaCoreProperties.TITLE, groupTitle);// eventtype

                StringBuffer content = new StringBuffer();
                int groupRecordCount = 0;
                for (Iterator iterator2 = recs.iterator(); iterator2.hasNext();) {
                    EvtxRecord evtxRecord = (EvtxRecord) iterator2.next();
                    String recContent = evtxRecord.getBinXml().toString();
                    String date = evtxRecord.getEventDateTime();

                    String eventKey = EVTX_METADATA_PREFIX + evtxRecord.getEventProviderName() + ":" + evtxRecord.getEventId();
                    MetadataUtil.setMetadataType(eventKey, Date.class);
                    recordMetadata.add(eventKey, date);
                    recordMetadata.add(RECID_PROP, (int) evtxRecord.getEventRecordId());
                    recordMetadata.add(EVTX_METADATA_PREFIX + "ProviderGUID", providerGUID);
                    content.append(recContent);

                    HashMap<String, String> datas = evtxRecord.getEventData();
                    if (datas != null && datas.size() > 0) {
                        for (Iterator<Entry<String, String>> iterator3 = datas.entrySet().iterator(); iterator3.hasNext();) {
                            Entry<String, String> data = iterator3.next();
                            String metaKey = EVTX_METADATA_PREFIX + data.getKey();
                            MetadataUtil.setMetadataType(metaKey, String.class);
                            recordMetadata.add(metaKey, data.getValue());
                        }
                    }
                    groupRecordCount++;
                }

                recordMetadata.set(RECCOUNT_PROP, groupRecordCount);
                totalRecordCount.val += groupRecordCount;

                if (extractor.shouldParseEmbedded(recordMetadata)) {
                    ByteArrayInputStream chatStream = new ByteArrayInputStream(content.toString().getBytes());
                    extractor.parseEmbedded(chatStream, handler, recordMetadata, false);
                }
            } catch (SAXException | IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

        String filePath = ""; //$NON-NLS-1$
        ItemInfo itemInfo = context.get(ItemInfo.class);

        if (itemInfo != null)
            filePath = itemInfo.getPath();

        IntRef maxProviderId = new IntRef();
        IntRef totalRecordCount = new IntRef();

        MetadataUtil.setMetadataType(RECCOUNT_PROP.getName(), Integer.class);
        MetadataUtil.setMetadataType(RECID_PROP.getName(), Integer.class);

        if (extractor.shouldParseEmbedded(metadata)) {

            HashMap<String, ArrayList<EvtxRecord>> subItens = new HashMap<String, ArrayList<EvtxRecord>>();

            EvtxRecordConsumer co = new EvtxRecordConsumer() {

                @Override
                public void accept(EvtxRecord evtxRecord) {
                    String groupValue = "";
                    if (groupBy != null) {
                        for (int i = 0; i < groupBy.length; i++) {
                            if (groupBy[i].contains("@")) {
                                String[] terms = groupBy[i].split("@");
                                EvtxElement el = evtxRecord.getElement(terms[0]);
                                if (el != null) {
                                    groupValue += groupBy[i] + ":" + el.getAttributeByName(terms[1]);
                                }
                            } else {
                                groupValue += groupBy[i] + ":" + evtxRecord.getElementValue(groupBy[i]);
                            }
                            if (i < groupBy.length - 1) {
                                groupValue += ";";
                            }
                        }
                    }

                    ArrayList<EvtxRecord> recs = subItens.get(groupValue);
                    if (recs == null) {
                        recs = new ArrayList<EvtxRecord>();
                        subItens.put(groupValue, recs);
                    }
                    recs.add(evtxRecord);

                    if (recs.size() >= maxEventPerItem) {
                        EvtxRecordGroupExtractor ex = new EvtxRecordGroupExtractor(groupValue, recs, context, handler, maxProviderId, totalRecordCount);
                        ex.run();
                        subItens.put(groupValue, new ArrayList<>());// empty
                    }
                }
            };

            EvtxFile evtxFile = new EvtxFile(stream);
            evtxFile.setName(filePath);
            evtxFile.setEvtxRecordConsumer(co);
            try {
                evtxFile.processFile();

            } catch (EvtxParseException e) {
                throw new TikaException(e.getMessage(), e);
            }

            for (Iterator<String> iterator = subItens.keySet().iterator(); iterator.hasNext();) {
                String subKey = (String) iterator.next();

                ArrayList<EvtxRecord> recs = subItens.get(subKey);
                if (recs.size() > 0) {
                    EvtxRecordGroupExtractor ex = new EvtxRecordGroupExtractor(subKey, recs, context, handler, maxProviderId, totalRecordCount);
                    ex.run();
                }
            }

            // metadata.set(RECCOUNT_PROP, totalRecordCount);
        }
    }
}
