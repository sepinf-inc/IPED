package iped.engine.webapi;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDSource;
import iped.engine.task.ParsingTask;
import iped.parsers.standard.StandardParser;

@Api(value = "Documents")
@Path("/sources/{sourceID}/docs/{id}/text")
public class Text {

    /** Sentinel for an absent {@code maxChars} parameter: no upper limit. */
    static final long NO_MAX_CHARS = -1L;

    @ApiOperation(value = "Get document's content converted as text")
    @ApiResponses({
        @ApiResponse(code = 400, message = "invalid offset or maxChars parameter")
    })
    @GET
    @Produces(MediaType.TEXT_PLAIN + "; charset=UTF-8")
    public static StreamingOutput content(@PathParam("sourceID") String sourceID, @PathParam("id") int id,
            @DefaultValue("0")
            @ApiParam(value = "Number of leading characters (Unicode code points) of the extracted text to skip. 0 or greater; omit defaults to 0. An offset at or beyond the text length returns an empty 200 body. 400 if invalid.")
            @QueryParam("offset") String offsetParam,
            @ApiParam(value = "Maximum number of characters (Unicode code points) to return, 1 or greater. Omit for no limit (0 is rejected, not treated as absent). This is a text window, not an HTTP Range: status stays 200 and no Content-Range header is sent. 400 if invalid.")
            @QueryParam("maxChars") String maxCharsParam)
            throws Exception {

        IIPEDSource source = Sources.getSource(sourceID);

        // Range de leitura (design 07d, F-4): validado depois do 404 de
        // sourceID e ANTES de getItemByID e de qualquer extracao, de modo que
        // um parametro malformado responde 400 text/plain mesmo em itens cuja
        // extracao daria 500 (AC5; ordem 404 -> 400 das familias 06b/07c).
        final long offset;
        final long maxChars;
        try {
            offset = parseOffsetParam(offsetParam);
            maxChars = parseMaxCharsParam(maxCharsParam);
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
        // AC1/D8: sem range pedido, os StreamingOutput abaixo rodam o mesmo
        // codigo literal de antes, sem nenhum wrapper (corpo byte-identico).
        final boolean ranged = offset != 0 || maxChars != NO_MAX_CHARS;

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
                        // range (design 07d): slice por codepoints sobre os
                        // bytes UTF-8 produzidos; D7: o texto continua lido
                        // por inteiro, apenas a saida e' janelada.
                        CodePointRangeStream range = ranged
                                ? new CodePointRangeStream(os, offset, maxChars) : null;
                        OutputStreamWriter writer = new OutputStreamWriter(
                                range != null ? range : os, StandardCharsets.UTF_8);
                        writer.write(prefix, 0, prefixLength);
                        char[] cbuf = new char[8192];
                        int len;
                        while ((len = r.read(cbuf)) != -1) {
                            writer.write(cbuf, 0, len);
                        }
                        writer.flush();
                        if (range != null) {
                            range.finish(); // emite o ultimo codepoint retido
                        }
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
                // range (design 07d): mesmo filtro de codepoints do caminho
                // streaming sobre a saida do parse (D3); o parse em si nao
                // muda e consome o documento inteiro (D7).
                CodePointRangeStream range = ranged
                        ? new CodePointRangeStream(arg0, offset, maxChars) : null;
                ContentHandler handler = new ToTextContentHandler(range != null ? range : arg0, "UTF-8");
                try (TikaInputStream is = item.getTikaStream()) {
                    parser.parse(is, handler, metadata, context);
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
                if (range != null) {
                    range.finish(); // emite o ultimo codepoint retido
                }
            }
        };
    }

    /**
     * Parses the {@code offset} query parameter (design 07d): decimal digits
     * only ({@code ^[0-9]+$}), value &ge; 0. Absent (null, via
     * {@code @DefaultValue("0")}) means 0; an empty string (a bare
     * {@code ?offset=} delivers {@code ""} to Jersey, not the default), a
     * sign, a decimal point, scientific notation or a value that does not fit
     * in a {@code long} are malformed and rejected.
     *
     * @throws IllegalArgumentException with a message naming the parameter
     *                                  (mapped to 400 text/plain by
     *                                  {@link #content})
     */
    static long parseOffsetParam(String value) {
        if (value == null) {
            return 0;
        }
        return parseUnsignedLong(value, "offset", 0);
    }

    /**
     * Parses the {@code maxChars} query parameter (design 07d): decimal digits
     * only, value &ge; 1. Absent (null) means no limit
     * ({@link #NO_MAX_CHARS}); {@code 0} is a client bug (absence already
     * expresses "no limit", precedent 07a {@code limit=0} &rarr; 400) and is
     * rejected, as is any other malformed value (see
     * {@link #parseOffsetParam(String)} for the accepted syntax).
     *
     * @throws IllegalArgumentException with a message naming the parameter
     *                                  (mapped to 400 text/plain by
     *                                  {@link #content})
     */
    static long parseMaxCharsParam(String value) {
        if (value == null) {
            return NO_MAX_CHARS;
        }
        return parseUnsignedLong(value, "maxChars", 1);
    }

    private static long parseUnsignedLong(String value, String name, long minimum) {
        String message = "invalid " + name + " value '" + value + "' (expected "
                + minimum + " or greater, digits only)";
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException(message);
        }
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
        if (parsed < minimum) {
            throw new IllegalArgumentException(message);
        }
        return parsed;
    }

    private static WebApplicationException badRequest(String message) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity(message)
                .build());
    }

    /**
     * Reads the item plain text through the SAME acquisition path used by
     * {@link #content(String, int, String, String)} (P0-1/R4-2): getTextReader() when
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

    /**
     * OutputStream filter that emits the half-open code point window
     * {@code [offset, offset + maxChars)} of the UTF-8 bytes written through
     * it (design 07d, F-4; {@code maxChars < 0} means no limit).
     *
     * <p>Code points are counted at UTF-8 sequence boundaries: a byte is a
     * continuation byte when {@code (b & 0xC0) == 0x80}, every other byte
     * starts a new sequence. The bytes of the sequence currently being
     * received are held back in a minimum-size buffer (at most 3 bytes
     * straddling a write boundary, risk R1) and only ever written to the
     * underlying stream once the sequence is complete, so a cut between two
     * code points can never split a multi-byte sequence, an astral code point
     * (surrogate pair, e.g. emoji) or emit invalid UTF-8. After the window
     * ends the filter just DISCARDS the remaining input until the producer
     * finishes (D3/D7: no early exception, no early close, the underlying
     * text keeps being produced in full). The {@code offset + maxChars} end
     * of the window is computed with saturating arithmetic (edge case 6).</p>
     *
     * <p>{@link #finish()} must be called once when the producer is done, so
     * the final held-back code point (which no following lead byte ever
     * completes) reaches the output. {@link #close()} implies
     * {@link #finish()} and closes the underlying stream.</p>
     */
    static class CodePointRangeStream extends FilterOutputStream {

        private final long start;
        private final long end;
        private long seen = 0;
        private final byte[] pending = new byte[4];
        private int pendingLength = 0;
        private boolean finished = false;

        CodePointRangeStream(OutputStream out, long offset, long maxChars) {
            super(out);
            this.start = offset;
            this.end = maxChars < 0 ? Long.MAX_VALUE
                    : (offset > Long.MAX_VALUE - maxChars ? Long.MAX_VALUE : offset + maxChars);
        }

        @Override
        public void write(int b) throws IOException {
            byte[] single = new byte[] { (byte) b };
            write(single, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                byte current = b[i];
                if (pendingLength > 0 && (current & 0xC0) == 0x80) {
                    // continuation byte: extends the code point held back in
                    // the pending buffer (bytes beyond the 4th of an invalid
                    // sequence are dropped defensively)
                    if (pendingLength < pending.length) {
                        pending[pendingLength++] = current;
                    }
                    continue;
                }
                // lead byte (or a stray continuation at the stream start):
                // whatever sequence was pending is now complete
                emitPending();
                pending[0] = current;
                pendingLength = 1;
            }
        }

        /** Completes the last held-back code point; call once at end of input. */
        void finish() throws IOException {
            if (finished) {
                return;
            }
            finished = true;
            emitPending();
            out.flush();
        }

        @Override
        public void flush() throws IOException {
            // never pushes an incomplete sequence down; just forwards
            out.flush();
        }

        @Override
        public void close() throws IOException {
            finish();
            out.close();
        }

        private void emitPending() throws IOException {
            if (pendingLength == 0) {
                return;
            }
            seen++;
            if (seen > start && seen <= end) {
                out.write(pending, 0, pendingLength);
            }
            pendingLength = 0;
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
