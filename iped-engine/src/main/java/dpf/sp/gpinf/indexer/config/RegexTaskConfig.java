package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import dpf.sp.gpinf.indexer.localization.Messages;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.exception.IPEDException;

public class RegexTaskConfig extends AbstractTaskConfig<Pair<Boolean, List<?>>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final String CONFIG_FILE = "RegexConfig.txt"; //$NON-NLS-1$
    private static final String ENABLE_PARAM = "enableRegexSearch"; //$NON-NLS-1$
    private static final String FORMAT_MATCHES = "formatRegexMatches"; //$NON-NLS-1$

    private boolean formatRegexMatches;
    private List<RegexEntry> regexList = new ArrayList<>();

    public boolean isFormatRegexMatches() {
        return formatRegexMatches;
    }

    public List<RegexEntry> getRegexList() {
        return regexList;
    }

    public static class RegexEntry implements Serializable {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

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

    public static final String replace(String s) {
        return s.replace("\\t", "\t") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\r", "\r") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\n", "\n") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\f", "\f") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\s", "[ \t\r\n\f]") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\S", "[^ \t\r\n\f") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\d", "[0-9]") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\D", "[^0-9]")
                .replace("\\w", "[0-9a-zA-Z_]") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\\W", "[^0-9a-zA-Z_]"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void replaceWordBoundaries(RegexEntry entry) {
        if (entry.regex.startsWith("\\b")) {
            entry.regex = "[^0-9a-zA-Z_]" + entry.regex.substring(2);
            entry.prefix++;
        }
        if (entry.regex.endsWith("\\b")) {
            entry.regex = entry.regex.substring(0, entry.regex.length() - 2) + "[^0-9a-zA-Z_]";
            entry.suffix++;
        }
    }

    @Override
    public Pair<Boolean, List<?>> getConfiguration() {
        return Pair.of(formatRegexMatches, regexList);
    }

    @Override
    public void setConfiguration(Pair<Boolean, List<?>> config) {
        formatRegexMatches = config.getLeft();
        regexList = (List<RegexEntry>) config.getRight();
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

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
                entry.ignoreCase = params.length > 1 ? Boolean.valueOf(params[1].trim()) : true;
                entry.prefix = params.length > 2 ? Integer.valueOf(params[2].trim()) : 0;
                entry.suffix = params.length > 3 ? Integer.valueOf(params[3].trim()) : 0;
                entry.regex = replace(values[1].trim());
                replaceWordBoundaries(entry);
                regexList.add(entry);
            }
        }

    }

}
