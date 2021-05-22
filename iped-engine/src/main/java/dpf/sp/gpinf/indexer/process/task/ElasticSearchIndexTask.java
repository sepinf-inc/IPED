package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.Cancellable;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.ElasticSearchTaskConfig;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.FragmentingReader;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;
import iped3.sleuthkit.ISleuthKitItem;
import iped3.util.BasicProps;
import repackaged.org.apache.http.HttpHost;
import repackaged.org.apache.http.auth.AuthScope;
import repackaged.org.apache.http.auth.UsernamePasswordCredentials;
import repackaged.org.apache.http.client.CredentialsProvider;
import repackaged.org.apache.http.client.config.RequestConfig;
import repackaged.org.apache.http.impl.client.BasicCredentialsProvider;
import repackaged.org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

public class ElasticSearchIndexTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(ElasticSearchIndexTask.class);

    private static final String MAX_FIELDS_KEY = "index.mapping.total_fields.limit";
    private static final String IGNORE_MALFORMED = "index.mapping.ignore_malformed";
    private static final String INDEX_SHARDS_KEY = "index.number_of_shards";
    private static final String INDEX_REPLICAS_KEY = "index.number_of_replicas";
    private static final String INDEX_POLICY_KEY = "index.lifecycle.name";

    private static final String CMD_FIELDS_KEY = "elastic";
    private static final String INDEX_NAME_KEY = "indexName";
    private static final String USER_KEY = "user";
    private static final String PASSWORD_KEY = "password";

    public static final String PREVIEW_IN_DATASOURCE = "previewInDataSource";
    public static final String KEY_VAL_SEPARATOR = ":";

    private ElasticSearchTaskConfig elasticConfig;

    private static RestHighLevelClient client;

    private static AtomicInteger count = new AtomicInteger();

    private static HashMap<String, String> cmdLineFields = new HashMap<>();

    private static String user, password, indexName;

    private BulkRequest bulkRequest = new BulkRequest();

    private HashMap<String, String> idToPath = new HashMap<>();

    private int numRequests = 0;

    private Object lock = new Object();

    private char[] textBuf = new char[16 * 1024];

    @Override
    public boolean isEnabled() {
        return elasticConfig.isEnabled();
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        count.incrementAndGet();

        if (client != null) {
            return;
        }

        elasticConfig = ConfigurationManager.findObject(ElasticSearchTaskConfig.class);

        if (!elasticConfig.isEnabled()) {
            return;
        }

        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        String cmdFields = args.getExtraParams().get(CMD_FIELDS_KEY);
        if (cmdFields != null) {
            parseCmdLineFields(cmdFields);
        }

        if (indexName == null) {
            indexName = output.getParentFile().getName();
        }

        RestClientBuilder clientBuilder = RestClient
                .builder(new HttpHost(elasticConfig.getHost(), elasticConfig.getPort(), elasticConfig.getProtocol()))
                .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
                    @Override
                    public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
                        return requestConfigBuilder.setConnectTimeout(elasticConfig.getConnect_timeout())
                                .setSocketTimeout(elasticConfig.getTimeout_millis());
                    }
                });

        if (user != null && password != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
            clientBuilder.setHttpClientConfigCallback(new HttpClientConfigCallback() {
                @Override
                public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                    return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }
            });
        }

        client = new RestHighLevelClient(clientBuilder);

        boolean ping = client.ping(RequestOptions.DEFAULT);
        if (!ping) {
            throw new IOException("ElasticSearch cluster at " + elasticConfig.getHost() + ":" + elasticConfig.getPort()
                    + " not responding to ping.");
        }

        if (args.isRestart()) {
            deleteIndex(indexName);
        }

        if (!args.isAppendIndex() && !args.isContinue()) {
            if (indexExists(indexName)) {
                throw new IPEDException("ElasticSearch index already exists: " + indexName);
            } else {
                createIndex(indexName);
            }
        } else if (!indexExists(indexName)) {
            throw new IPEDException("ElasticSearch index does not exist: " + indexName);
        }

    }

    private void parseCmdLineFields(String cmdFields) {
        String[] entries = cmdFields.split(";");
        for (String entry : entries) {
            String[] pair = entry.split(":", 2);
            if (USER_KEY.equals(pair[0]))
                user = pair[1];
            else if (PASSWORD_KEY.equals(pair[0]))
                password = pair[1];
            else if (INDEX_NAME_KEY.equals(pair[0])) {
                indexName = pair[1];
            } else
                cmdLineFields.put(pair[0], pair[1]);
        }
    }

    private boolean indexExists(String indexName) throws IOException {
        GetIndexRequest request = new GetIndexRequest(indexName);
        return client.indices().exists(request, RequestOptions.DEFAULT);
    }

    private void createIndex(String indexName) throws IOException {

        CreateIndexRequest request = new CreateIndexRequest(indexName);
        Builder builder = Settings.builder().put(MAX_FIELDS_KEY, elasticConfig.getMax_fields())
                .put(INDEX_SHARDS_KEY, elasticConfig.getIndex_shards())
                .put(INDEX_REPLICAS_KEY, elasticConfig.getIndex_replicas())
                .put(IGNORE_MALFORMED, true);

        if (!elasticConfig.getIndex_policy().isEmpty()) {
            builder.put(INDEX_POLICY_KEY, elasticConfig.getIndex_policy());
        }

        if (elasticConfig.isUseCustomAnalyzer()) {
            builder.put("analysis.tokenizer.latinExtB.type", "simple_pattern") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("analysis.tokenizer.latinExtB.pattern", getLatinExtendedBPattern()) //$NON-NLS-1$ //$NON-NLS-2$
                    .put("analysis.analyzer.default.type", "custom") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("analysis.analyzer.default.tokenizer", "latinExtB") //$NON-NLS-1$ //$NON-NLS-2$
                    .putList("analysis.analyzer.default.filter", "lowercase", "asciifolding"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        request.settings(builder);

        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        if (!response.isAcknowledged()) {
            throw new IOException("Creation of index '" + indexName + "'failed!");
        }
        createFieldMappings(indexName);

    }

    private void deleteIndex(String indexName) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse deleteIndexResponse = client.indices().delete(request, RequestOptions.DEFAULT);
        boolean acknowledged = deleteIndexResponse.isAcknowledged();
        if (!acknowledged) {
            throw new IOException("Fail to delete index in ElasticSearch: " + indexName);
        }
    }

    private String getLatinExtendedBPattern() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i <= 0x24F; i++) {
            if (Character.isLetterOrDigit(i))
                sb.append(new String(Character.toChars(i)));
        }
        // photoDNA has 144 bytes
        sb.append("]{1,288}"); //$NON-NLS-1$ //$NON-NLS-2$
        return sb.toString();
    }

    private void createFieldMappings(String indexName) throws IOException {
        HashMap<String, Object> properties = new HashMap<>();
        properties.put(BasicProps.EVIDENCE_UUID, Collections.singletonMap("type", "keyword")); //$NON-NLS-1$ //$NON-NLS-2$
        properties.put(BasicProps.ID, Collections.singletonMap("type", "keyword"));
        properties.put(BasicProps.PARENTID, Collections.singletonMap("type", "keyword"));
        properties.put(BasicProps.PARENTIDs, Collections.singletonMap("type", "keyword"));

        // mapping the parent-child relation
        /*
         * "document_content": { "type": "join", "relations": { "document": "content" }
         * }
         */
        HashMap<String, Object> documentContentRelation = new HashMap<>();
        documentContentRelation.put("type", "join");
        documentContentRelation.put("relations", Collections.singletonMap("document", "content"));
        properties.put("document_content", documentContentRelation);

        HashMap<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("properties", properties);
        PutMappingRequest putMappings = new PutMappingRequest(indexName);
        putMappings.source(jsonMap);

        AcknowledgedResponse putMappingResponse = client.indices().putMapping(putMappings, RequestOptions.DEFAULT);
        if (!putMappingResponse.isAcknowledged()) {
            throw new IOException("Creation of mappings in index '" + indexName + "'failed!");
        }
    }

    private static List<ElasticSearchIndexTask> taskInstances = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void finish() throws Exception {

        if (bulkRequest.numberOfActions() > 0) {
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                    "Finishing Worker-" + worker.id + " ElasticSearchTask..."); //$NON-NLS-1$
            LOGGER.info("Finishing Worker-" + worker.id + " ElasticSearchTask..."); //$NON-NLS-1$ //$NON-NLS-2$
            sendBulkRequest();
        }

        taskInstances.add(this);

        if (count.decrementAndGet() == 0) {
            for (ElasticSearchIndexTask instance : taskInstances) {
                synchronized (instance.lock) {
                    while (instance.numRequests > 0) {
                        instance.lock.wait();
                    }
                }
            }
            taskInstances.clear();
            IOUtil.closeQuietly(client);
        }
    }

    private IndexRequest createIndexRequest(String id, String route, XContentBuilder jsonData) throws IOException {

        IndexRequest indexRequest = Requests.indexRequest(indexName);

        indexRequest.id(id);

        // routing is required when using parent-child relations
        indexRequest.routing(route);

        // json data to be inserted
        indexRequest.source(jsonData);

        indexRequest.timeout(TimeValue.timeValueMillis(elasticConfig.getTimeout_millis()));
        indexRequest.opType(OpType.CREATE);

        return indexRequest;

    }

    @Override
    protected void process(IItem item) throws Exception {

        Reader textReader = null;

        if (!item.isToAddToCase()) {
            if (IndexTask.isTreeNodeOnly(item)) {
                IndexTask.configureTreeNodeAttributes(item);
                textReader = new StringReader("");
            } else
                return;
        }

        if (textReader == null) {
            textReader = item.getTextReader();
        }

        if (textReader == null) {
            LOGGER.warn("Null text reader: " + item.getPath() + " ("
                    + (item.getLength() != null ? item.getLength() : "null") + " bytes)");
            textReader = new StringReader(""); //$NON-NLS-1$
        }

        FragmentingReader fragReader = new FragmentingReader(textReader);
        int fragNum = fragReader.estimateNumberOfFrags();
        if (fragNum == -1) {
            fragNum = 1;
        }

        String parentId = Util.getPersistentId(item);

        try {
            // creates the father;
            XContentBuilder jsonMetadata = getJsonMetadataBuilder(item);
            IndexRequest parentIndexRequest = createIndexRequest(parentId, parentId, jsonMetadata);
            bulkRequest.add(parentIndexRequest);
            idToPath.put(parentId, item.getPath());

            do {
                String contentPersistentId = Util.generatePersistentIdForTextFrag(parentId, fragNum--);

                // creates the json _source of the fragment
                XContentBuilder jsonContent = getJsonFragmentBuilder(item, fragReader, parentId, contentPersistentId);

                // creates the request
                IndexRequest contentRequest = createIndexRequest(contentPersistentId, parentId, jsonContent);

                bulkRequest.add(contentRequest);

                idToPath.put(contentPersistentId, item.getPath());

                LOGGER.debug("Added to bulk request {}", item.getPath());

                if (bulkRequest.estimatedSizeInBytes() >= elasticConfig.getMin_bulk_size()
                        || bulkRequest.numberOfActions() >= elasticConfig.getMin_bulk_items()) {
                    sendBulkRequest();
                    bulkRequest = new BulkRequest();
                    idToPath = new HashMap<>();
                }

            } while (!Thread.currentThread().isInterrupted() && fragReader.nextFragment());

        } finally {
            item.setExtraAttribute(IndexItem.PERSISTENT_ID, parentId);
            fragReader.close();
        }

    }

    private void sendBulkRequest() throws IOException {
        try {
            synchronized (lock) {
                if (++numRequests > elasticConfig.getMax_async_requests()) {
                    lock.wait();
                }
            }
            Cancellable cancellable = client.bulkAsync(bulkRequest, RequestOptions.DEFAULT,
                    new BulkResponseListener(idToPath));

        } catch (Exception e) {
            LOGGER.error("Error indexing to ElasticSearch " + bulkRequest.getDescription(), e);
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
                    String msg = failure.getMessage();
                    if (!msg.contains("document already exists")) { //$NON-NLS-1$
                        LOGGER.error("Elastic failure result {}: {}", path, msg); //$NON-NLS-1$
                    } else {
                        LOGGER.debug("Elastic failure result {}: {}", path, msg); //$NON-NLS-1$
                    }
                } else {
                    LOGGER.debug("Elastic result {} {}", bulkItemResponse.getResponse().getResult(),
                            idPathMap.get(bulkItemResponse.getId()));
                }
            }
        }

        @Override
        public void onFailure(Exception e) {
            notifyWaitingRequests();
            LOGGER.error("Error indexing to ElasticSearch ", e);
        }

        private void notifyWaitingRequests() {
            synchronized (lock) {
                numRequests--;
                lock.notify();
            }
        }

    }

    private XContentBuilder getJsonMetadataBuilder(IItem item) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();

        String inputStreamSrcPath = getInputStreamSourcePath(item);
        builder.startObject().field(BasicProps.EVIDENCE_UUID, item.getDataSource().getUUID())
                .field(BasicProps.ID, item.getId()).field("document_content", "document")
                .field(BasicProps.SUBITEMID, item.getSubitemId()).field(BasicProps.PARENTID, item.getParentId())
                .field(BasicProps.PARENTIDs, item.getParentIds())
                .field(IndexItem.SLEUTHID,
                        item instanceof ISleuthKitItem ? ((ISleuthKitItem) item).getSleuthId() : null)
                .field(IndexItem.ID_IN_SOURCE, item.getIdInDataSource())
                .field(IndexItem.SOURCE_PATH, inputStreamSrcPath)
                .field(IndexItem.SOURCE_DECODER,
                        inputStreamSrcPath != null ? item.getInputStreamFactory().getClass().getName() : null)
                // TODO boost name?
                .field(BasicProps.NAME, item.getName()).field(BasicProps.LENGTH, item.getLength())
                .field(BasicProps.TYPE, item.getType().getLongDescr()).field(BasicProps.PATH, item.getPath())
                .timeField(BasicProps.CREATED, item.getCreationDate()).timeField(BasicProps.MODIFIED, item.getModDate())
                .timeField(BasicProps.ACCESSED, item.getAccessDate())
                .timeField(BasicProps.RECORDDATE, item.getRecordDate()).field(BasicProps.EXPORT, item.getFileToIndex())
                .field(BasicProps.CATEGORY, item.getCategorySet())
                .field(BasicProps.CONTENTTYPE, item.getMediaType().toString()).field(BasicProps.HASH, item.getHash())
                .field(BasicProps.THUMB, item.getThumb()).field(BasicProps.TIMEOUT, item.isTimedOut())
                .field(BasicProps.DUPLICATE, item.isDuplicate()).field(BasicProps.DELETED, item.isDeleted())
                .field(BasicProps.HASCHILD, item.hasChildren()).field(BasicProps.ISDIR, item.isDir())
                .field(BasicProps.ISROOT, item.isRoot()).field(BasicProps.CARVED, item.isCarved())
                .field(BasicProps.SUBITEM, item.isSubItem()).field(BasicProps.OFFSET, item.getFileOffset())
                .field("extraAttributes", item.getExtraAttributeMap());

        for (String key : getMetadataKeys(item)) {
            if (PREVIEW_IN_DATASOURCE.equals(key)) {
                HashMap<String, String> previewInDataSource = new HashMap<>();
                for (String preview : item.getMetadata().getValues(key)) {
                    String[] prevIt = preview.split(KEY_VAL_SEPARATOR);
                    if (prevIt.length == 2) {
                        previewInDataSource.put(prevIt[0], prevIt[1]);
                    }
                }
                builder.field(key, previewInDataSource);

            } else if (key != null) {
                builder.array(key, item.getMetadata().getValues(key));
            }
        }

        for (Entry<String, String> entry : cmdLineFields.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }

        return builder.endObject();
    }

    private XContentBuilder getJsonFragmentBuilder(IItem item, Reader textReader, String parentID,
            String contentPersistentId) throws IOException {

        XContentBuilder builder = XContentFactory.jsonBuilder();

        // maps the content to its parent metadata
        HashMap<String, String> document_content = new HashMap<>();
        document_content.put("name", "content");
        document_content.put("parent", parentID);

        builder.startObject().field(BasicProps.EVIDENCE_UUID, item.getDataSource().getUUID())
                .field(BasicProps.ID, item.getId()).field("document_content", document_content)
                .field("contentPersistentId", contentPersistentId)
                .field(BasicProps.CONTENT, getStringFromReader(textReader));

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
        if (item.getInputStreamFactory() != null) {
            URI uri = item.getInputStreamFactory().getDataSourceURI();
            if (uri != null) {
                return Util.getRelativePath(output, uri);
            }
        }
        return null;
    }

    // catch rare ConcurrentModificationException if metadata is updated by
    // disconnected timed out threads
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
