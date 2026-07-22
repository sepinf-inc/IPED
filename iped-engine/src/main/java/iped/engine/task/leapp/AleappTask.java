package iped.engine.task.leapp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.function.FailableRunnable;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.config.ALeappConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.task.AbstractTask;
import iped.engine.task.ExportFileTask;
import iped.parsers.android.backup.AndroidBackupParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import jep.Jep;
import jep.JepException;
import jep.SharedInterpreter;
import jep.python.PyObject;

public class AleappTask extends AbstractTask {

    protected static final Logger logger = LoggerFactory.getLogger(AleappTask.class);

    public static final String ALEAPP_APPLICATION_PREFIX = "x-aleapp-";

    public static final MediaType ALEAPP_CASE_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "case");
    public static final MediaType ALEAPP_CATEGORY_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "category");
    public static final MediaType ALEAPP_PLUGIN_RESULTS_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "plugin-results");
    public static final MediaType ALEAPP_ACTIVITY_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "activity");
    public static final MediaType ALEAPP_DEVICE_INFO_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "deviceinfo");

    public static final String ALEAPP_PLUGIN_CATEGORY_KEY = "aleapp_category";

    private static final String DEVICE_INFO_HTML = "DeviceInfo.html";

    private static final String CASE_EVIDENCE_NAME = "ALEAPP_Results";

    private static final String ZIP_EXT = "zip";
    private static final String UFDR_EXT = "ufdr";

    private static final Set<String> DUMP_ROOT_FOLDER_NAMES = Set.of("Dump", "backup");
    private static final Set<String> ARTIFACT_INFO_KEYS_TO_IGNORE = Set
            .of("function", "paths", "requirements", "output_types", "notes", "sample_data", "artifact_icon");

    public static final String ALEAPP_METADATA_PREFIX = "aleapp:";
    public static final String ALEAPP_PLUGIN_METADATA_PREFIX = ALEAPP_METADATA_PREFIX + "plugin:";
    public static final String ALEAPP_EXTRACTION_TYPE_META = ALEAPP_METADATA_PREFIX + "extractionType";
    public static final String ALEAPP_PLUGIN_KEYNAME_META = ALEAPP_PLUGIN_METADATA_PREFIX + "key";

    private static final String EXTRACTION_TYPE_ANDROID_BACKUP = "android-backup";
    private static final String EXTRACTION_TYPE_ZIP = "zip";
    private static final String EXTRACTION_TYPE_UFDR = "ufdr";
    private static final String EXTRACTION_TYPE_DUMP = "dump";

    private volatile boolean initialized = false;
    private static volatile boolean interceptorsInstalled = false;

    private Map<String, PluginSpec> selectedPlugins;
    private ALeappConfig config;

    private static Path outputFolder;
    private static String outputFolderBase;

    private Path exportFilesFolder;

    // Jep interpreters are thread-confined: every interaction with "jep" MUST happen
    // on the same thread that created it. This dedicated single-thread executor is
    // that thread; all Python access goes through runOnPythonThread().
    private ExecutorService executor;
    private Jep jep;

    public AleappTask() {
    }

    private ExecutorService getExecutor() {
        if (executor == null) {
            BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("AleappTask-%d-" + Thread.currentThread().getName())
                .build();
            executor = Executors.newSingleThreadExecutor(factory);
        }
        return executor;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new ALeappConfig());
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        config = (ALeappConfig) configurationManager.findObject(ALeappConfig.class);

        exportFilesFolder = Files.createTempDirectory("aleapp-data-");
    }

    /**
     * Runs the given code on this task's dedicated Python thread and rethrows any exception it raised. Required because
     * Jep is thread-confined (see {@link #executor}); also used for code that only touches Python indirectly, e.g. via
     * {@link PluginSpec} getters, which call PyObject.getAttr.
     */
    private void runOnPythonThread(FailableRunnable<Exception> task) throws Exception {
        Exception ret = getExecutor().submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                return e;
            }
            return null;

        }).get();

        if (ret != null) {
            throw ret;
        }
    }

    public void initialize() throws Exception {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    doSetup();
                    initialized = true;
                }
            }
        }
    }

    private void doSetup() throws Exception {
        runOnPythonThread(() -> {
            jep = new SharedInterpreter();

            jep.exec("import sys");
            jep.exec("sys.path.append('" + config.getAleappFolder().getCanonicalPath() + "')");

            // SharedInterpreter instances all share the same Python module state, so
            // interceptors (and OutputParameters below) are installed only ONCE globally,
            // even though each worker thread creates its own Jep instance.
            synchronized (AleappTask.class) {
                if (!interceptorsInstalled) {
                    LeappInterceptors interceptors = new LeappInterceptors();
                    interceptors.install(jep);
                    interceptorsInstalled = true;
                }
            }

            // load all available plugins
            // (mimics https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L181)
            jep.exec("import scripts.plugin_loader as plugin_loader");
            jep.exec("loader = plugin_loader.PluginLoader()");
            jep.exec("available_plugins = list(loader.plugins)");

            @SuppressWarnings("unchecked")
            List<PyObject> availablePlugins = (List<PyObject>) jep.getValue("available_plugins");

            selectedPlugins = availablePlugins
                    .stream()
                    .map(PluginSpec::new)
                    .filter(plugin -> config.isPluginIncluded(plugin.getModuleName()))
                    .collect(Collectors.toMap(PluginSpec::getName, Function.identity()));


            jep.exec("from scripts.ilapfuncs import OutputParameters");
            jep.exec("from scripts.context import Context");
            synchronized (AleappTask.class) {
                if (outputFolder == null) {
                    outputFolder = Files.createTempDirectory("aleapp-output-");

                    // mimics https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L307
                    jep.exec("out_params = OutputParameters('" + outputFolder.toString() + "', 'ALEAPP_Reports')");
                    outputFolderBase = jep.getValue("out_params.output_folder_base", String.class);
                    jep.exec("Context.set_output_params(out_params)");
                }
            }
        });
    }

    @Override
    public void process(IItem item) throws Exception {

        initialize();

        if (isExtractionRoot(item)) {
            processExtractionRoot(item);
        } else if (isCaseEvidence(item)) {
            processCaseEvidence(item);
        } else if (isCategoryEvidence(item)) {
            processCategoryEvidence(item);
        } else if (isPluginEvidence(item)) {
            processPluginEvidence(item);
        } else if (isDeviceInfoEvidence(item)) {
            processDeviceInfoEvidence(item);
        }
    }

    private boolean isExtractionRoot(IItem evidence) {

        if (AndroidBackupParser.SUPPORTED_TYPES.contains(evidence.getMediaType())) {
            return true;
        }

        String realName = evidence.getName();
        String realExt = evidence.getExt();
        if (evidence.isRoot()) {
            // if evidence is root, its realname can be changed via -dname parameter, so we
            // need to get it from other source.
            realName = evidence.getDataSource().getSourceFile().getName();
            realExt = FilenameUtils.getExtension(realName);
        }

        return DUMP_ROOT_FOLDER_NAMES.contains(realName) || StringUtils.equalsAnyIgnoreCase(realExt, UFDR_EXT);
    }

    private boolean isCaseEvidence(IItem evidence) {
        return ALEAPP_CASE_MEDIATYPE.equals(evidence.getMediaType());
    }

    private boolean isCategoryEvidence(IItem evidence) {
        return ALEAPP_CATEGORY_MEDIATYPE.equals(evidence.getMediaType());
    }

    private boolean isPluginEvidence(IItem evidence) {
        return ALEAPP_PLUGIN_RESULTS_MEDIATYPE.equals(evidence.getMediaType());
    }

    private boolean isDeviceInfoEvidence(IItem evidence) {
        return ALEAPP_DEVICE_INFO_MEDIATYPE.equals(evidence.getMediaType());
    }

    private void processExtractionRoot(IItem rootEvidence) {

        if (selectedPlugins.isEmpty()) {
            return;
        }

        // creates a subitem to represent the ALeapp report
        Item caseEvidence = (Item) rootEvidence.createChildItem();
        caseEvidence.setMediaType(ALEAPP_CASE_MEDIATYPE);

        String name = CASE_EVIDENCE_NAME;
        caseEvidence.setName(name);
        caseEvidence.setPath(rootEvidence.getPath() + "/" + name);
        caseEvidence.setIdInDataSource("");
        caseEvidence.setHasChildren(true);
        caseEvidence.setExtraAttribute(BasicProps.TREENODE, true);
        caseEvidence.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

        String extractionType;
        if (AndroidBackupParser.SUPPORTED_TYPES.contains(rootEvidence.getMediaType())) {
            extractionType = EXTRACTION_TYPE_ANDROID_BACKUP;
        } else if (DUMP_ROOT_FOLDER_NAMES.contains(rootEvidence.getName())) {
            extractionType = EXTRACTION_TYPE_DUMP;
        } else {
            String realExt = rootEvidence.getExt();
            if (rootEvidence.isRoot()) {
                String realName = rootEvidence.getDataSource().getSourceFile().getName();
                realExt = FilenameUtils.getExtension(realName);
            }
            if (UFDR_EXT.equalsIgnoreCase(realExt)) {
                extractionType = EXTRACTION_TYPE_UFDR;
            } else if (ZIP_EXT.equalsIgnoreCase(realExt)) {
                extractionType = EXTRACTION_TYPE_ZIP;
            } else {
                throw new IllegalStateException("Unexpected extension: " + realExt);
            }
        }
        caseEvidence.getMetadata().set(ALEAPP_EXTRACTION_TYPE_META, extractionType);

        worker.processNewItem(caseEvidence, ProcessTime.LATER);
    }

    private void processCaseEvidence(IItem caseEvidence) throws Exception {

        // check if case is a real dump of Android extraction
        String extractionType = caseEvidence.getMetadata().get(ALEAPP_EXTRACTION_TYPE_META);
        if (StringUtils.equalsAny(extractionType, EXTRACTION_TYPE_DUMP, EXTRACTION_TYPE_ZIP) && !isInsideRealDump(caseEvidence)) {
            caseEvidence.setToIgnore(true);
            return;
        }

        // must run on the Python thread: PluginSpec getters below call PyObject.getAttr
        runOnPythonThread(() -> {

            Map<String, Item> categoryItems = new HashMap<>();

            // creates one subitem for each plugin execution
            for (PluginSpec plugin : selectedPlugins.values()) {

                Item categoryItem = categoryItems.computeIfAbsent(plugin.getCategory(), name -> {
                    Item categoryEvidence = (Item) caseEvidence.createChildItem();
                    categoryEvidence.setMediaType(ALEAPP_CATEGORY_MEDIATYPE);

                    categoryEvidence.setName(name);
                    categoryEvidence.setExtension("");
                    categoryEvidence.setPath(caseEvidence.getPath() + "/" + name);
                    categoryEvidence.setIdInDataSource("");
                    categoryEvidence.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

                    worker.processNewItem(categoryEvidence, ProcessTime.LATER);

                    return categoryEvidence;
                });

                Item pluginEvidence = (Item) categoryItem.createChildItem();
                pluginEvidence.setMediaType(ALEAPP_PLUGIN_RESULTS_MEDIATYPE);
                pluginEvidence.setTempAttribute(ALEAPP_PLUGIN_CATEGORY_KEY, categoryItem);

                String name = StringUtils.firstNonBlank((String) plugin.getArtifactInfo().get("name"), plugin.getName());
                pluginEvidence.setName(name);
                pluginEvidence.setExtension("");
                pluginEvidence.setPath(categoryItem.getPath() + "/" + name);
                pluginEvidence.setIdInDataSource("");
                pluginEvidence.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

                pluginEvidence.getMetadata().set(ALEAPP_PLUGIN_KEYNAME_META, plugin.getName());
                pluginEvidence.getMetadata().set(ALEAPP_PLUGIN_METADATA_PREFIX + "moduleName", plugin.getModuleName());
                for (Entry<String, Object> entry : plugin.getArtifactInfo().entrySet()) {
                    if (ARTIFACT_INFO_KEYS_TO_IGNORE.contains(entry.getKey())) {
                        continue;
                    }
                    pluginEvidence.getMetadata().set(ALEAPP_PLUGIN_METADATA_PREFIX + entry.getKey(), entry.getValue().toString());
                }

                worker.processNewItem(pluginEvidence, ProcessTime.LATER);
            }
        });

        // creates subitem to hold device info collected
        Item deviceInfoEvidence = (Item) caseEvidence.createChildItem();
        deviceInfoEvidence.setName(DEVICE_INFO_HTML);
        deviceInfoEvidence.setMediaType(ALEAPP_DEVICE_INFO_MEDIATYPE);
        deviceInfoEvidence.setPath(caseEvidence.getPath() + "/" + DEVICE_INFO_HTML);
        deviceInfoEvidence.setIdInDataSource("");
        worker.processNewItem(deviceInfoEvidence, ProcessTime.LATER);
    }

    /**
     * Checks if the case evidence really corresponds to an Android dump, by looking for a folder every Android device
     * has ("/data/data/com.android.vending"). Avoids running plugins over unrelated zips/folders that just happen to
     * match the extraction root naming rules.
     */
    private boolean isInsideRealDump(IItem caseEvidence) {

        String checkFolder = "/data/data/com.android.vending";
        String rootPath = StringUtils.substringBefore(caseEvidence.getPath(), "/" + CASE_EVIDENCE_NAME);

        IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
        List<IItemReader> result = searcher.search("path:\"" + rootPath + checkFolder + "\"");

        return !result.isEmpty();
    }

    private void processCategoryEvidence(IItem categoryEvidence) throws Exception {
        if (!categoryEvidence.hasChildren()) {
            logger.info("Ignoring Aleapp category {}: no children", categoryEvidence.getName());
            categoryEvidence.setToIgnore(true);
        }
    }

    private void processPluginEvidence(IItem pluginEvidence) throws Exception {
        runOnPythonThread(() -> {
            doProcessPluginEvidence(pluginEvidence);
        });
    }

    private void doProcessPluginEvidence(IItem pluginEvidence) {

        String pluginName = pluginEvidence.getMetadata().get(ALEAPP_PLUGIN_KEYNAME_META);
        PluginSpec plugin = selectedPlugins.get(pluginName);
        if (plugin == null) {
            throw new IllegalStateException("Plugin should have been found: " + pluginName);
        }

        // look for the files the plugin needs
        // (mimics https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L386)
        IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
        String pathRoot = StringUtils.substringBefore(pluginEvidence.getPath(), "/" + CASE_EVIDENCE_NAME);
        FileSeeker seeker = new FileSeeker(pathRoot, exportFilesFolder, searcher);

        try {

            List<IItemReader> filesFound = seeker.searchItems(plugin.getSearchGlobs());

            if (filesFound.isEmpty()) {
                logger.warn("Ignoring Aleapp {} plugin: no files found", pluginName);
                pluginEvidence.setToIgnore(true);
                return;
            }

            LeappContext.create(seeker, worker, jep, plugin, pluginEvidence, filesFound);

            try {
                // mimics https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L409
                Path categoryFolder = Paths.get(outputFolderBase, "_HTML", plugin.getCategory());
                Files.createDirectories(categoryFolder);

                // export all files found (and -wal/-journal companions of sqlite items)
                ArrayList<String> filesFoundStringList = seeker.exportItems(filesFound);

                // call the plugin method
                // plugin.method(files_found, report_folder, seeker, wrap_text)
                //
                // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L418
                plugin.getMethod().call(filesFoundStringList, categoryFolder.toString(), seeker, false);

            } catch (Exception e) {
                logger.error("Aleapp {} plugin ended prematurely: {}", pluginName, ExceptionUtils.getMessage(e));
                logger.warn(pluginName, e);
            }

            if (!pluginEvidence.hasChildren()) {
                logger.warn("Ignoring Aleapp {} plugin: no children", pluginName);
                pluginEvidence.setToIgnore(true);
            }
        } finally {
            seeker.cleanup();
            LeappContext.clear();
        }
    }

    private void processDeviceInfoEvidence(IItem deviceInfoEvidence) throws Exception {

        runOnPythonThread(() -> {
            // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L432
            jep.exec("import scripts.ilapfuncs");
            jep.exec("scripts.ilapfuncs.write_device_info()");

            String deviceInfoPath = jep.getValue("OutputParameters.screen_output_file_path_devinfo", String.class);
            byte[] deviceInfoBytes = Files.readAllBytes(Paths.get(deviceInfoPath));

            if (deviceInfoBytes.length > 0) {
                ExportFileTask.getLastInstance().insertIntoStorage(deviceInfoEvidence, deviceInfoBytes, deviceInfoBytes.length);
            } else {
                deviceInfoEvidence.setToIgnore(true);
            }
        });
    }

    @Override
    public void finish() throws Exception {
        if (jep != null) {
            runOnPythonThread(() -> {
                try {
                    jep.close();
                } catch (JepException e) {
                }
                jep = null;
            });
        }
        if (executor != null) {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            executor = null;
        }
    }
}
