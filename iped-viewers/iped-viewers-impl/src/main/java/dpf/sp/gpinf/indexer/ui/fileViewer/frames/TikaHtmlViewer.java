package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

import dpf.sp.gpinf.indexer.parsers.util.ToXMLContentHandler;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.io.IStreamSource;

/**
 * Visualizador para versão Html dos arquivos gerados pelos parsers do Tika.
 * Somente os tipos cujo Html seja satisfatório devem ser configurados.
 */
public class TikaHtmlViewer extends HtmlViewer {

    private Parser parser = new AutoDetectParser();

    @Override
    public String getName() {
        return "TikaHtml"; //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("application/x-msaccess") //$NON-NLS-1$
                || contentType.equals("application/x-sqlite3") //$NON-NLS-1$
                || contentType.equals("application/sqlite-skype") //$NON-NLS-1$
                || contentType.equals("application/x-emule") //$NON-NLS-1$
                || contentType.equals("application/x-ares-galaxy") //$NON-NLS-1$
                || contentType.equals("application/x-shareaza-library-dat"); //$NON-NLS-1$
    }

    @Override
    public void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms) {

        if (content != null) {
            try {
                content = new FileContentSource(getHtmlVersion(content.getSeekableInputStream(), contentType));
            } catch (IOException e) {
                e.printStackTrace();
                content = null;
            }
        }

        super.loadFile(content, highlightTerms);
    }
    /*
     * TODO: Gerar preview html em outra thread, senão pode travar a interface ao
     * trocar de abas no visualizador caso a geração do preview seja lenta.
     */

    private File getHtmlVersion(InputStream in, String contentType) {

        File outFile = null;
        TikaInputStream tis = null;
        try {
            tis = TikaInputStream.get(in);
            outFile = File.createTempFile("tmp", ".html"); //$NON-NLS-1$ //$NON-NLS-2$
            outFile.deleteOnExit();

            generateHtmlPreview(tis, outFile, contentType);

        } catch (Exception e) {
            e.printStackTrace();
            return null;

        } finally {
            IOUtil.closeQuietly(tis);
        }
        return outFile;
    }

    public void generateHtmlPreview(TikaInputStream tis, File outFile, String contentType) {
        BufferedOutputStream outStream = null;
        try {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType);
            ParseContext context = new ParseContext();
            // Habilita parsing de subitens embutidos, o que ficaria ruim no preview de
            // certos arquivos
            // Ex: Como renderizar no preview html um PDF embutido num banco de dados?
            // context.set(Parser.class, parser);
            outStream = new BufferedOutputStream(new FileOutputStream(outFile));
            ToXMLContentHandler handler = new ToXMLContentHandler(outStream, "UTF-8"); //$NON-NLS-1$
            parser.parse(tis, handler, metadata, context);

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            IOUtil.closeQuietly(outStream);
        }
    }

}
