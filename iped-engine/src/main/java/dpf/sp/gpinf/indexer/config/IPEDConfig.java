package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

import java.nio.file.Path;

public class IPEDConfig extends AbstractPropertiesConfigurable {

    boolean toAddUnallocated = false;
    boolean toAddFileSlacks = false;
    boolean enableHtmlReport = true;

    public static final String CONFDIR = "confdir";
    public static final String TOADDUNALLOCATED = "addUnallocated";
    public static final String TOADDFILESLACKS = "addFileSlacks";
    public static final String ENABLEHTMLREPORT = "enableHTMLReport"; //$NON-NLS-1$
    public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    public IPEDConfig() {
        propNames.add(IPEDConfig.CONFDIR);
        propNames.add(IPEDConfig.TOADDUNALLOCATED);
        propNames.add(IPEDConfig.TOADDFILESLACKS);
    }

    public String getConfDir() {
        return (String) properties.get(IPEDConfig.CONFDIR);
    }

    public boolean isToAddUnallocated() {
        return toAddUnallocated;
    }

    public boolean isToAddFileSlacks() {
        return toAddFileSlacks;
    }

    public boolean isHtmlReportEnabled() {
        return enableHtmlReport;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        String value = null;

        value = properties.getProperty(TOADDUNALLOCATED); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            toAddUnallocated = Boolean.valueOf(value);
        }

        value = properties.getProperty(TOADDFILESLACKS); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            toAddFileSlacks = Boolean.valueOf(value);
        }

        value = properties.getProperty("indexUnknownFiles"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(IndexerDefaultParser.FALLBACK_PARSER_PROP, value.trim());
        }

        value = properties.getProperty("indexCorruptedFiles"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(IndexerDefaultParser.ERROR_PARSER_PROP, value.trim());
        }

        value = properties.getProperty(ENABLEHTMLREPORT);
        if (value != null && !value.trim().isEmpty()) {
            enableHtmlReport = Boolean.valueOf(value.trim());
        }

    }

}
