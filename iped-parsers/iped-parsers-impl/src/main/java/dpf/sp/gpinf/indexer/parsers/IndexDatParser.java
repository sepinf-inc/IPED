/*
 * Copyright 2012-2016, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.parsers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.RegistryParser.ContainerVolatile;
import dpf.sp.gpinf.indexer.parsers.util.Util;

/**
 * Parser para históricos index.dat do Internet Explorer 9 e anteriores. Utiliza
 * a libmsiecf para o parsing via execução de processo auxiliar.
 * 
 * @author Nassif
 *
 */
public class IndexDatParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static Logger LOGGER = LoggerFactory.getLogger(IndexDatParser.class);

    private static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-msie-cache")); //$NON-NLS-1$
    private static final String TOOL_NAME = "msiecfexport"; //$NON-NLS-1$
    private static boolean tested = false;

    public static final String TOOL_PATH_PROP = TOOL_NAME + ".path"; //$NON-NLS-1$
    private String TOOL_PATH = System.getProperty(TOOL_PATH_PROP, ""); //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        if (tested)
            return SUPPORTED_TYPES;

        synchronized (this.getClass()) {
            if (!tested)
                try {
                    String[] cmd = { TOOL_PATH + TOOL_NAME, "-V" }; //$NON-NLS-1$
                    Process p = Runtime.getRuntime().exec(cmd);
                    Util.ignoreStream(p.getErrorStream());
                    Util.ignoreStream(p.getInputStream());
                    if (p.waitFor() != 0)
                        throw new Exception();

                } catch (Exception e) {
                    LOGGER.error("Error testing msiecfexport (libmsiecf): index.dat files will NOT be parsed"); //$NON-NLS-1$
                    SUPPORTED_TYPES = Collections.emptySet();
                }
            tested = true;
        }

        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("pre"); //$NON-NLS-1$

        TemporaryResources tmp = new TemporaryResources();
        Process p = null;
        Thread readThread = null;
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            File file = tis.getFile();

            String[] cmd = { TOOL_PATH + TOOL_NAME, "-m", "all", file.getAbsolutePath() }; //$NON-NLS-1$ //$NON-NLS-2$
            ProcessBuilder pb = new ProcessBuilder(cmd);
            p = pb.start();
            Util.ignoreStream(p.getErrorStream());

            InputStream is = p.getInputStream();
            byte[] data = new byte[64 * 1024];
            BytesRead read = new BytesRead();

            readThread = readStream(is, data, read);

            while (true) {
                synchronized (read) {
                    while (read.value == null)
                        read.wait();
                    if (read.value == -1)
                        break;
                }

                byte[] out = new byte[read.value];
                System.arraycopy(data, 0, out, 0, read.value);

                synchronized (read) {
                    read.value = null;
                    read.notify();
                }
                String str = Util.decodeUnknowCharset(out);
                xhtml.characters(str);

                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
            }

            if (p.waitFor() != 0)
                throw new TikaException(TOOL_NAME + " terminated with error code " + p.exitValue()); //$NON-NLS-1$

        } catch (InterruptedException e) {
            throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

        } finally {
            if (p != null)
                p.destroyForcibly();
            if (readThread != null)
                readThread.interrupt();
            tmp.close();

            xhtml.endElement("pre"); //$NON-NLS-1$
            xhtml.endDocument();
        }

    }

    private Thread readStream(final InputStream stream, final byte[] out, final BytesRead read) {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                        int i = stream.read(out);
                        synchronized (read) {
                            read.value = i;
                            read.notify();
                            if (read.value == -1)
                                break;
                            while (read.value != null)
                                read.wait();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();

        return t;
    }

    class BytesRead {
        Integer value = null;
    }

}
