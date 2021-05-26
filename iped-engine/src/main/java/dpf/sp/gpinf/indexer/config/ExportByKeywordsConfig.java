package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.util.Util;
import macee.core.EnabledInterface;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ExportByKeywordsConfig extends AbstractPropertiesConfigurable implements EnabledInterface {

    private static final String CONFIG_FILE = "KeywordsToExport.txt";

    private List<String> keywords = new ArrayList<>();

    @Override
    public boolean isEnabled() {
        return !this.keywords.isEmpty();
    }

    public List<String> getKeywords() {
        return this.keywords;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        String content = Util.readUTF8Content(resource.toFile());
        for (String line : content.split("\n")) { //$NON-NLS-1$
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) { //$NON-NLS-1$
                continue;
            }
            keywords.add(line);
        }

    }

}
