package iped.engine.task;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.MakePreviewConfig;
import iped.engine.config.ParsingTaskConfig;
import iped.engine.core.QueuesProcessingOrder;
import iped.engine.io.TimeoutException;
import iped.engine.tika.EmptyEmbeddedDocumentExtractor;
import iped.engine.util.ItemInfoFactory;
import iped.engine.util.Util;
import iped.parsers.fork.ParsingTimeout;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.ToCSVContentHandler;
import iped.parsers.util.ToXMLContentHandler;
import iped.search.IItemSearcher;
import iped.utils.IOUtil;
import iped.viewers.HtmlLinkViewer;

public class MakePreviewTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(MakePreviewTask.class);

    public static final String viewFolder = "view"; //$NON-NLS-1$

    private MakePreviewConfig previewConfig;

    private StandardParser parser;

    private volatile Throwable exception;

    public List<Configurable<?>> getConfigurables() {
        MakePreviewConfig result = ConfigurationManager.get().findObject(MakePreviewConfig.class);
        if(result == null) {
            result = new MakePreviewConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        previewConfig = configurationManager.findObject(MakePreviewConfig.class);

        parser = new StandardParser();
        parser.setPrintMetadata(false);
        parser.setIgnoreStyle(false);
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
            LOGGER.warn("Error generating preview of {} ({} bytes) {}", evidence.getPath(), evidence.getLength(), //$NON-NLS-1$
                    e.toString());
            LOGGER.debug("", e);
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
            context.set(IItemReader.class, evidence);
            context.set(ItemInfo.class, ItemInfoFactory.getItemInfo(evidence));
            context.set(EmbeddedDocumentExtractor.class, new EmptyEmbeddedDocumentExtractor());

            ParsingTaskConfig parsingConfig = ConfigurationManager.get().findObject(ParsingTaskConfig.class);

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

            if (QueuesProcessingOrder.getProcessingQueue(evidence.getMediaType()) == 0) {
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
