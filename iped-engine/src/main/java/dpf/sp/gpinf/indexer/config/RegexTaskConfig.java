package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RegexTaskConfig extends AbstractPropertiesConfigurable {

    private static final String CONFIG_FILE = "RegexConfig.txt"; //$NON-NLS-1$
    private static final String ENABLE_PARAM = "enableRegexSearch"; //$NON-NLS-1$
    private static final String FORMAT_MATCHES = "formatRegexMatches"; //$NON-NLS-1$

    private boolean taskEnabled;
    private boolean formatRegexMatches;
    private List<RegexEntry> regexList = new ArrayList<>();

    public boolean isTaskEnabled() {
        return taskEnabled;
    }

    public boolean isFormatRegexMatches() {
        return formatRegexMatches;
    }

    public List<RegexEntry> getRegexList() {
        return regexList;
    }

    public static class RegexEntry {

        private String regexName;
        private int prefix, suffix;
        private boolean ignoreCase;
        private String regex;

        public String getRegexName() {
            return regexName;
        }

        public int getPrefix() {
            return prefix;
        }

        public int getSuffix() {
            return suffix;
        }

        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        public String getRegex() {
            return regex;
        }
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE) || entry.endsWith(IPEDConfig.CONFIG_FILE);
            }
        };
    }

    public static final String replace(String s) {
        return s.replace("\\t", "\t") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\r", "\r") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\n", "\n") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\f", "\f") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\s", "[ \t\r\n\f]"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        if (resource.getFileName().toString().equals(IPEDConfig.CONFIG_FILE)) {
            properties.load(resource.toFile());
            String value = properties.getProperty(ENABLE_PARAM);
            if (value != null) {
                taskEnabled = Boolean.valueOf(value.trim());
            }
        } else {
            String content = Util.readUTF8Content(resource.toFile());
            for (String line : content.split("\n")) { //$NON-NLS-1$
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) //$NON-NLS-1$
                    continue;
                else {
                    String[] values = line.split("=", 2); //$NON-NLS-1$
                    if (values.length < 2)
                        throw new IPEDException(Messages.getString("RegexTask.SeparatorNotFound.1") + CONFIG_FILE //$NON-NLS-1$
                                + Messages.getString("RegexTask.SeparatorNotFound.2") + line); //$NON-NLS-1$
                    String name = values[0].trim();
                    if (name.equals(FORMAT_MATCHES)) {
                        formatRegexMatches = Boolean.valueOf(values[1].trim());
                        continue;
                    }
                    String[] params = name.split(","); //$NON-NLS-1$
                    RegexEntry entry = new RegexEntry();
                    entry.regexName = params[0].trim();
                    entry.prefix = params.length > 1 ? Integer.valueOf(params[1].trim()) : 0;
                    entry.suffix = params.length > 2 ? Integer.valueOf(params[2].trim()) : 0;
                    entry.ignoreCase = params.length > 3 ? Boolean.valueOf(params[3].trim()) : true;
                    entry.regex = replace(values[1].trim());
                    regexList.add(entry);
                }
            }
        }

    }

}
