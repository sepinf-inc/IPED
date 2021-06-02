package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import org.apache.tika.fork.ForkParser2;

import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.parsers.PDFOCRTextParser;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;

public class PDFToImageConfig extends AbstractPropertiesConfigurable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "conf/AdvancedConfig.txt"; //$NON-NLS-1$

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processConfigs(List<Path> resources) throws IOException {
        for (Iterator<Path> iterator = resources.iterator(); iterator.hasNext();) {
            Path path = iterator.next();
            processConfig(path);
        }
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        String value = null;

    }
}
