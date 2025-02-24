package iped.engine.task;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.tika.io.TemporaryResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.RemoteImageClassifierConfig;
import iped.engine.task.die.DIETask;
import iped.engine.task.index.IndexItem;
import iped.utils.ImageUtil;
public class RemoteImageClassifier extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(RemoteImageClassifier.class);
    private String url = "http://localhost:8000/zip";
    private int batch_size = 50;
    private Map<String, IItem> queue = new TreeMap<>();
    private zipfile zip = null;
    private RemoteImageClassifierConfig config;

    private static class ResultItem {
        public String name;
        public TreeMap<String, ArrayList<Double>> classes = new TreeMap<>();

        public void addClass(String classname,double val) {
            if (!classes.containsKey(classname)) {
                classes.put(classname, new ArrayList<>());
            }
            classes.get(classname).add(val);
        }

        public double getClassProb(String classname) {
            double resp = 0;
            for (Double val : classes.get(classname)) {
                if (val > resp)
                    resp = val;
            }
            return resp;

        }
    }

    private static class zipfile {
        TemporaryResources tmp;
        File zipFile;
        FileOutputStream fos;
        ZipOutputStream zos;
        int size;

        public zipfile() throws IOException {
            tmp = new TemporaryResources();
            zipFile = tmp.createTemporaryFile();
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);
            size = 0;
        }

        public int size() {
            return size;
        }

        public void addFileToZip(String filename, byte[] dados) throws IOException {

            ZipEntry zipEntry = new ZipEntry(filename);

            zipEntry.setMethod(ZipEntry.STORED);
            CRC32 crc = new CRC32();
            crc.update(dados);

            zipEntry.setSize(dados.length);
            zipEntry.setCompressedSize(dados.length);
            zipEntry.setCrc(crc.getValue());


            zos.putNextEntry(zipEntry);
            zos.write(dados, 0, dados.length);
            zos.closeEntry();
            size++;

        }

        public File closeAndGetZip() throws IOException {
            zos.close();
            fos.close();
            return zipFile;
        }

        public void clean() throws IOException {
            tmp.close();
        }
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new RemoteImageClassifierConfig());
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        config = configurationManager.findObject(RemoteImageClassifierConfig.class);
        url = config.getUrl();
        batch_size = config.getBatchSize();
        if (zip == null) {
            zip = new zipfile();
        }

    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    private void sendItemsToNextTask() throws Exception {
        for (IItem item : queue.values()) {
            if (item != null) {
                super.sendToNextTask(item);
            }
        }
        queue.clear();
    }

    protected void sendToNextTask(IItem item) throws Exception {
        if (!isEnabled()) {
            super.sendToNextTask(item);
            return;
        }

        if (zip.size() > 0 && (zip.size() >= batch_size || item.isQueueEnd())) {
            sendZipFile(zip.closeAndGetZip());
            zip.clean();
            zip = new zipfile();
            sendItemsToNextTask();
            if (!item.isQueueEnd())
                return;
        }

        if (!queue.containsValue(item) || item.isQueueEnd()) {
            super.sendToNextTask(item);
        }

    }

    private void processResult(InputStream responseStream) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse = objectMapper.readTree(responseStream);
        logger.debug("Server Response: {}", jsonResponse.toPrettyString());
        JsonNode resultArray = jsonResponse.get("results");
        Map<String,ResultItem> results=new TreeMap<>();

        if (resultArray != null && resultArray.isArray()) {
            for (JsonNode item : resultArray) {
                String name = item.get("filename").asText();
                ResultItem res=null;
                if (name == null) {
                    logger.warn("Warning: Invalid name ");
                    continue;
                }
                if(name.split("_").length==2) {
                    name=name.split("_")[1];
                    res=results.get(name);
                }
                if (res == null) {
                    res = new ResultItem();
                    results.put(name, res);
                }

                JsonNode classes = item.get("class");
                if (classes != null && classes.isObject()) {
                    var iterator = classes.fields();
                    while (iterator.hasNext()) {
                        Map.Entry<String, JsonNode> entry = iterator.next();
                        String key = entry.getKey();
                        double value = entry.getValue().asDouble();
                        res.addClass(key, value);
                    }
                } else {
                    logger.warn("Warning: 'class' field is missing for filename: {}", name);
                }
            }

            for (String name : results.keySet()) {
                IItem evidence;
                if (name == null || (evidence = queue.get(name)) == null) {
                    logger.warn("Warning: No matching item found ");
                    continue;
                }
                ResultItem res = results.get(name);
                for (String classname : res.classes.keySet()) {
                    evidence.setExtraAttribute(classname, res.getClassProb(classname));
                }

            }

        } else {
            logger.warn("Warning: 'results' array is missing in JSON response.");
        }
    }
    
    private void sendZipFile(File zipFile) throws IOException {
        logger.info("Envia zip de tamanho {}", zip.size());
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);

            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .addBinaryBody("file", zipFile, ContentType.APPLICATION_OCTET_STREAM, zipFile.getName()).build();

            post.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    try (InputStream responseStream = response.getEntity().getContent()) {
                        processResult(responseStream);
                    }

                } else {
                    String errorMessage;
                    try (InputStream errorStream = response.getEntity().getContent()) {
                        errorMessage = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        errorMessage = "Failed to read error message from response.";
                    }

                    logger.error("Failed to upload ZIP. HTTP Code: {} - Response: {}", statusCode, errorMessage);
                }
            }
        }
    }

    @Override
    protected boolean processQueueEnd() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        if (evidence.isQueueEnd() && zip.size() > 0) {
            sendZipFile(zip.closeAndGetZip());
            zip = new zipfile();
            sendItemsToNextTask();
            return;
        }
        if (!isEnabled() || !evidence.isToAddToCase() || evidence.getHashValue() == null || evidence.getThumb() == null
                || evidence.getThumb().length < 10 || evidence.isQueueEnd()) {
            return;
        }

        if (DIETask.isVideoType(evidence.getMediaType()) || DIETask.isAnimationImage(evidence)) {

            // For videos call the detection method for each extracted frame image
            // (VideoThumbsTask must be enabled)
            File viewFile = evidence.getViewFile();
            if (viewFile != null && viewFile.exists()) {

                List<BufferedImage> frames = ImageUtil.getFrames(viewFile);
                int i = 0;
                String name = evidence.getExtraAttribute(IndexItem.TRACK_ID).toString() + ".jpg";
                for (BufferedImage frame : frames) {
                    String name_i = (++i) + "_" + name;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(frame, "jpeg", baos);
                    zip.addFileToZip(name_i, baos.toByteArray());
                }
                queue.put(name, evidence);

            }
        } else {
            String name = evidence.getExtraAttribute(IndexItem.TRACK_ID).toString() + ".jpg";
            zip.addFileToZip(name, evidence.getThumb());
            queue.put(name, evidence);
        }

    }

}
