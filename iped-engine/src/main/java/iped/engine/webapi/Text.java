package iped.engine.webapi;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

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

    public static ParseContext getTikaContext(IItem item, Parser parser, IPEDSource source) throws Exception {
        ParsingTask expander = new ParsingTask(item, (StandardParser) parser);
        expander.init(ConfigurationManager.get());
        ParseContext context = expander.getTikaContext(source);
        expander.setExtractEmbedded(false);
        return context;
    }

}
