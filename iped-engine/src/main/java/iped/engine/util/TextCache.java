package iped.engine.util;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.input.BoundedReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.utils.IOUtil;

public class TextCache implements Closeable, Cloneable {

    private static Logger logger = LoggerFactory.getLogger(TextCache.class);

    private static int MAX_MEMORY_CHARS = 10000000;

    private IItem sourceItem;
    private StringBuilder sb = new StringBuilder();
    private File tmp;
    private Writer writer;
    private long size = 0;
    private boolean diskCacheEnabled = true;
    private long offset = -1;
    private AtomicInteger refCount = new AtomicInteger();

    public void setSourceItem(IItem sourceItem) {
        this.sourceItem = sourceItem;
    }

    public void setEnableDiskCache(boolean diskCacheEnabled) {
        this.diskCacheEnabled = diskCacheEnabled;
    }

    public void write(String string) throws IOException {
        this.write(string.toCharArray(), 0, string.length());
    }

    public void write(char[] buf, int off, int len) throws IOException {
        if (tmp == null && sb != null && sb.length() + len > MAX_MEMORY_CHARS && diskCacheEnabled) {
            tmp = File.createTempFile("text", null);
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8));
            writer.write(sb.toString());
            sb = null;
        }

        if (sb != null && sb.length() < MAX_MEMORY_CHARS) {
            if (sb.length() + len > MAX_MEMORY_CHARS)
                len = MAX_MEMORY_CHARS - sb.length();
            sb.append(buf, off, len);
        }

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

        Reader reader = null;
        if (sb != null)
            reader = new StringReader(sb.toString());

        if (tmp != null) {
            try {
                reader = Files.newBufferedReader(tmp.toPath());

            } catch (FileSystemException | FileNotFoundException e) {
                logger.error("Error reading extracted text file{}, maybe your antivirus blocked or deleted it? {}",
                        sourceItem != null ? " from " + sourceItem.getPath() : "", e.toString());
                e.printStackTrace();
                return new StringReader("");
            }
        }

        if (reader != null) {
            if (offset > -1) {
                long skipped = 0;
                while (skipped < offset) {
                    skipped += reader.skip(offset - skipped);
                }
                reader = new BoundedReader(reader, (int) size);
            }
            return new KnownSizeReader(reader);
        }

        return null;
    }

    public void setTextBounds(long offset, int size) {
        if (offset < 0 || size < 0) {
            throw new IllegalArgumentException("Both offset & size must be non negative.");
        }
        if (offset + size > this.size) {
            throw new IllegalArgumentException("offset + size must be less than or equal to original text size.");
        }
        this.offset = offset;
        this.size = size;
    }

    @Override
    public void close() throws IOException {
        if (writer != null)
            writer.close();
        if (tmp != null && refCount.decrementAndGet() == 0)
            tmp.delete();
    }

    public class KnownSizeReader extends Reader {

        private Reader delegate;

        public KnownSizeReader(Reader delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return delegate.read(cbuf, off, len);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        public long getSize() {
            return size;
        }

    }

    @Override
    public TextCache clone() {
        TextCache o = new TextCache();
        o.sourceItem = sourceItem;
        o.sb = sb;
        o.tmp = tmp;
        // we just use clone for reading for now
        // o.writer = writer;
        o.size = size;
        o.diskCacheEnabled = diskCacheEnabled;
        o.offset = offset;
        o.refCount = refCount;
        refCount.incrementAndGet();
        return o;
    }

}
