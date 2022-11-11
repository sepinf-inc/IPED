package iped.engine.config;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import iped.engine.util.Util;

public class ExportByKeywordsConfig extends AbstractTaskConfig<List<String>> implements Externalizable {

    /**
     * 
     */
    private static final long serialVersionUID = 2L;

    public static final String CONFIG_FILE = "KeywordsToExport.txt";

    private List<String> keywords = new ArrayList<>();

    @Override
    public boolean isEnabled() {
        return !this.keywords.isEmpty();
    }

    public List<String> getKeywords() {
        return this.keywords;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {
        String content = null;
        try {
            content = Util.readUTF8Content(resource.toFile());
        }catch (ArrayIndexOutOfBoundsException e) {
            
        }
        if(content!=null) {
            for (String line : content.split("\n")) { //$NON-NLS-1$
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) { //$NON-NLS-1$
                    continue;
                }
                keywords.add(line);
            }
        }
    }

    @Override
    public List<String> getConfiguration() {
        return keywords;
    }

    @Override
    public void setConfiguration(List<String> config) {
        this.keywords = config;
    }

    @Override
    public String getTaskEnableProperty() {
        return ExportByCategoriesConfig.ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        long l = in.readLong();
        if (l != serialVersionUID) {
            throw new InvalidClassException("SerialVersionUID not supported: " + l);
        }
        int size = in.readInt();
        keywords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            keywords.add(in.readUTF());
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(serialVersionUID);
        out.writeInt(keywords.size());
        for (String s : keywords) {
            out.writeUTF(s);
        }
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, CONFIG_FILE);
            Files.write(confFile.toPath(),keywords);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
