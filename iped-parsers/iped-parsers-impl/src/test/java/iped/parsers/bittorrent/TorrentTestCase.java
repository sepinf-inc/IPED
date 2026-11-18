package iped.parsers.bittorrent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;

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

    protected String clean(String s) {
        StringBuilder ret = new StringBuilder();
        ret.append(s).append("\n");
        // Remove possible number formatting
        ret.append(s.replaceAll("[.,]", ""));
        // Replace possible Windows-style path separators
        ret.append(s.replace('\\', '/'));
        return ret.toString();
    }
}
