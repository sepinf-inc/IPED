package iped.engine.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.tika.io.TemporaryResources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.task.index.IndexItem;

public class RemoteImageClassifier extends AbstractTask {

    private String url = "http://localhost:8000/zip";
    private Map<String, IItem> queue = new TreeMap<>();

    @Override
    public List<Configurable<?>> getConfigurables() {
        // TODO Auto-generated method stub
        return new ArrayList<Configurable<?>>();
    }

    public boolean isEnabled() {
        return true;
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    private void addFileToZip(String filename, byte[] dados, ZipOutputStream zos) throws IOException {

        ZipEntry zipEntry = new ZipEntry(filename);
        zos.putNextEntry(zipEntry);
        zos.write(dados, 0, dados.length);
        zos.closeEntry();

    }

    private void sendItemsToNextTask() throws Exception {
        // criar zip um com i.getThumb() de todos os itens
        System.out.println("Envia fila de tamanho" + queue.size());
        try (TemporaryResources tmp = new TemporaryResources()) {
            if (queue.size() > 0) {
                File zipFile = tmp.createTemporaryFile();
                try (FileOutputStream fos = new FileOutputStream(zipFile);
                        ZipOutputStream zos = new ZipOutputStream(fos)) {
                    for (String name : queue.keySet()) {
                        addFileToZip(name, queue.get(name).getThumb(), zos);
                    }

                }
                sendZipFile(zipFile);
            }
        } finally {
            for (IItem item : queue.values()) {
                if (item != null) {
                    super.sendToNextTask(item);
                }
            }
            queue.clear();
        }
    }

    private void sendZipFile(File zipFile) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);

            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .addBinaryBody("file", zipFile, ContentType.APPLICATION_OCTET_STREAM, zipFile.getName()).build();

            post.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    try (InputStream responseStream = response.getEntity().getContent()) {
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonResponse = objectMapper.readTree(responseStream);
                        // System.out.println("Server Response: " + jsonResponse.toPrettyString());
                        JsonNode resultArray = jsonResponse.get("results");
                        if (resultArray != null && resultArray.isArray()) {
                            for (JsonNode item : resultArray) {
                                String name = item.get("filename").asText();
                                IItem evidence;
                                if (name == null || (evidence = queue.get(name)) == null) {
                                    System.out.println("Warning: No matching item found ");
                                    continue;
                                }

                                JsonNode classes = item.get("class");
                                if (classes != null && classes.isObject()) {
                                    classes.fields().forEachRemaining(entry -> {
                                        String key = entry.getKey();
                                        double value = entry.getValue().asDouble();
                                        evidence.setExtraAttribute(key, value);

                                    });
                                } else {
                                    System.out.println("Warning: 'class' field is missing for filename: " + name);
                                }
                            }
                        } else {
                            System.out.println("Warning: 'results' array is missing in JSON response.");
                        }
                    }

                } else {
                    System.out.println("Failed to upload ZIP. HTTP Code: " + statusCode);
                }
            }
        }
    }

    protected void sendToNextTask(IItem item) throws Exception {
        if (!isEnabled()) {
            super.sendToNextTask(item);
            return;
        }
        if (queue.size() > 0 && (queue.size() >= 50 || item.isQueueEnd())) {
            sendItemsToNextTask();
        }
        if (!queue.containsValue(item) || item.isQueueEnd()) {
            super.sendToNextTask(item);
        }


    }

    @Override
    protected boolean processQueueEnd() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        if (evidence.isQueueEnd() && queue.size() > 0) {
            sendItemsToNextTask();
            return;
        }
        // TODO Auto-generated method stub
        if (!isEnabled() || !evidence.isToAddToCase() || evidence.getHashValue() == null
                || evidence.getThumb() == null || evidence.isQueueEnd()) {
            return;
        }
        queue.put(evidence.getExtraAttribute(IndexItem.TRACK_ID).toString() + ".jpg", evidence);

    }

}
