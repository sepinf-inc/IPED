package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Cancellable;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.FragmentingReader;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;
import iped3.sleuthkit.ISleuthKitItem;
import iped3.util.BasicProps;

public class ElasticSearchIndexTask extends AbstractTask {
    
    private static Logger LOGGER = LoggerFactory.getLogger(ElasticSearchIndexTask.class);
    
    private static final String CONF_FILE_NAME = "ElasticSearchConfig.txt";
    
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";
    private static final String MAX_FIELDS_KEY = "index.mapping.total_fields.limit";
    private static final String INDEX_SHARDS_KEY = "index.number_of_shards";
    private static final String INDEX_REPLICAS_KEY = "index.number_of_replicas";
    private static final String INDEX_POLICY_KEY = "index.lifecycle.name";
    private static final String MIN_BULK_SIZE_KEY = "min_bulk_size";
    private static final String MIN_BULK_ITEMS_KEY = "min_bulk_items";
    private static final String MAX_ASYNC_REQUESTS_KEY = "max_async_requests";
    private static final String TIMEOUT_MILLIS_KEY = "timeout_millis";
    private static final String CONNECT_TIMEOUT_KEY = "connect_timeout_millis";
    private static final String CMD_FIELDS_KEY = "elastic";
    
    private static String host;
    private static int port = 9200;
    private static int max_fields = 10000;
    private static int min_bulk_size = 1 << 23;
    private static int min_bulk_items = 1000;
    private static int connect_timeout = 5000;
    private static int timeout_millis = 3600000;
    private static int max_async_requests = 5;
    private static int index_shards = 1;
    private static int index_replicas = 1;
    private static String index_policy = "default_policy";
    
    private static RestHighLevelClient client;
    
    private static AtomicInteger count = new AtomicInteger();
    
    private static HashMap<String, String> cmdLineFields = new HashMap<>();
    
    private String indexName;
    
    private BulkRequest bulkRequest = new BulkRequest();
    
    private HashMap<String, String> idToPath = new HashMap<>();
    
    private int numRequests = 0;
    
    private Object lock = new Object();
    
    private char[] textBuf = new char[16 * 1024];

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        count.incrementAndGet();
        
        indexName = output.getParentFile().getName();
        
        if(client != null) {
            return;
        }
        
        loadConfFile(new File(confDir, CONF_FILE_NAME));
        
        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        String cmdFields = args.getExtraParams().get(CMD_FIELDS_KEY);
        if(cmdFields != null) {
            parseCmdLineFields(cmdFields);
        }
        
        client = new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, "http")).setRequestConfigCallback(
                new RestClientBuilder.RequestConfigCallback() {
                    @Override
                    public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
                        return requestConfigBuilder
                            .setConnectTimeout(connect_timeout)
                            .setSocketTimeout(timeout_millis);
                    }
                }));
        
        boolean ping = client.ping(RequestOptions.DEFAULT);
        if(!ping) {
            throw new IOException("ElasticSearch cluster at " + host + ":" + port + " not responding to ping.");
        }
        
        createIndex(indexName);
    }
    
    private void parseCmdLineFields(String cmdFields) {
        String[] entries = cmdFields.split(";");
        for(String entry : entries) {
            String[] pair = entry.split(":");
            cmdLineFields.put(pair[0], pair[1]);
        }
    }
    
    private void loadConfFile(File file) throws IOException {
        UTF8Properties props = new UTF8Properties();
        props.load(file);
        
        host = props.getProperty(HOST_KEY).trim();
        port = Integer.valueOf(props.getProperty(PORT_KEY).trim());
        max_fields = Integer.valueOf(props.getProperty(MAX_FIELDS_KEY).trim());
        min_bulk_size = Integer.valueOf(props.getProperty(MIN_BULK_SIZE_KEY).trim());
        min_bulk_items = Integer.valueOf(props.getProperty(MIN_BULK_ITEMS_KEY).trim());
        connect_timeout = Integer.valueOf(props.getProperty(CONNECT_TIMEOUT_KEY).trim());
        timeout_millis = Integer.valueOf(props.getProperty(TIMEOUT_MILLIS_KEY).trim());
        max_async_requests = Integer.valueOf(props.getProperty(MAX_ASYNC_REQUESTS_KEY).trim());
        index_shards = Integer.valueOf(props.getProperty(INDEX_SHARDS_KEY).trim());
        index_replicas = Integer.valueOf(props.getProperty(INDEX_REPLICAS_KEY).trim());
        index_policy = props.getProperty(INDEX_POLICY_KEY).trim();
    }
    
    private void createIndex(String indexName) throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(Settings.builder()
                .put(MAX_FIELDS_KEY, max_fields)
                .put(INDEX_SHARDS_KEY, index_shards)
                .put(INDEX_REPLICAS_KEY, index_replicas)
                .put(INDEX_POLICY_KEY, index_policy)
                .put("analysis.analyzer.default.type", "custom") //$NON-NLS-1$ //$NON-NLS-2$
                .put("analysis.analyzer.default.tokenizer", "standard") //$NON-NLS-1$ //$NON-NLS-2$
                .putList("analysis.analyzer.default.filter", "lowercase", "asciifolding") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                );
        
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        if(!response.isAcknowledged()) {
            throw new IOException("Creation of index '" + indexName + "'failed!");
        }
    }

    @Override
    public void finish() throws Exception {
        
        if(bulkRequest.numberOfActions() > 0) {
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", "Finishing Worker-" + worker.id + " ElasticSearchTask..."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("Finishing Worker-" + worker.id + " ElasticSearchTask..."); //$NON-NLS-1$ //$NON-NLS-2$
            sendBulkRequest();
        }
        
        synchronized(lock) {
            while(numRequests > 0) {
                lock.wait();
            }
        }
        
        if(count.decrementAndGet() == 0) {
            IOUtil.closeQuietly(client);
        }
    }
    
    @Override
    protected void process(IItem item) throws Exception {
        
        Reader textReader = item.getTextReader();
        if(textReader == null) {
            LOGGER.warn("Null text reader: " + item.getPath() + " (" + (item.getLength() != null ? item.getLength() : "null") + " bytes)");
            textReader = new StringReader(""); //$NON-NLS-1$
        }
        FragmentingReader fragReader = new FragmentingReader(textReader);
        try {
            int fragNum = 0;
            do {
                String id = String.valueOf(item.getId());
                if(fragNum > 0) {
                    id += "_frag" + fragNum;
                }
                
                XContentBuilder jsonBuilder = getJsonItemBuilder(item, fragReader);
                
                IndexRequest indexRequest = Requests.indexRequest(indexName);
                indexRequest.id(id);
                indexRequest.source(jsonBuilder);
                indexRequest.timeout(TimeValue.timeValueMillis(timeout_millis));
                
                bulkRequest.add(indexRequest);
                idToPath.put(id, item.getPath());
                
                LOGGER.debug("Added to bulk request " + item.getPath());
                
                if(bulkRequest.estimatedSizeInBytes() >= min_bulk_size || bulkRequest.numberOfActions() >= min_bulk_items) {
                    sendBulkRequest();
                    bulkRequest = new BulkRequest();
                    idToPath = new HashMap<>();
                }
                
                fragNum++;
                
            } while(!Thread.currentThread().isInterrupted() && fragReader.nextFragment());
            
        }finally {
            fragReader.close();
        }
        
    }
    
    private void sendBulkRequest() throws IOException {
        try {
            synchronized(lock) {
                if(++numRequests > max_async_requests) {
                    lock.wait();
                }
            }
            Cancellable cancellable = client.bulkAsync(bulkRequest, RequestOptions.DEFAULT, new BulkResponseListener(idToPath));
            
        }catch(Exception e) {
            LOGGER.error("Error indexing to ElasticSearch " + bulkRequest.getDescription(), e);
            e.printStackTrace();
        }
    }
    
    private class BulkResponseListener implements ActionListener<BulkResponse> {
        
        HashMap<String, String> idPathMap;
        
        private BulkResponseListener(HashMap<String, String> itemMap) {
            this.idPathMap = itemMap;
        }

        @Override
        public void onResponse(BulkResponse response) {
            
            notifyWaitingRequests();
            
            for (BulkItemResponse bulkItemResponse : response) {
                if (bulkItemResponse.isFailed()) {
                    BulkItemResponse.Failure failure = bulkItemResponse.getFailure();
                    String path = idPathMap.get(bulkItemResponse.getId());
                    LOGGER.warn("Error indexing to ElasticSearch " + path + ": " + failure.getMessage());
                }
            }
        }

        @Override
        public void onFailure(Exception e) {
            notifyWaitingRequests();
            LOGGER.error("Error indexing to ElasticSearch ", e);
        }
        
        private void notifyWaitingRequests() {
            synchronized(lock) {
                numRequests--;
                lock.notify();
            }
        }
        
    }
    
    private XContentBuilder getJsonItemBuilder(IItem item, Reader textReader) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        
        builder.startObject()
                .field(BasicProps.EVIDENCE_UUID, item.getDataSource().getUUID())
                .field(BasicProps.ID, item.getId())
                .field(BasicProps.PARENTID, item.getParentId())
                .field(BasicProps.PARENTIDs, item.getParentIdsString())
                .field(IndexItem.SLEUTHID, item instanceof ISleuthKitItem ? ((ISleuthKitItem)item).getSleuthId() : null)
                .field(IndexItem.ID_IN_SOURCE, item.getIdInDataSource())
                .field(IndexItem.SOURCE_PATH, getInputStreamSourcePath(item))
                .field(IndexItem.SOURCE_DECODER, item.getInputStreamFactory() != null ? item.getInputStreamFactory().getClass().getName() : null)
                //TODO boost name?
                .field(BasicProps.NAME, item.getName())
                .field(BasicProps.LENGTH, item.getLength())
                .field(BasicProps.TYPE, item.getType().getLongDescr())
                .field(BasicProps.PATH, item.getPath())
                .timeField(BasicProps.CREATED, item.getCreationDate())
                .timeField(BasicProps.MODIFIED, item.getModDate())
                .timeField(BasicProps.ACCESSED, item.getAccessDate())
                .timeField(BasicProps.RECORDDATE, item.getRecordDate())
                .field(BasicProps.EXPORT, item.getFileToIndex())
                .field(BasicProps.CATEGORY, item.getCategorySet())
                .field(BasicProps.CONTENTTYPE, item.getMediaType().toString())
                .field(BasicProps.HASH, item.getHash())
                .field(BasicProps.THUMB, item.getThumb())
                .field(BasicProps.TIMEOUT, item.isTimedOut())
                .field(BasicProps.DUPLICATE, item.isDuplicate())
                .field(BasicProps.DELETED, item.isDeleted())
                .field(BasicProps.HASCHILD, item.hasChildren())
                .field(BasicProps.ISDIR, item.isDir())
                .field(BasicProps.ISROOT, item.isRoot())
                .field(BasicProps.CARVED, item.isCarved())
                .field(BasicProps.SUBITEM, item.isSubItem())
                .field(BasicProps.OFFSET, item.getFileOffset())
                .field("extraAttributes", item.getExtraAttributeMap())
                .field(BasicProps.CONTENT, getStringFromReader(textReader));

        for (String key : getMetadataKeys(item)) {
            builder.array(key, item.getMetadata().getValues(key));
        }
        
        for(Entry<String, String> entry : cmdLineFields.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
                
        return builder.endObject();
    }
    
    private String getStringFromReader(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while ((i = reader.read(textBuf)) != -1) {
            sb.append(textBuf, 0, i);
        }
        return sb.toString();
    }
    
    private String getInputStreamSourcePath(IItem item) {
        if(item.getInputStreamFactory() != null) {
            return Util.getRelativePath(output, item.getInputStreamFactory().getDataSourcePath().toFile());
        }
        return null;
    }
    
    //catch rare ConcurrentModificationException if metadata is updated by disconnected timed out threads
    private String[] getMetadataKeys(IItem item) {
        String[] names = null;
        while (names == null) {
            try {
                names = item.getMetadata().names();
            } catch (ConcurrentModificationException e) {
            }
        }
        return names;
    }

}
