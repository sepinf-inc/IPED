package iped.engine.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.After;
import org.junit.Test;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.data.IHashValue;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.core.Statistics;
import iped.engine.core.Worker;
import iped.engine.data.CaseData;
import iped.engine.data.Item;
import iped.engine.task.index.IndexItem;
import iped.utils.HashValue;
import sun.reflect.ReflectionFactory;

public class DuplicateTaskTest {

    private static final String HASH_1 = "0123456789abcdef0123456789abcdef";
    private static final String HASH_2 = "fedcba9876543210fedcba9876543210";
    private static final String HASH_3 = "d41d8cd98f00b204e9800998ecf8427e";

    @org.junit.Before
    public void setUp() throws Exception {
        Statistics.get(new CaseData(), new java.io.File("target/test-index"));
    }

    @After
    public void tearDown() throws Exception {
        Field singletonField = ConfigurationManager.class.getDeclaredField("singleton");
        singletonField.setAccessible(true);
        singletonField.set(null, null);

        Field ignoreDuplicatesField = DuplicateTask.class.getDeclaredField("ignoreDuplicates");
        ignoreDuplicatesField.setAccessible(true);
        ignoreDuplicatesField.set(null, false);

        Field instanceField = Statistics.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @SuppressWarnings("unchecked")
    private static Worker createWorkerStub(IndexWriter writer, CaseData caseData) throws Exception {
        Constructor<Worker> cons = (Constructor<Worker>) ReflectionFactory.getReflectionFactory()
                .newConstructorForSerialization(Worker.class, Object.class.getDeclaredConstructor());
        Worker worker = cons.newInstance();
        worker.writer = writer;
        worker.caseData = caseData;
        return worker;
    }

    private DuplicateTask createDuplicateTask(boolean ignoreDuplicates, CaseData caseData) throws Exception {
        if (caseData.getCaseObject(DuplicateTask.HASH_MAP) == null) {
            caseData.putCaseObject(DuplicateTask.HASH_MAP, new HashMap<IHashValue, IHashValue>());
        }
        return createDuplicateTask(ignoreDuplicates, caseData, null);
    }

    private DuplicateTask createDuplicateTask(boolean ignoreDuplicates, CaseData caseData, Worker worker) throws Exception {
        EnableTaskProperty enableProp = new EnableTaskProperty("ignoreDuplicates");
        enableProp.setEnabled(ignoreDuplicates);

        IConfigurationDirectory dummyDir = new IConfigurationDirectory() {
            @Override
            public void addPath(Path path) {}
            @Override
            public List<Path> getResourceLookupFolders() { return Collections.emptyList(); }
            @Override
            public List<Path> lookUpResource(Predicate<Path> predicate) { return Collections.emptyList(); }
            @Override
            public List<Path> lookUpResource(Configurable<?> configurable) { return Collections.emptyList(); }
        };

        ConfigurationManager cm = ConfigurationManager.createInstance(dummyDir);
        cm.addObject(enableProp);

        DuplicateTask task = new DuplicateTask();
        if (worker != null) {
            task.setWorker(worker);
        } else {
            task.caseData = caseData;
        }
        task.init(cm);
        return task;
    }

    @Test
    public void testUniqueItemNotIgnored() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item item = new Item();
        item.setHash(HASH_1);

        task.process(item);

        assertFalse(item.isToIgnore());
        assertEquals("true", item.getTempAttribute(DuplicateTask.class.getName() + "_EXECUTED"));

        @SuppressWarnings("unchecked")
        Map<IHashValue, IHashValue> map = (Map<IHashValue, IHashValue>) caseData.getCaseObject(DuplicateTask.HASH_MAP);
        assertNotNull(map);
        assertTrue(map.containsKey(new HashValue(HASH_1)));
    }

    @Test
    public void testDuplicateItemIgnoredWhenEnabled() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item item1 = new Item();
        item1.setHash(HASH_1);

        Item item2 = new Item();
        item2.setHash(HASH_1);

        Item item3 = new Item();
        item3.setHash(HASH_2);

        task.process(item1);
        task.process(item2);
        task.process(item3);

        assertFalse(item1.isToIgnore());
        assertTrue(item2.isToIgnore());
        assertFalse(item3.isToIgnore());
    }

    @Test
    public void testDuplicateItemNotIgnoredWhenDisabled() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(false, caseData);

        Item item1 = new Item();
        item1.setHash(HASH_1);

        Item item2 = new Item();
        item2.setHash(HASH_1);

        task.process(item1);
        task.process(item2);

        assertFalse(item1.isToIgnore());
        assertFalse(item2.isToIgnore());
    }

    @Test
    public void testDirectoryNeverIgnored() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item item1 = new Item();
        item1.setHash(HASH_1);

        Item dirItem = new Item();
        dirItem.setHash(HASH_1);
        dirItem.setIsDir(true);

        task.process(item1);
        task.process(dirItem);

        assertFalse(item1.isToIgnore());
        assertFalse(dirItem.isToIgnore());
    }

    @Test
    public void testRootItemNeverIgnored() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item item1 = new Item();
        item1.setHash(HASH_1);

        Item rootItem = new Item();
        rootItem.setHash(HASH_1);
        rootItem.setRoot(true);

        task.process(item1);
        task.process(rootItem);

        assertFalse(item1.isToIgnore());
        assertFalse(rootItem.isToIgnore());
    }

    @Test
    public void testIpedReportNeverIgnored() throws Exception {
        CaseData caseData = new CaseData();
        caseData.setIpedReport(true);
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item item1 = new Item();
        item1.setHash(HASH_1);

        Item item2 = new Item();
        item2.setHash(HASH_1);

        task.process(item1);
        task.process(item2);

        assertFalse(item1.isToIgnore());
        assertFalse(item2.isToIgnore());
    }

    @Test
    public void testNullOrEmptyHashNotIgnoredOrTracked() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item itemNull = new Item();
        Item itemEmpty = new Item();
        itemEmpty.setHash("");

        task.process(itemNull);
        task.process(itemEmpty);

        assertFalse(itemNull.isToIgnore());
        assertFalse(itemEmpty.isToIgnore());
        assertEquals("true", itemNull.getTempAttribute(DuplicateTask.class.getName() + "_EXECUTED"));
        assertEquals("true", itemEmpty.getTempAttribute(DuplicateTask.class.getName() + "_EXECUTED"));

        @SuppressWarnings("unchecked")
        Map<IHashValue, IHashValue> map = (Map<IHashValue, IHashValue>) caseData.getCaseObject(DuplicateTask.HASH_MAP);
        assertTrue(map.isEmpty());
    }

    @Test
    public void testAlreadyExecutedItemSkipped() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item item1 = new Item();
        item1.setHash(HASH_1);
        task.process(item1);

        Item item2 = new Item();
        item2.setHash(HASH_1);
        item2.setTempAttribute(DuplicateTask.class.getName() + "_EXECUTED", "true");

        task.process(item2);

        // Skipped because already executed flag is set, so isToIgnore remains false
        assertFalse(item2.isToIgnore());
    }

    @Test
    public void testMultiWorkerSharedState() throws Exception {
        CaseData sharedCaseData = new CaseData();
        DuplicateTask workerTask1 = createDuplicateTask(true, sharedCaseData);
        DuplicateTask workerTask2 = createDuplicateTask(true, sharedCaseData);

        Item itemWorker1 = new Item();
        itemWorker1.setHash(HASH_1);

        Item itemWorker2 = new Item();
        itemWorker2.setHash(HASH_1);

        workerTask1.process(itemWorker1);
        workerTask2.process(itemWorker2);

        assertFalse(itemWorker1.isToIgnore());
        assertTrue(itemWorker2.isToIgnore());
    }

    @Test
    public void testFinishClearsHashMap() throws Exception {
        CaseData caseData = new CaseData();
        DuplicateTask task = createDuplicateTask(true, caseData);

        Item item = new Item();
        item.setHash(HASH_1);
        task.process(item);

        @SuppressWarnings("unchecked")
        Map<IHashValue, IHashValue> map = (Map<IHashValue, IHashValue>) caseData.getCaseObject(DuplicateTask.HASH_MAP);
        assertEquals(1, map.size());

        task.finish();

        assertTrue(map.isEmpty());
    }

    @Test
    public void testConfigurablesAndMetadata() throws Exception {
        DuplicateTask task = new DuplicateTask();
        List<Configurable<?>> configs = task.getConfigurables();
        assertEquals(1, configs.size());
        assertTrue(configs.get(0) instanceof EnableTaskProperty);
        EnableTaskProperty prop = (EnableTaskProperty) configs.get(0);
        assertEquals("ignoreDuplicates", prop.getPropertyName());

        createDuplicateTask(true, new CaseData());
        assertTrue(DuplicateTask.isIgnoreDuplicatesEnabled());

        Field singletonField = ConfigurationManager.class.getDeclaredField("singleton");
        singletonField.setAccessible(true);
        singletonField.set(null, null);

        createDuplicateTask(false, new CaseData());
        assertFalse(DuplicateTask.isIgnoreDuplicatesEnabled());
    }

    @Test
    public void testInitFromLuceneIndexDocValues() throws Exception {
        CaseData caseData = new CaseData();

        ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriterConfig iwc = new IndexWriterConfig(new KeywordAnalyzer());
        try (IndexWriter writer = new IndexWriter(dir, iwc)) {
            Document doc1 = new Document();
            doc1.add(new SortedDocValuesField(IndexItem.HASH, new BytesRef(HASH_1)));
            writer.addDocument(doc1);

            Document doc2 = new Document();
            doc2.add(new SortedDocValuesField(IndexItem.HASH, new BytesRef(HASH_2)));
            writer.addDocument(doc2);

            writer.commit();

            Worker worker = createWorkerStub(writer, caseData);
            DuplicateTask task = createDuplicateTask(true, caseData, worker);

            @SuppressWarnings("unchecked")
            Map<IHashValue, IHashValue> map = (Map<IHashValue, IHashValue>) caseData.getCaseObject(DuplicateTask.HASH_MAP);
            assertNotNull(map);
            assertEquals(2, map.size());
            assertTrue(map.containsKey(new HashValue(HASH_1)));
            assertTrue(map.containsKey(new HashValue(HASH_2)));

            // Item with HASH_1 was pre-loaded from index, so its very first process call detects duplicate
            Item item = new Item();
            item.setHash(HASH_1);
            task.process(item);
            assertTrue(item.isToIgnore());

            // Item with HASH_3 was not in index, so it is not duplicate
            Item itemNew = new Item();
            itemNew.setHash(HASH_3);
            task.process(itemNew);
            assertFalse(itemNew.isToIgnore());
        } finally {
            dir.close();
        }
    }
}
