package iped.parsers.bittorrent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import iped.parsers.util.BaseItemSearchContext;
import junit.framework.TestCase;

public abstract class TorrentTestCase extends TestCase {
    protected File getFile(String name) throws IOException {
        try {
            return new File(BaseItemSearchContext.class.getClassLoader().getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    protected InputStream getStream(String name) throws IOException {
        return Files.newInputStream(getFile(name).toPath());
    }

    protected Set<String> splitLines(String s) {
        Set<String> ret = new HashSet<String>();
        String[] lines = s.split("\\R");
        for (String line : lines) {
            ret.add(line);
            // Remove possible number formatting
            ret.add(line.replaceAll("[.,]", ""));
        }
        return ret;
    }
}
