package dpf.sp.gpinf.indexer.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class TextCache implements Closeable {

    private static int MAX_MEMORY_CHARS = 10000000;

    private StringBuilder sb = new StringBuilder();
    private File tmp;
    private Writer writer;
    private long size = 0;

    public void write(String string) throws IOException {
        this.write(string.toCharArray(), 0, string.length());
    }

    public void write(char[] buf, int off, int len) throws IOException {
        if (tmp == null && sb != null && sb.length() + len > MAX_MEMORY_CHARS) {
            tmp = File.createTempFile("text", null);
            writer = Files.newBufferedWriter(tmp.toPath(), StandardOpenOption.APPEND);
            writer.write(sb.toString());
            sb = null;
        }

        if (sb != null)
            sb.append(buf, off, len);

        if (writer != null)
            try {
                writer.write(buf, off, len);
            } catch (IOException e) {
                // maybe no space left
                e.printStackTrace();
                IOUtil.closeQuietly(writer);
                writer = null;
                tmp.delete();
                tmp = null;
            }

        size += len;
    }

    public long getSize() {
        return size;
    }

    public Reader getTextReader() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }

        if (sb != null)
            return new StringReader(sb.toString());

        if (tmp != null)
            return Files.newBufferedReader(tmp.toPath());

        return null;
    }

    @Override
    public void close() throws IOException {
        if (writer != null)
            writer.close();
        if (tmp != null)
            tmp.delete();
    }

}
