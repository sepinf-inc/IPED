package iped.engine.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.mime.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import iped.engine.data.Category;
import iped.properties.MediaTypes;

public class CategoryConfig extends AbstractTaskConfig<String> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "CategoriesConfig.json";

    private Category root;

    private transient Map<String, String> normalizedMap;

    private transient Map<String, Category> nameToCategoryMap;

    private Map<String, String> getNormalizedMap() {
        if (normalizedMap == null) {
            synchronized (this) {
                if (normalizedMap == null) {
                    Map<String, String> map = new HashMap<>();
                    Map<String, String> mimetypeToCategoryMap = getMimeToCategoryMap(root);
                    for(String key : mimetypeToCategoryMap.keySet()) {
                        MediaType type = MediaTypes.normalize(MediaType.parse(key));
                        String mime = type != null ? type.toString() : key;
                        map.put(mime, mimetypeToCategoryMap.get(key));
                    }
                    normalizedMap = map;
                }
            }
        }
        return normalizedMap;
    }

    private Map<String, String> getMimeToCategoryMap(Category category) {
        Map<String, String> mimetypeToCategoryMap = new HashMap<>();
        for (String mimeType : category.getMimes()) {
            mimetypeToCategoryMap.put(mimeType, category.getName());
        }
        for (Category subCategory : category.getChildren()) {
            mimetypeToCategoryMap.putAll(getMimeToCategoryMap(subCategory));
        }
        return mimetypeToCategoryMap;
    }

    public String getCategory(MediaType type) {

        do {
            String category = getNormalizedMap().get(type.toString());
            if (category == null) {
                category = getNormalizedMap().get(type.getType());
            }
            if (category != null) {
                return category;
            }

            type = MediaTypes.getParentType(type);

        } while (type != null);

        return ""; //$NON-NLS-1$
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectWriter writer = objectMapper.writerFor(Category.class);
        try {
            return writer.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setConfiguration(String config) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectReader reader = objectMapper.readerFor(Category.class);
        try {
            root = reader.readValue(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        populateParents(root);
    }

    public Category getRootCategory() {
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

    private void populateParents(Category category) {
        for (Category subCategory : category.getChildren()) {
            subCategory.setParent(category);
            populateParents(subCategory);
        }
    }

    private Map<String, Category> getNameToCategoryMap() {
        if (nameToCategoryMap == null) {
            synchronized (this) {
                if (nameToCategoryMap == null) {
                    nameToCategoryMap = buildNameToCategoryMap(root);
                }
            }
        }
        return nameToCategoryMap;
    }

    private Map<String, Category> buildNameToCategoryMap(Category category) {
        Map<String, Category> map = new HashMap<>();
        map.put(category.getName(), category);
        for (Category sub : category.getChildren()) {
            map.putAll(buildNameToCategoryMap(sub));
        }
        return map;
    }

    public Category getCategoryFromName(String categoryName) {
        return getNameToCategoryMap().get(categoryName);
    }

}
