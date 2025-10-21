package iped.engine.task;

import java.io.File;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.StringUtils;
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
import iped.engine.preview.PreviewConstants;
import iped.engine.preview.PreviewKey;
import iped.engine.preview.PreviewRepository;
import iped.engine.preview.PreviewRepositoryManager;
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
import iped.utils.LockManager;
import iped.viewers.HtmlLinkViewer;

public class MakePreviewTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(MakePreviewTask.class);

    private MakePreviewConfig previewConfig;

    private StandardParser parser;

    private static LockManager<PreviewKey> lockManager;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new MakePreviewConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        previewConfig = configurationManager.findObject(MakePreviewConfig.class);

        parser = new StandardParser();
        parser.setPrintMetadata(false);
        parser.setIgnoreStyle(false);

        initLockManager();
    }

    private static synchronized void initLockManager() {
        if (lockManager == null) {
            lockManager = new LockManager<>();
        }
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
        if (evidence.getLength() == Long.valueOf(0) || !isSupportedType(mediaType) || !evidence.isToAddToCase()) {
            return;
        }

        String ext = "html"; //$NON-NLS-1$
        if (isSupportedTypeCSV(mediaType)) {
            ext = "csv"; //$NON-NLS-1$
        }

        PreviewKey key = PreviewKey.create(evidence);
        ReentrantLock lock = lockManager.getLock(key);
        lock.lock();
        try {
            // skip if evidence already has preview
            if (evidence.hasPreview() && PreviewRepositoryManager.get(output).previewExists(evidence) && ext.equals(evidence.getPreviewExt())) {
                return;
            }
            if (evidence.getViewFile() != null && evidence.getViewFile().exists() && StringUtils.isNotBlank(evidence.getHash())
                    && evidence.getViewFile().equals(Util.getFileFromHash(new File(output, PreviewConstants.VIEW_FOLDER_NAME), evidence.getHash(), ext))) {
                return;
            }

            LOGGER.debug("Generating preview of {} ({} bytes)", evidence.getPath(), evidence.getLength());
            makeHtmlPreviewAndStore(evidence, mediaType, ext);

        } catch (Throwable e) {
            LOGGER.warn("Error generating preview of {} ({} bytes) {}", evidence.getPath(), evidence.getLength(), //$NON-NLS-1$
                    e.toString());
            LOGGER.debug("", e);
        } finally {
            lock.unlock();
        }

    }

    private void makeHtmlPreviewAndStore(IItem evidence, String mediaType, String viewExt) throws Throwable {

        PreviewRepository previewRepo = PreviewRepositoryManager.get(output);
        if (previewRepo.previewExists(evidence)) {
            evidence.setHasPreview(true);
            evidence.setPreviewExt(viewExt);
            return;
        }

        PipedInputStream inputStream = new PipedInputStream(8192);
        PipedOutputStream outputStream = new PipedOutputStream(inputStream);

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

        ContentHandler handler;
        if (!isSupportedTypeCSV(evidence.getMediaType().toString())) {
            String comment = null;
            if (mayContainLinks(mediaType)) {
                comment = HtmlLinkViewer.PREVIEW_WITH_LINKS_HEADER;
            }
            handler = new ToXMLContentHandlerWithComment(outputStream, "UTF-8", comment); //$NON-NLS-1$
        } else {
            handler = new ToCSVContentHandler(outputStream, "UTF-8"); //$NON-NLS-1$
        }
        final ProgressContentHandler pch = new ProgressContentHandler(handler);

        if (QueuesProcessingOrder.getProcessingQueue(evidence.getMediaType()) == 0) {
            parser.setCanUseForkParser(true);
        } else {
            parser.setCanUseForkParser(false);
        }

        final CountDownLatch latch = new CountDownLatch(2); // latch for 2 threads
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        Thread producerThread = new Thread(Thread.currentThread().getName() + "-MakePreviewThread-Producer") {
            @Override
            public void run() {
                try {
                    parser.parse(tis, pch, metadata, context);

                } catch (Throwable e) {
                    exception.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                    IOUtil.closeQuietly(outputStream);
                }
            }
        };

        Thread consumerThread = new Thread(Thread.currentThread().getName() + "-MakePreviewThread-Consumer") {
            @Override
            public void run() {
                try {
                    PreviewRepositoryManager.get(output).storeRawPreview(evidence, inputStream);
                    evidence.setHasPreview(true);
                    evidence.setPreviewExt(viewExt);
                } catch (Throwable e) {
                    exception.compareAndSet(null, e);
                    LOGGER.info("ERROR {} {}", evidence.getHash(), e.getMessage() );

                } finally {
                    latch.countDown();
                    IOUtil.closeQuietly(inputStream);
                }
            }
        };

        producerThread.start();
        consumerThread.start();

        long start = System.currentTimeMillis();
        while (latch.getCount() > 0) {
            if (pch.getProgress()) {
                start = System.currentTimeMillis();
            }

            if ((System.currentTimeMillis() - start) / 1000 >= parsingConfig.getTimeOut()) {
                producerThread.interrupt();
                consumerThread.interrupt();
                stats.incTimeouts();
                throw new TimeoutException();
            }
            latch.await(1000, TimeUnit.MILLISECONDS);
            if (exception.get() != null) {
                throw exception.get();
            }
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
            if (comment != null) {
                this.write(comment + "\n"); //$NON-NLS-1$
            }
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
