package iped.engine.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import iped.engine.data.SimpleFilterNode;

public class AIFiltersConfig extends AbstractTaskConfig<String> {

    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "AIFiltersConfig.json";

    private SimpleFilterNode root;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectWriter writer = objectMapper.writerFor(SimpleFilterNode.class);
        try {
            return writer.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setConfiguration(String config) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectReader reader = objectMapper.readerFor(SimpleFilterNode.class);
        try {
            root = reader.readValue(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        populateParents(root);
    }

    public SimpleFilterNode getRootAIFilter() {
        return root;
    }

    @Override
    public String getTaskEnableProperty() {
        return "";
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {
        setConfiguration(Files.readString(resource));
    }

    private void populateParents(SimpleFilterNode aiFilter) {
        for (SimpleFilterNode child : aiFilter.getChildren()) {
            child.setParent(aiFilter);
            populateParents(child);
        }
    }
}
