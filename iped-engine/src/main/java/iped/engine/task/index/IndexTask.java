package iped.engine.task.index;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.IndexOptions;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;
import iped.engine.core.Worker.STATE;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.io.CloseFilterReader;
import iped.engine.io.FragmentingReader;
import iped.engine.io.ParsingReader;
import iped.engine.task.AbstractTask;
import iped.engine.task.ParsingTask;
import iped.engine.task.SkipCommitedTask;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.parsers.standard.StandardParser;
import iped.utils.IOUtil;

/**
 * Tarefa de indexação dos itens. Indexa apenas as propriedades, caso a
 * indexação do conteúdo esteja desabilitada. Reaproveita o texto dos itens caso
 * tenha sido extraído por tarefas anteriores.
 *
 * Indexa itens grandes dividindo-os em fragmentos, pois a lib de indexação
 * consome mta memória com documentos grandes.
 *
 */
public class IndexTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(IndexTask.class);

    public static final String TEXT_SIZE = "textSize"; //$NON-NLS-1$
    public static final String TEXT_SPLITTED = "textSplitted";
    public static final String FRAG_NUM = "fragNum";
    public static final String FRAG_PARENT_ID = "fragParentId";
    public static final String extraAttrFilename = "extraAttributes.dat"; //$NON-NLS-1$

    private static final AtomicBoolean finished = new AtomicBoolean();

    private static FieldType contentField;

    private static final FieldType getContentFieldType() {
        if (contentField == null) {
            FieldType field = new FieldType();
            field.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            field.setOmitNorms(true);
            IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
            field.setStoreTermVectors(indexConfig.isStoreTermVectors());
            field.freeze();
            contentField = field;
        }
        return contentField;
    }

    private StandardParser autoParser;

    private IndexTaskConfig indexConfig;

    public static boolean isTreeNodeOnly(IItem item) {
        return (!item.isToAddToCase() && (item.isDir() || item.isRoot() || item.hasChildren()))
                || item.getExtraAttribute(IndexItem.TREENODE) != null;
    }

    public static void configureTreeNodeAttributes(IItem item) {
        if (item.isSubItem()) {
            item.dispose();
        }
        item.setIdInDataSource(null);
        item.setInputStreamFactory(null);
        item.setExtraAttribute(IndexItem.TREENODE, "true"); //$NON-NLS-1$
        item.getCategorySet().clear();
    }

    public void process(IItem evidence) throws IOException {
        if (evidence.isQueueEnd()) {
            return;
        }

        if (SkipCommitedTask.isAlreadyCommited(evidence)) {
            evidence.setToIgnore(true);
            return;
        }

        Reader textReader = null;

        if (!evidence.isToAddToCase()) {
            if (isTreeNodeOnly(evidence)) {
                configureTreeNodeAttributes(evidence);
                textReader = new StringReader("");
            } else
                return;
        }

        stats.updateLastId(evidence.getId());

        if (textReader == null) {
            if (indexConfig.isIndexFileContents() && (indexConfig.isIndexUnallocated()
                    || !BaseCarveTask.UNALLOCATED_MIMETYPE.equals(evidence.getMediaType()))) {
                textReader = evidence.getTextReader();
                if (textReader == null) {
                    LOGGER.warn("Null Text reader, creating a new one for {}", evidence.getPath()); //$NON-NLS-1$
                    try {
                        TikaInputStream tis = evidence.getTikaStream();
                        Metadata metadata = getMetadata(evidence);
                        final ParseContext context = getTikaContext(evidence);
                        textReader = new ParsingReader(this.autoParser, tis, metadata, context);
                        ((ParsingReader) textReader).startBackgroundParsing();

                    } catch (IOException e) {
                        LOGGER.warn("{} Error opening: {} {}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                                e.toString());
                    }
                }
            }
        }

        if (textReader == null)
            textReader = new StringReader(""); //$NON-NLS-1$

        FragmentingReader fragReader = new FragmentingReader(textReader, indexConfig.getTextSplitSize(),
                indexConfig.getTextOverlapSize());
        try {
            worker.writer.addDocuments(new DocumentsIterable(evidence, fragReader));

        } catch (IOException e) {
            if (IOUtil.isDiskFull(e))
                throw new IPEDException(
                        "Not enough space for the index on " + worker.manager.getIndexTemp().getAbsolutePath()); //$NON-NLS-1$
            else
                throw e;
        } finally {
            fragReader.close();
        }

    }

    private class DocumentsIterable implements Iterable<Document> {

        private IItem item;
        private FragmentingReader fragReader;
        private boolean hasMoreContentFrags, parentIndexed = false;
        private int numFrags = 0;

        private DocumentsIterable(IItem item, FragmentingReader fragReader) {
            this.item = item;
            this.fragReader = fragReader;
        }

        public Iterator<Document> iterator() {
            return new Iterator<Document>() {

                public boolean hasNext() {
                    try {
                        while (worker.state != STATE.RUNNING) {
                            Thread.sleep(1000);
                        }
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }
                        hasMoreContentFrags = (numFrags == 0 || fragReader.nextFragment());
                        return hasMoreContentFrags || !parentIndexed;

                    } catch (InterruptedException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                public Document next() {
                    if (hasMoreContentFrags) {
                        if (++numFrags > 1) {
                            stats.incSplits();
                            LOGGER.info("{} Splitting text of {}", Thread.currentThread().getName(), item.getPath()); //$NON-NLS-1$
                        }
                        // child (content) document
                        Document doc = new Document();
                        doc.add(new IntPoint(FRAG_NUM, numFrags));
                        doc.add(new IntPoint(FRAG_PARENT_ID, item.getId()));
                        doc.add(new Field(IndexItem.CONTENT, new CloseFilterReader(fragReader), getContentFieldType()));
                        return doc;
                    } else {
                        if (numFrags > 1) {
                            item.setExtraAttribute(TEXT_SPLITTED, Boolean.TRUE.toString());
                        }
                        item.setExtraAttribute(TEXT_SIZE, fragReader.getTotalTextSize());
                        // parent (metadata) document
                        Document doc = IndexItem.Document(item, output);
                        parentIndexed = true;
                        return doc;
                    }
                }
                
            };
        }

    }

    private Metadata getMetadata(IItem evidence) {
        // new metadata to prevent ConcurrentModificationException while indexing
        Metadata metadata = new Metadata();
        ParsingTask.fillMetadata(evidence, metadata);
        return metadata;
    }

    private ParseContext getTikaContext(IItem evidence) {
        ParsingTask pt = new ParsingTask(evidence, this.autoParser);
        pt.setWorker(worker);
        pt.init(ConfigurationManager.get());
        ParseContext context = pt.getTikaContext();
        // this is to not create new items while indexing
        pt.setExtractEmbedded(false);
        return context;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        IndexTaskConfig result = ConfigurationManager.get().findObject(IndexTaskConfig.class);
        if(result == null) {
            result = new IndexTaskConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        
        indexConfig = configurationManager.findObject(IndexTaskConfig.class);

        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if (args.isAppendIndex() || args.isContinue() || args.isRestart()) {
            try (IPEDSource ipedSrc = new IPEDSource(output.getParentFile(), worker.writer)) {
                stats.setLastId(ipedSrc.getLastId());
                Item.setStartID(ipedSrc.getLastId() + 1);
            }
        }

        // Don't load default types if generating report, they will be loaded later.
        // See https://github.com/sepinf-inc/IPED/issues/1258
        if (!caseData.isIpedReport()) {
            IndexItem.loadMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
        }
        loadExtraAttributes();

        this.autoParser = new StandardParser();

    }

    @Override
    public void finish() throws Exception {

        if (!finished.getAndSet(true)) {
            saveExtraAttributes(output);
            IndexItem.saveMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
        }
    }

    public static void saveExtraAttributes(File output) throws IOException {
        File extraAttributtesFile = new File(output, "data/" + extraAttrFilename); //$NON-NLS-1$
        Set<String> extraAttr = Item.getAllExtraAttributes();
        Util.writeObject(extraAttr, extraAttributtesFile.getAbsolutePath());
        Util.fsync(extraAttributtesFile.toPath());
    }

    private void loadExtraAttributes() throws ClassNotFoundException, IOException {

        File extraAttributtesFile = new File(output, "data/" + extraAttrFilename); //$NON-NLS-1$
        if (extraAttributtesFile.exists()) {
            Set<String> extraAttributes = (Set<String>) Util.readObject(extraAttributtesFile.getAbsolutePath());
            Item.getAllExtraAttributes().addAll(extraAttributes);
        }
    }

}
