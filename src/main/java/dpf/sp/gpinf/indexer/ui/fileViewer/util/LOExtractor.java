package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.SwingUtilities;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;
import dpf.sp.gpinf.indexer.util.IOUtil;

/**
 *
 * Classe para descompactar o aplicativo LibreOffice
 *
 */
public class LOExtractor extends CancelableWorker implements EmbeddedDocumentExtractor {

    private Parser parser = new AutoDetectParser();
    private File output, input;
    private CancelableWorker worker;
    private ProgressDialog progressMonitor;
    private int progress = 0, numSubitens = 6419;// TODO obter número de itens automaticamente
    private volatile boolean completed = false;

    public LOExtractor(String input, String output) {
        this.output = new File(output);
        this.input = new File(input);
        this.worker = this;
    }

    public boolean decompressLO() {

        try {
            if (output.exists()) {
                if (IOUtil.countSubFiles(output) >= numSubitens) {
                    return true;
                } else {
                    IOUtil.deletarDiretorio(output);
                }
            }

            if (input.exists()) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressMonitor = new ProgressDialog(null, worker);
                        progressMonitor.setMaximum(numSubitens);
                        progressMonitor.setNote("Descompactando LibreOffice...");
                    }
                });

                ParseContext context = new ParseContext();
                context.set(EmbeddedDocumentExtractor.class, this);
                parser.parse(new FileInputStream(input), new ToTextContentHandler(), new Metadata(), context);

                if (!progressMonitor.isCanceled()) {
                    progressMonitor.close();
                }

                if (isCompleted()) {
                    return true;
                } else {
                    IOUtil.deletarDiretorio(output);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;

    }

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        return !this.isCancelled();
    }

    @Override
    public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

        String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
        File outputFile = new File(output, name);
        File parent = outputFile.getParentFile();

        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("unable to create directory \"" + parent + "\"");
            }
        }

        if (metadata.get(ExtraProperties.EMBEDDED_FOLDER) != null) {
            return;
        }

        // System.out.println("Extracting '"+name+" to " + outputFile);
        FileOutputStream os = new FileOutputStream(outputFile);
        IOUtils.copy(inputStream, os);
        os.close();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressMonitor.setProgress(++progress);
                if (progress == numSubitens) {
                    completed = true;
                }
            }
        });

    }

    @Override
    protected Void doInBackground() throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isCompleted() {
        return completed;
    }

}
