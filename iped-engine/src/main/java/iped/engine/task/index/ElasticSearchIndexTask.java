package iped.engine.task.index;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.action.ActionListener;
import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.Cancellable;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.Requests;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.PutMappingRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.Settings.Builder;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ElasticSearchTaskConfig;
import iped.engine.config.IndexTaskConfig;
import iped.engine.io.FragmentingReader;
import iped.engine.task.AbstractTask;
import iped.engine.task.MinIOTask.MinIODataRef;
import iped.engine.task.similarity.ImageSimilarity;
import iped.engine.task.similarity.ImageSimilarityTask;
import iped.engine.util.SSLFix;
import iped.engine.util.UIPropertyListenerProvider;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.io.ISeekableInputStreamFactory;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.IOUtil;

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
    
    private static boolean isEnabled = false;

    private ElasticSearchTaskConfig elasticConfig;

    private static RestHighLevelClient client;

    private static HashMap<String, String> cmdLineFields = new HashMap<>();

    private static List<ElasticSearchIndexTask> taskInstances = Collections.synchronizedList(new ArrayList<>());

    private static String user, password, indexName;

    private BulkRequest bulkRequest = new BulkRequest();

    private HashMap<String, String> idToPath = new HashMap<>();

    private int numRequests = 0;

    private AtomicBoolean onCommit = new AtomicBoolean();

    private char[] textBuf = new char[16 * 1024];

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    public List<Configurable<?>> getConfigurables() {
        ElasticSearchTaskConfig result = ConfigurationManager.get().findObject(ElasticSearchTaskConfig.class);
        if(result == null) {
            result = new ElasticSearchTaskConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        taskInstances.add(this);
        elasticConfig = configurationManager.findObject(ElasticSearchTaskConfig.class);

        if (!(isEnabled = elasticConfig.isEnabled())) {
            return;
        }

        if (client != null) {
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
                    var custombuilder=httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    // disable validation if configured
                    if (!elasticConfig.getValidateSSL()) {
                        custombuilder.setSSLContext(SSLFix.getUnsecureSSLContext()).setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    }
                    return custombuilder;
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
                .put(INDEX_REPLICAS_KEY, elasticConfig.getIndex_replicas()).put(IGNORE_MALFORMED, true)
                .put("index.knn", true);

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
        properties.put(ExtraProperties.LOCATIONS, Collections.singletonMap("type", "geo_point"));
        properties.put("extraAttributes." + ImageSimilarityTask.IMAGE_FEATURES,
                Map.of("type", "knn_vector", "dimension", ImageSimilarity.numFeatures, "method",
                        Map.of("name", "hnsw", "space_type", "l2", "engine", "nmslib")));

        Map<String, String> contentMapping = new HashMap<>(Map.of("type", "text"));

        if (elasticConfig.isTermVector()) {
            contentMapping.put("term_vector", "with_positions_offsets");
        }

        properties.put(BasicProps.CONTENT, contentMapping);

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

    public static void commit() throws IOException, InterruptedException {
        if (!isEnabled)
            return;
        UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", "Commiting to ElasticSearch...");
        for (ElasticSearchIndexTask instance : taskInstances) {
            LOGGER.info("Commiting Worker-" + instance.worker.id + " ElasticSearchTask..."); //$NON-NLS-1$ //$NON-NLS-2$
            instance.onCommit.set(true);
            instance.sendBulkRequest();
        }
        for (ElasticSearchIndexTask instance : taskInstances) {
            synchronized (instance) {
                while (instance.numRequests > 0) {
                    instance.wait();
                }
                instance.onCommit.set(false);
                instance.notifyAll();
            }
        }
    }

    @Override
    public void finish() throws Exception {
        if (!taskInstances.isEmpty()) {
            commit();
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
    protected synchronized void process(IItem item) throws Exception {

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

        IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
        FragmentingReader fragReader = new FragmentingReader(textReader, indexConfig.getTextSplitSize(),
                indexConfig.getTextOverlapSize());
        int fragNum = fragReader.estimateNumberOfFrags();
        if (fragNum == -1) {
            fragNum = 1;
        }

        // used for parent items in elastic to store just metadata info
        // globalID works like an 'UUID' and should be unique across cases
        String parentId = (String) item.getExtraAttribute(ExtraProperties.GLOBAL_ID);

        try {
            // creates the father;
            XContentBuilder jsonMetadata = getJsonMetadataBuilder(item);
            IndexRequest parentIndexRequest = createIndexRequest(parentId, parentId, jsonMetadata);
            bulkRequest.add(parentIndexRequest);
            idToPath.put(parentId, item.getPath());

            do {
                // used for children items in elastic to store text content
                String contenttrackID = Util.generatetrackIDForTextFrag(parentId, fragNum);

                // creates the json _source of the fragment
                XContentBuilder jsonContent = getJsonFragmentBuilder(item, fragReader, parentId, contenttrackID,
                        fragNum--);

                // creates the request
                IndexRequest contentRequest = createIndexRequest(contenttrackID, parentId, jsonContent);

                bulkRequest.add(contentRequest);

                idToPath.put(contenttrackID, item.getPath());

                LOGGER.debug("Added to bulk request {}", item.getPath());

                if (bulkRequest.estimatedSizeInBytes() >= elasticConfig.getMin_bulk_size()
                        || bulkRequest.numberOfActions() >= elasticConfig.getMin_bulk_items()) {

                    // do not send more requests while commit is going on
                    while (this.onCommit.get()) {
                        this.wait();
                    }
                    sendBulkRequest();
                }

            } while (!Thread.currentThread().isInterrupted() && fragReader.nextFragment());

        } finally {
            fragReader.close();
        }

    }

    private synchronized void sendBulkRequest() throws IOException {
        BulkRequest bulkRequest = this.bulkRequest;
        HashMap<String, String> idToPath = this.idToPath;

        this.bulkRequest = new BulkRequest();
        this.idToPath = new HashMap<>();

        if (bulkRequest.numberOfActions() == 0) {
            return;
        }
        try {
            numRequests++;
            while (numRequests > elasticConfig.getMax_async_requests()) {
                this.wait();
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
            synchronized (ElasticSearchIndexTask.this) {
                numRequests--;
                ElasticSearchIndexTask.this.notifyAll();
            }
        }

    }

    private XContentBuilder getJsonMetadataBuilder(IItem item) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();

        builder.startObject().field(BasicProps.EVIDENCE_UUID, item.getDataSource().getUUID())
                .field(BasicProps.ID, item.getId()).field("document_content", "document")
                .field(BasicProps.SUBITEMID, item.getSubitemId()).field(BasicProps.PARENTID, item.getParentId())
                .field(BasicProps.PARENTIDs, item.getParentIds())
                // TODO boost name?
                .field(BasicProps.NAME, item.getName()).field(BasicProps.LENGTH, item.getLength())
                .field(BasicProps.TYPE, item.getType()).field(BasicProps.PATH, item.getPath())
                .timeField(BasicProps.CREATED, item.getCreationDate()).timeField(BasicProps.MODIFIED, item.getModDate())
                .timeField(BasicProps.ACCESSED, item.getAccessDate())
                .timeField(BasicProps.CHANGED, item.getChangeDate())
                .field(BasicProps.CATEGORY, item.getCategorySet())
                .field(BasicProps.CONTENTTYPE, item.getMediaType().toString()).field(BasicProps.HASH, item.getHash())
                .field(BasicProps.THUMB, item.getThumb()).field(BasicProps.TIMEOUT, item.isTimedOut())
                .field(BasicProps.DELETED, item.isDeleted()).field(BasicProps.HASCHILD, item.hasChildren())
                .field(BasicProps.ISDIR, item.isDir()).field(BasicProps.ISROOT, item.isRoot())
                .field(BasicProps.CARVED, item.isCarved()).field(BasicProps.SUBITEM, item.isSubItem())
                .field(BasicProps.OFFSET, item.getFileOffset());

        var extraAttributes = new HashMap<String, Object>();
        extraAttributes.putAll(item.getExtraAttributeMap());
        if (extraAttributes.containsKey(ImageSimilarityTask.IMAGE_FEATURES)) {
            float v[] = new float[ImageSimilarity.numFeatures];
            byte vet[] = (byte[]) extraAttributes.get(ImageSimilarityTask.IMAGE_FEATURES);
            for (int i = 0; i < ImageSimilarity.numFeatures; i++) {
                v[i] = vet[i];
            }
            extraAttributes.put(ImageSimilarityTask.IMAGE_FEATURES, v);
        }
        builder.field("extraAttributes", extraAttributes);

        ISeekableInputStreamFactory isisf = item.getInputStreamFactory();
        String idInSource = item.getIdInDataSource();
        MinIODataRef minIODataRef = (MinIODataRef) item.getTempAttribute(MinIODataRef.class.getName());
        if (minIODataRef != null) {
            isisf = minIODataRef.inputStreamFactory;
            idInSource = minIODataRef.idInDataSource;
        }
        String sourcePath = getInputStreamSourcePath(isisf);
        builder.field(IndexItem.ID_IN_SOURCE, idInSource).field(IndexItem.SOURCE_PATH, sourcePath)
                .field(IndexItem.SOURCE_DECODER, sourcePath != null ? isisf.getClass().getName() : null);

        for (String key : getMetadataKeys(item)) {
            if (PREVIEW_IN_DATASOURCE.equals(key)) {
                HashMap<String, String> previewInDataSource = new HashMap<>();
                for (String preview : item.getMetadata().getValues(key)) {
                    String[] prevIt = preview.split(KEY_VAL_SEPARATOR);
                    if (prevIt.length == 2) {
                        previewInDataSource.put(prevIt[0], prevIt[1]);
                    }
                }
                if (item.getViewFile() != null) {
                    previewInDataSource.put("size", Long.toString(item.getViewFile().length()));
                }
                builder.field(key, previewInDataSource);

            } else if (key != null) {
                String[] values = item.getMetadata().getValues(key);
                if (ExtraProperties.LOCATIONS.equals(key)) {
                    List<float[]> locations = new ArrayList<>(values.length);
                    for (int i = 0; i < values.length; i++) {
                        String[] coord = values[i].split(";");
                        float[] point = { Float.parseFloat(coord[0]), Float.parseFloat(coord[1]) };
                        locations.add(point);
                    }
                    builder.array(key, locations.toArray());
                } else {
                    builder.array(key, values);
                }
            }
        }

        for (Entry<String, String> entry : cmdLineFields.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }

        return builder.endObject();
    }

    private XContentBuilder getJsonFragmentBuilder(IItem item, Reader textReader, String parentID,
            String contenttrackID, int fragNum) throws IOException {

        XContentBuilder builder = XContentFactory.jsonBuilder();

        // maps the content to its parent metadata
        HashMap<String, String> document_content = new HashMap<>();
        document_content.put("name", "content");
        document_content.put("parent", parentID);

        builder.startObject().field(BasicProps.EVIDENCE_UUID, item.getDataSource().getUUID())
                .field(BasicProps.ID, item.getId()).field("document_content", document_content)
                .field("contenttrackID", contenttrackID).field(IndexTask.FRAG_NUM, fragNum)
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

    private String getInputStreamSourcePath(ISeekableInputStreamFactory sisf) {
        if (sisf != null) {
            URI uri = sisf.getDataSourceURI();
            if (uri != null) {
                return Util.getRelativePath(output, uri);
            }
        }
        return null;
    }

    private String[] getMetadataKeys(IItem item) {
        return item.getMetadata().names();
    }

}
