package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.fork.ParsingTimeout;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.MakePreviewConfig;
import dpf.sp.gpinf.indexer.config.ParsingTaskConfig;
import dpf.sp.gpinf.indexer.io.TimeoutException;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.ToCSVContentHandler;
import dpf.sp.gpinf.indexer.parsers.util.ToXMLContentHandler;
import dpf.sp.gpinf.indexer.process.MimeTypesProcessingOrder;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlLinkViewer;
import dpf.sp.gpinf.indexer.util.EmptyEmbeddedDocumentExtractor;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import macee.core.Configurable;

public class MakePreviewTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(MakePreviewTask.class);

    public static final String viewFolder = "view"; //$NON-NLS-1$

    private MakePreviewConfig previewConfig;

    private IndexerDefaultParser parser = new IndexerDefaultParser();

    private volatile Throwable exception;

    public MakePreviewTask() {
        parser.setPrintMetadata(false);
        parser.setIgnoreStyle(false);
    }

    public List<Configurable> getConfigurables() {
        return Arrays.asList(new MakePreviewConfig());
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        previewConfig = ConfigurationManager.findObject(MakePreviewConfig.class);
    }

    @Override
    public void finish() throws Exception {
    }

    public boolean isSupportedType(String contentType) {
        return previewConfig.getSupportedMimes().contains(contentType) || mayContainLinks(contentType)
                || isSupportedTypeCSV(contentType);
    }

    private boolean mayContainLinks(String contentType) {
        return previewConfig.getSupportedMimesWithLinks().contains(contentType);
    }

    private boolean isSupportedTypeCSV(String contentType) {
        return false;// contentType.equals("application/x-shareaza-library-dat");
    }

    @Override
    public boolean isEnabled() {
        return previewConfig.isEnabled();
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        String mediaType = evidence.getMediaType().toString();
        if (evidence.getLength() == Long.valueOf(0) || evidence.getHash() == null || evidence.getHash().isEmpty()
                || !isSupportedType(mediaType) || !evidence.isToAddToCase()) {
            return;
        }

        String ext = "html"; //$NON-NLS-1$
        if (isSupportedTypeCSV(mediaType)) {
            ext = "csv"; //$NON-NLS-1$
        }

        File viewFile = Util.getFileFromHash(new File(output, viewFolder), evidence.getHash(), ext);

        if (viewFile.exists()) {
            evidence.setViewFile(viewFile);
            return;
        }

        if (!viewFile.getParentFile().exists()) {
            viewFile.getParentFile().mkdirs();
        }

        try {
            LOGGER.debug("Generating preview of {} ({} bytes)", evidence.getPath(), evidence.getLength());
            makeHtmlPreview(evidence, viewFile, mediaType);
            evidence.setViewFile(viewFile);

        } catch (Throwable e) {
            Log.warning(this.getClass().getSimpleName(), "Error processing " + evidence.getPath() + " " + e.toString()); //$NON-NLS-1$//$NON-NLS-2$

        }

    }

    private void makeHtmlPreview(IItem evidence, File outFile, String mediaType) throws Throwable {
        BufferedOutputStream outStream = null;
        try {
            final Metadata metadata = new Metadata();
            ParsingTask.fillMetadata(evidence, metadata);

            // Não é necessário fechar tis pois será fechado em evidence.dispose()
            final TikaInputStream tis = evidence.getTikaStream();

            final ParseContext context = new ParseContext();
            IItemSearcher itemSearcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
            context.set(IItemSearcher.class, itemSearcher);
            context.set(IItemBase.class, evidence);
            context.set(ItemInfo.class, ItemInfoFactory.getItemInfo(evidence));
            context.set(EmbeddedDocumentExtractor.class, new EmptyEmbeddedDocumentExtractor());

            ParsingTaskConfig parsingConfig = ConfigurationManager.findObject(ParsingTaskConfig.class);

            // ForkServer timeout
            if (evidence.getLength() != null) {
                int timeOutBySize = (int) (evidence.getLength() / 1000000) * parsingConfig.getTimeOutPerMB();
                int totalTimeout = (parsingConfig.getTimeOut() + timeOutBySize) * 1000;
                context.set(ParsingTimeout.class, new ParsingTimeout(totalTimeout));
            }

            // Habilita parsing de subitens embutidos, o que ficaria ruim no preview de
            // certos arquivos
            // Ex: Como renderizar no preview html um PDF embutido num banco de dados?
            // context.set(Parser.class, parser);

            outStream = new BufferedOutputStream(new FileOutputStream(outFile));

            ContentHandler handler;
            if (!isSupportedTypeCSV(evidence.getMediaType().toString())) {
                String comment = null;
                if (mayContainLinks(mediaType))
                    comment = HtmlLinkViewer.PREVIEW_WITH_LINKS_HEADER;
                handler = new ToXMLContentHandlerWithComment(outStream, "UTF-8", comment); //$NON-NLS-1$
            } else {
                handler = new ToCSVContentHandler(outStream, "UTF-8"); //$NON-NLS-1$
            }
            final ProgressContentHandler pch = new ProgressContentHandler(handler);

            if (MimeTypesProcessingOrder.getProcessingPriority(evidence.getMediaType()) == 0) {
                parser.setCanUseForkParser(true);
            } else
                parser.setCanUseForkParser(false);

            exception = null;
            Thread t = new Thread(Thread.currentThread().getName() + "-MakePreviewThread") { //$NON-NLS-1$
                @Override
                public void run() {
                    try {
                        parser.parse(tis, pch, metadata, context);

                    } catch (IOException | SAXException | TikaException | OutOfMemoryError e) {
                        exception = e;
                    }
                }
            };
            t.start();

            long start = System.currentTimeMillis();
            while (t.isAlive()) {
                if (pch.getProgress())
                    start = System.currentTimeMillis();

                if ((System.currentTimeMillis() - start) / 1000 >= parsingConfig.getTimeOut()) {
                    t.interrupt();
                    stats.incTimeouts();
                    throw new TimeoutException();
                }
                t.join(1000);
                if (exception != null)
                    throw exception;
            }

        } finally {
            IOUtil.closeQuietly(outStream);
        }
    }

    private class ToXMLContentHandlerWithComment extends ToXMLContentHandler {

        private String comment;

        public ToXMLContentHandlerWithComment(OutputStream stream, String encoding, String comment)
                throws UnsupportedEncodingException {
            super(stream, encoding);
            this.comment = comment;
        }

        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            if (comment != null)
                this.write(comment + "\n"); //$NON-NLS-1$
        }
    }

    public class ProgressContentHandler extends ContentHandlerDecorator {

        private volatile boolean progress = false;

        public ProgressContentHandler(ContentHandler handler) {
            super(handler);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            progress = true;
            super.characters(ch, start, length);
        }

        public boolean getProgress() {
            if (progress) {
                progress = false;
                return true;
            }
            return false;
        }

    }

}
