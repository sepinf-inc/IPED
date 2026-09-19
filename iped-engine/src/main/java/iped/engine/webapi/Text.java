package iped.engine.webapi;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDSource;
import iped.engine.task.ParsingTask;
import iped.parsers.standard.StandardParser;

@Api(value = "Documents")
@Path("/sources/{sourceID}/docs/{id}/text")
public class Text {

    @ApiOperation(value = "Get document's content converted as text")
    @GET
    @Produces(MediaType.TEXT_PLAIN + "; charset=UTF-8")
    public static StreamingOutput content(@PathParam("sourceID") String sourceID, @PathParam("id") int id)
            throws Exception {

        IIPEDSource source = Sources.getSource(sourceID);
        final IItem item = source.getItemByID(id);

        // P0-1: usa o cache de texto extraido em vez de re-parsear com Tika.
        // R4-2 (feedback PR #2961): streama via getTextReader() em vez do
        // @Deprecated getParsedTextCache(), que truncava o texto a
        // 10.000.000 chars. Reader null ou vazio (item sem texto em cache)
        // mantem integralmente o fallback Tika abaixo (comportamento de erro
        // atual para itens sem texto preservado).
        Reader reader = null;
        char[] firstChunk = null;
        int firstChunkLength = 0;
        try {
            reader = item.getTextReader();
            if (reader != null) {
                char[] buffer = new char[8192];
                firstChunkLength = reader.read(buffer);
                if (firstChunkLength <= 0) {
                    // cache presente porem vazio: trata como item sem texto em cache
                    reader.close();
                    reader = null;
                } else {
                    firstChunk = buffer;
                }
            }
        } catch (IOException e) {
            // cache ilegivel: mantem o comportamento anterior (fallback Tika)
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
                reader = null;
            }
        }
        if (reader != null) {
            final Reader textReader = reader;
            final char[] prefix = firstChunk;
            final int prefixLength = firstChunkLength;
            return new StreamingOutput() {
                @Override
                public void write(OutputStream os) throws IOException, WebApplicationException {
                    // copia em buffer de 8KB, sem materializar a String inteira;
                    // falha do reader aqui interrompe o stream (200 ja iniciado)
                    try (Reader r = textReader) {
                        OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                        writer.write(prefix, 0, prefixLength);
                        char[] cbuf = new char[8192];
                        int len;
                        while ((len = r.read(cbuf)) != -1) {
                            writer.write(cbuf, 0, len);
                        }
                        writer.flush();
                    }
                }
            };
        }

        // Fallback: re-parseia com Tika se o cache estiver vazio
        final StandardParser parser = new StandardParser();
        final ParseContext context = getTikaContext(item, parser, (IPEDSource) source);
        final Metadata metadata = new Metadata();

        ParsingTask.fillMetadata(item, metadata);
        parser.setPrintMetadata(false);

        return new StreamingOutput() {
            @Override
            public void write(OutputStream arg0) throws IOException, WebApplicationException {
                ContentHandler handler = new ToTextContentHandler(arg0, "UTF-8");
                try (TikaInputStream is = item.getTikaStream()) {
                    parser.parse(is, handler, metadata, context);
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }
        };
    }

    /**
     * Reads the item plain text through the SAME acquisition path used by
     * {@link #content(String, int)} (P0-1/R4-2): getTextReader() when
     * available, falling back to the on-demand Tika parse otherwise. Reading
     * stops as soon as capChars characters are produced (design 07c
     * SCAN_CAP budget), so snippet requests never materialize huge documents
     * in memory. Returns the raw (not whitespace-normalized) text, possibly
     * empty. A fallback parse that produced no characters at all throws to
     * the caller, which must treat the item as "no snippet" (design 07c edge
     * case 5: /search never answers 500); a parse that produced characters
     * and then threw (e.g. StandardParser's metadata post-processing in a
     * broken-OCR environment) delivers what it produced, exactly as /text
     * does with bytes already streamed to the response.
     */
    static String readPlainText(IItem item, IIPEDSource source, int capChars) throws Exception {
        StringBuilder text = new StringBuilder(Math.min(capChars, 16384));
        Reader reader = null;
        try {
            reader = item.getTextReader();
        } catch (IOException e) {
            // cache ilegivel: mesmo fallback Tika do /text
            reader = null;
        }
        if (reader != null) {
            try (Reader r = reader) {
                char[] buffer = new char[8192];
                int len;
                while (text.length() < capChars && (len = r.read(buffer)) != -1) {
                    text.append(buffer, 0, Math.min(len, capChars - text.length()));
                }
            } catch (IOException e) {
                if (text.length() != 0) {
                    // texto parcial nao e' snippet confiavel: propaga (omissao)
                    throw e;
                }
                // falha antes de qualquer caractere: cai no fallback Tika
            }
            if (text.length() > 0) {
                return text.toString();
            }
        }
        // Fallback: mesmo parse Tika on-demand do /text, abortado assim que o
        // cap e' atingido (WriteLimitReachedException e' o stop signal da
        // propria Tika, tratado graciosamente pela cadeia de parsers).
        StandardParser parser = new StandardParser();
        ParseContext context = getTikaContext(item, parser, (IPEDSource) source);
        Metadata metadata = new Metadata();
        ParsingTask.fillMetadata(item, metadata);
        parser.setPrintMetadata(false);
        CapWriter writer = new CapWriter(text, capChars);
        try (TikaInputStream is = item.getTikaStream()) {
            ContentHandler handler = new ToTextContentHandler(writer);
            parser.parse(is, handler, metadata, context);
        } catch (Exception e) {
            // Symmetry with /text: content already streamed to the client is
            // delivered even when StandardParser's finally-block post-processing
            // (MetadataUtil.normalizeMetadata) throws after the content handler
            // finished. Only a parse that produced NO characters at all is a
            // real extraction failure (design 07c edge case 5: omitted key);
            // reaching the cap is a normal expected abort (writer.isCapped()).
            if (text.length() == 0 && !writer.isCapped()) {
                throw new IOException("snippet text extraction failed for item", e);
            }
        }
        return text.toString();
    }

    /**
     * Writer that appends to a StringBuilder and aborts the surrounding Tika
     * extraction (WriteLimitReachedException) as soon as cap characters were
     * produced, never exceeding the cap.
     */
    private static class CapWriter extends Writer {

        private final StringBuilder out;
        private final int cap;
        private boolean capped = false;

        CapWriter(StringBuilder out, int cap) {
            this.out = out;
            this.cap = cap;
        }

        boolean isCapped() {
            return capped;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            int room = cap - out.length();
            if (len >= room) {
                if (room > 0) {
                    out.append(cbuf, off, room);
                }
                capped = true;
                // Writer.write so pode lancar IOException; a causa WLR mantém
                // o sinal graca de stop da Tika (isWriteLimitReached varre a
                // cadeia de causas nos parsers que a tratam).
                throw new IOException(new WriteLimitReachedException(cap));
            }
            out.append(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void close() throws IOException {
        }
    }

    public static ParseContext getTikaContext(IItem item, Parser parser, IPEDSource source) throws Exception {
        ParsingTask expander = new ParsingTask(item, (StandardParser) parser);
        expander.init(ConfigurationManager.get());
        ParseContext context = expander.getTikaContext(source);
        expander.setExtractEmbedded(false);
        return context;
    }

}
