/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dpf.sp.gpinf.indexer.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.tika.fork.ParsingTimeout;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.ParsingTaskConfig;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.CorruptedCarvedException;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.process.MimeTypesProcessingOrder;

/**
 * Reader for the text content from a given binary stream. This class uses a
 * background parsing task with a {@link Parser} ({@link AutoDetectParser} by
 * default) to parse the text content from a given input stream. The
 * {@link BodyContentHandler} class and a pipe is used to convert the push-based
 * SAX event stream to the pull-based character stream defined by the
 * {@link Reader} interface.
 *
 * @since Apache Tika 0.2
 */
public class ParsingReader extends Reader {

    private static Logger LOGGER = LoggerFactory.getLogger(ParsingReader.class);

    /**
     * Parser instance used for parsing the given binary stream.
     */
    private Parser parser;

    /**
     * Buffered read end of the pipe.
     */
    private final Reader reader;

    /**
     * Write end of the pipe.
     */
    private final Writer writer;

    /**
     * The binary stream being parsed.
     */
    private TikaInputStream stream;

    /**
     * Metadata associated with the document being parsed.
     */
    private final Metadata metadata;

    private long length = -1;

    /**
     * The parse context.
     */
    private final ParseContext context;

    /**
     * An exception (if any) thrown by the parsing thread.
     */
    private volatile Throwable throwable;

    private FastPipedReader pipedReader;

    /**
     * Creates a reader for the text content of the given binary stream with the
     * given document metadata. The given parser is used for the parsing task that
     * is run with the given executor. The given executor <em>must</em> run the
     * parsing task asynchronously in a separate thread, since the current thread
     * must return to the caller that can then consume the parsed text through the
     * {@link Reader} interface.
     * <p>
     * The created reader will be responsible for closing the given stream. The
     * stream and any associated resources will be closed at or before the time when
     * the {@link #close()} method is called on this reader.
     *
     * @param parser
     *            parser instance
     * @param stream
     *            binary stream
     * @param metadata
     *            document metadata
     * @param context
     *            parsing context
     * @throws IOException
     *             if the document can not be parsed
     * @since Apache Tika 0.4
     */
    public ParsingReader(Parser parser, InputStream stream, Metadata metadata, ParseContext context)
            throws IOException {

        this.parser = parser;
        this.stream = TikaInputStream.get(stream);
        this.metadata = metadata;
        this.context = context;

        int timeOutBySize = 0;
        if (this.stream.hasLength()) {
            length = this.stream.getLength();
        } else {
            String lengthStr = metadata.get(Metadata.CONTENT_LENGTH);
            if (lengthStr != null) {
                length = Long.parseLong(lengthStr);
            }
        }
        ParsingTaskConfig parsingConfig = ConfigurationManager.findObject(ParsingTaskConfig.class);
        timeOutBySize = (int) (length / 1000000) * parsingConfig.getTimeOutPerMB();

        pipedReader = new FastPipedReader(128 * 1024, parsingConfig.getTimeOut(), timeOutBySize);
        this.reader = new BufferedReader(pipedReader);
        this.writer = new FastPipedWriter(pipedReader);

        String timeout = metadata.get(IndexerDefaultParser.INDEXER_TIMEOUT);
        String mediaType = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
        if (timeout != null || MediaType.OCTET_STREAM.toString().equals(mediaType)) {
            pipedReader.setTimeoutPaused(true);
        }

        // ForkServer timeout
        context.set(ParsingTimeout.class, new ParsingTimeout(pipedReader.getTotalTimeout() * 1000));

        // Executa parsing em outra JVM, isolando problemas, mas impacta desempenho
        // until proxies for item and itemSearcher are implemented,
        // we do not run parsers that use them in forkParser
        if (MimeTypesProcessingOrder.getProcessingPriority(MediaType.parse(mediaType)) == 0) {
            ((IndexerDefaultParser) parser).setCanUseForkParser(true);
        } else
            ((IndexerDefaultParser) parser).setCanUseForkParser(false);
    }

    public void startBackgroundParsing() {
        future = threadPool.submit(new BackgroundParsing());
    }

    public static ExecutorService threadPool = Executors.newCachedThreadPool(new ParsingThreadFactory());

    private Future<?> future;

    private volatile boolean parseDone = false;

    private Object lock = new Object();

    private static class ParsingThreadFactory implements ThreadFactory {

        private int i;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ParsingThread-" + i++); //$NON-NLS-1$
            t.setDaemon(true);
            return t;
        }

    }

    public static void shutdownTasks() {
        threadPool.shutdownNow();
    }

    public void closeAndInterruptParsingTask() {
        closeAndInterruptParsingTask(true);
    }

    public void closeAndInterruptParsingTask(boolean waitCleanup) {

        try {
            writer.close();
            stream.close();
        } catch (Exception e) {
        }

        // em situações raríssimas essa chamada pode travar,
        // ex: ao ler arquivos proibidos pelo FS, como aux no Windows,
        // então é executada em outra thread por segurança
        threadPool.submit(new Runnable() {
            public void run() {
                future.cancel(true);
            }
        });

        if (waitCleanup)
            try {
                // wait some time to cancel task, kill external process, close file handles, etc
                synchronized (lock) {
                    if (!parseDone)
                        lock.wait(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

    }

    /**
     * The background parsing task.
     */
    private class BackgroundParsing implements Runnable {

        /**
         * Parses the given binary stream and writes the text content to the write end
         * of the pipe. Potential exceptions (including the one caused if the read end
         * is closed unexpectedly) are stored before the input stream is closed and
         * processing is stopped.
         */
        @Override
        public void run() {
            ContentHandler handler = new ToTextContentHandler(writer);
            try {
                parser.parse(stream, handler, metadata, context);

            } catch (CorruptedCarvedException e) {
                ItemInfo itemInfo = context.get(ItemInfo.class);
                String filePath = itemInfo.getPath();
                LOGGER.warn("{} Ignoring corrupted carved file '{}' ({} bytes )\t{}", Thread.currentThread().getName(), //$NON-NLS-1$
                        filePath, length, e.toString());
                throwable = e;
                // e.printStackTrace();

            } catch (OutOfMemoryError t) {
                ItemInfo itemInfo = context.get(ItemInfo.class);
                String filePath = itemInfo.getPath();
                LOGGER.error("{} OutOfMemory processing '{}' ({} bytes )\t{}", Thread.currentThread().getName(), //$NON-NLS-1$
                        filePath, length, t.toString());

            } catch (Throwable t) {
                // t.printStackTrace();
                // Loga outros erros que não sejam de parsing, como OutMemory
                // para não interromper indexação
                // Não loga SAXException pois provavelmente foi devido a
                // timeout, já logado
                if (!(t instanceof SAXException)) {
                    ItemInfo itemInfo = context.get(ItemInfo.class);
                    String filePath = itemInfo.getPath();
                    LOGGER.warn("{} Error processing '{}' ({} bytes )\t{}", Thread.currentThread().getName(), filePath, //$NON-NLS-1$
                            length, t.toString());
                }

            } finally {
                synchronized (lock) {
                    lock.notify();
                    parseDone = true;
                }
            }

            try {
                stream.close();
            } catch (Throwable t) {
                if (throwable == null) {
                    // throwable = t;
                }
            }

            try {
                writer.close();
            } catch (Throwable t) {
                if (throwable == null) {
                    // throwable = t;
                }
            }
        }

    }

    /**
     * Reads parsed text from the pipe connected to the parsing thread. Fails if the
     * parsing thread has thrown an exception.
     *
     * @param cbuf
     *            character buffer
     * @param off
     *            start offset within the buffer
     * @param len
     *            maximum number of characters to read
     * @throws IOException
     *             if the parsing thread has failed or if for some reason the pipe
     *             does not work properly
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {

        int read = reader.read(cbuf, off, len);

        if (throwable != null) {
            if (throwable instanceof IOException) {
                throw (IOException) throwable;
            } else {
                throw new IOException(throwable); // $NON-NLS-1$
            }
        }

        return read;
    }

    /**
     * Closes the read end of the pipe. If the parsing thread is still running, next
     * write to the pipe will fail and cause the thread to stop. Thus there is no
     * need to explicitly terminate the thread.
     *
     * @throws IOException
     *             if the pipe can not be closed
     */
    @Override
    public void close() throws IOException {
        reader.close();
        if (!parseDone) {
            closeAndInterruptParsingTask();
        }
    }

    /**
     * Return the available bytes from the stream being consumed
     *
     */
    public int available() {
        try {
            return stream.available();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean setTimeoutPaused(boolean paused) {
        return pipedReader.setTimeoutPaused(paused);
    }

}
