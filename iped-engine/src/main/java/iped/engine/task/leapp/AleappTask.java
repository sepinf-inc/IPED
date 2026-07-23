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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import iped.parsers.python.PythonParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import jep.Jep;
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

    /**
     * Guards all access to the shared ilapfuncs.identifiers dict: device_info() writes (serialized by
     * IlapfuncsDeviceInfoInterceptor) and the write_device_info() iteration in processDeviceInfoEvidence().
     */
    public static final Object DEVICE_INFO_LOCK = new Object();

    private volatile boolean initialized = false;
    private static volatile boolean interceptorsInstalled = false;

    private Map<String, PluginSpec> selectedPlugins;
    private ALeappConfig config;

    private Path outputFolder;
    private String outputFolderBase;

    private Path exportFilesFolder;

    // The per-worker-thread Jep interpreter shared with PythonParser/PythonTask
    // (see PythonParser.getJep()). Jep is thread-confined, but every AleappTask
    // instance belongs to a single worker and process()/finish() always run on
    // that worker's thread, so no dedicated Python thread is needed. The
    // interpreter's lifecycle is owned by PythonParser: never close it here.
    private Jep jep;

    public AleappTask() {
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

        outputFolder = Files.createTempDirectory("aleapp-output-");
        exportFilesFolder = outputFolder.resolve("data");
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

        // reuses the worker thread's interpreter, shared with PythonParser/PythonTask.
        // NOTE: the interpreter namespace is shared with the python parsers/tasks
        // running on this thread, so every global this integration creates is either
        // prefixed with _iped_leapp_ or deleted right after use.
        jep = PythonParser.getJep();
        if (jep == null) {
            logger.error("Python environment not available, ALeapp task disabled.");
            return;
        }

        jep.exec("import sys");
        jep.exec("sys.path.append('" + config.getAleappFolder().getCanonicalPath() + "')");

        // SharedInterpreter instances all share the same Python module state, so interceptors are 
        // installed only ONCE globally, even though each worker
        // thread has its own Jep instance
        synchronized (AleappTask.class) {
            if (!interceptorsInstalled) {
                LeappInterceptors interceptors = new LeappInterceptors();
                interceptors.install(jep);
                interceptorsInstalled = true;
            }
        }

        // load all available plugins
        // (mimics https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L181)
        jep.exec("import scripts.plugin_loader");
        jep.exec("_iped_leapp_plugins = list(scripts.plugin_loader.PluginLoader().plugins)");

        @SuppressWarnings("unchecked")
        List<PyObject> availablePlugins = (List<PyObject>) jep.getValue("_iped_leapp_plugins");

        selectedPlugins = availablePlugins
                .stream()
                .map(PluginSpec::new)
                .filter(plugin -> config.isPluginIncluded(plugin.getModuleName()))
                .collect(Collectors.toMap(PluginSpec::getName, Function.identity()));

        // the PyObjects held by PluginSpec keep their own references: the temp global can be removed
        jep.exec("del _iped_leapp_plugins");

        jep.exec("import scripts.ilapfuncs");
        jep.exec("import scripts.context");

        // mimics https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L307
        jep.exec("_iped_leapp_out_params = scripts.ilapfuncs.OutputParameters('" + outputFolder.toString() + "', 'ALEAPP_Reports')");
        outputFolderBase = jep.getValue("_iped_leapp_out_params.output_folder_base", String.class);

        // the Context keeps the reference: the temp global can be removed
        jep.exec("scripts.context.Context.set_output_params(_iped_leapp_out_params)");
        jep.exec("del _iped_leapp_out_params");
    }

    @Override
    public void process(IItem item) throws Exception {

        initialize();

        if (jep == null) {
            // python environment not available (see doSetup)
            return;
        }

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

        Map<String, Item> categoryItems = new HashMap<>();

        // creates one subitem for each plugin execution
        // (PluginSpec getters call PyObject.getAttr on this worker's interpreter)
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

        String pluginName = pluginEvidence.getMetadata().get(ALEAPP_PLUGIN_KEYNAME_META);
        PluginSpec plugin = selectedPlugins.get(pluginName);
        if (plugin == null) {
            throw new IllegalStateException("Plugin should have been found: " + pluginName);
        }

        // look for the files the plugin needs
        // (mimics https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L386)
        IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
        String pathRoot = StringUtils.substringBeforeLast(pluginEvidence.getPath(), "/" + CASE_EVIDENCE_NAME);
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
            try {
                // clears this thread's Python Context state, mirroring aleapp.py's per-artifact
                // Context.clear() (state is thread-local, see context_thread_local_patch.py)
                jep.exec("import scripts.context");
                jep.exec("scripts.context.Context.clear()");
            } catch (Exception e) {
                logger.warn("Failed to clear Python Context after {} plugin", pluginName, e);
            }
            seeker.cleanup();
            LeappContext.clear();
        }
    }

    private void processDeviceInfoEvidence(IItem deviceInfoEvidence) throws Exception {

        // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L432
        jep.exec("import scripts.ilapfuncs");

        Path deviceInfoPath = Files.createTempFile("screen_output_file_path_devinfo", ".html");
        try {
            // OutputParameters attributes are class-level and identifiers is a module
            // global, both shared across all workers: serialize the path switch + write
            // against concurrent device_info() calls (see IlapfuncsDeviceInfoInterceptor)
            synchronized (DEVICE_INFO_LOCK) {
                jep.exec("scripts.ilapfuncs.OutputParameters.screen_output_file_path_devinfo = '" + deviceInfoPath.toString() + "'");
                jep.exec("scripts.ilapfuncs.write_device_info()");
            }

            byte[] deviceInfoBytes = Files.readAllBytes(deviceInfoPath);

            if (deviceInfoBytes.length > 0) {
                ExportFileTask.getLastInstance().insertIntoStorage(deviceInfoEvidence, deviceInfoBytes, deviceInfoBytes.length);
            } else {
                deviceInfoEvidence.setToIgnore(true);
            }
        } finally {
            Files.deleteIfExists(deviceInfoPath);
        }
    }

    @Override
    public void finish() throws Exception {
        // the Jep interpreter is shared with PythonParser/PythonTask and owned by them: do NOT close it here
        jep = null;

        try {
            if (outputFolder != null && Files.exists(outputFolder)) {
                PathUtils.deleteDirectory(outputFolder);
            }
        } catch (Exception e) {
            logger.warn("Failed to delete outputFolder: {}", outputFolder, e);
        }
        outputFolder = null;
        exportFilesFolder = null;
    }
}
