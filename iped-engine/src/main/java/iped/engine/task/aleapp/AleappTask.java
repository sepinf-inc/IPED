package iped.engine.task.aleapp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
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
import iped.engine.core.Worker;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.CaseData;
import iped.engine.data.Item;
import iped.engine.task.AbstractTask;
import iped.engine.task.ExportFileTask;
import iped.parsers.android.backup.AndroidBackupParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import jep.Jep;
import jep.SharedInterpreter;
import jep.python.PyObject;

public class AleappTask extends AbstractTask {

    protected static final Logger logger = LoggerFactory.getLogger(AleappTask.class);

    public static final String ALEAPP_APPLICATION_PREFIX = "x-aleapp-";

    public static final MediaType ALEAPP_CASE_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "case");
    public static final MediaType ALEAPP_PLUGIN_RESULTS_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "plugin-results");
    public static final MediaType ALEAPP_ACTIVITY_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "activity");
    public static final MediaType ALEAPP_DEVICE_INFO_MEDIATYPE = MediaType.application(ALEAPP_APPLICATION_PREFIX + "deviceinfo");

    private static final String DEVICE_INFO_HTML = "DeviceInfo.html";

    private static final String CASE_EVIDENCE_NAME = "ALEAPP_Results";

    private static final String ZIP_EXT = "zip";
    private static final String UFDR_EXT = "ufdr";

    private static final Set<String> dumpStartFolderNames = Set.of("Dump", "backup");
    private static final Set<String> artifactInfosToIgnore = Set.of("function", "paths", "requirements", "output_types", "notes");

    public static final String ALEAPP_METADATA_PREFIX = "aleapp:";
    public static final String ALEAPP_PLUGIN_METADATA_PREFIX = ALEAPP_METADATA_PREFIX + "plugin:";
    public static final String ALEAPP_EXTRACTION_TYPE_META = ALEAPP_METADATA_PREFIX + "extractionType";
    public static final String ALEAPP_PLUGIN_KEYNAME_META = ALEAPP_PLUGIN_METADATA_PREFIX + "key";

    private static final String EXTRACTION_TYPE_ANDROID_BACKUP = "android-backup";
    private static final String EXTRACTION_TYPE_ZIP = "zip";
    private static final String EXTRACTION_TYPE_UFDR = "ufdr";
    private static final String EXTRACTION_TYPE_DUMP = "dump";

    private static final Map<String, String> translatedPaths = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;
    private Map<String, PluginSpec> selectedPlugins;
    private ALeappConfig config;

    private AleappInterceptors interceptor;

    private static Path outputFolder;
    private static AtomicBoolean outputParametersCreated = new AtomicBoolean();
    private static String reportFolderBase;
    private static String deviceInfoPath;

    private static final ThreadLocal<Jep> jepThreadLocal = new ThreadLocal<>() {
        @Override
        protected Jep initialValue() {
            return new SharedInterpreter();
        }
    };

    private static final ThreadLocal<State> stateThreadLocal = new ThreadLocal<>();

    public AleappTask() {
    }

    public static class State {
        private CaseData caseData;
        private Worker worker;
        private IItem pluginItem;

        public State(CaseData caseData, Worker worker, IItem pluginItem) {
            this.caseData = caseData;
            this.worker = worker;
            this.pluginItem = pluginItem;
        }

        public CaseData getCaseData() {
            return caseData;
        }

        public Worker getWorker() {
            return worker;
        }

        public IItem getPluginItem() {
            return pluginItem;
        }
    }

    public static State getState() {
        return stateThreadLocal.get();
    }

    public static Jep getJep() {
        return jepThreadLocal.get();
    }

    public static Map<String, String> getTranslatedPaths() {
        return translatedPaths;
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

        if (outputFolder == null) {
            outputFolder = output.toPath().resolve("aleapp");
            Files.createDirectories(outputFolder);
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
        Jep jep = getJep();

        jep.exec("import sys");
        jep.exec("sys.path.append('" + config.getAleappFolder().getCanonicalPath() + "')");

        interceptor = new AleappInterceptors(caseData);
        interceptor.install(jep);

        // load all available plugins
        // (mimics https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L154)
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

        if (!outputParametersCreated.getAndSet(true)) {
            // Sets the reportFolder (can be executed once, due to os.makedirs() in OutputParameters constructor
            // mimics https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L289
            jep.exec("from scripts.ilapfuncs import OutputParameters");
            jep.exec("out_params = OutputParameters('" + outputFolder.toString() + "', 'ALEAPP_Reports')");
            reportFolderBase = jep.getValue("out_params.report_folder_base", String.class);
            deviceInfoPath = jep.getValue("OutputParameters.screen_output_file_path_devinfo", String.class);
        } else {
            jep.set("OutputParameters.screen_output_file_path_devinfo", deviceInfoPath);
        }
    }

    @Override
    public void process(IItem item) throws Exception {

        initialize();

        if (isExtractionRoot(item)) {
            processExtractionRoot(item);
        } else if (isCaseEvidence(item)) {
            processCaseEvidence(item);
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

        return dumpStartFolderNames.contains(realName) || StringUtils.equalsAnyIgnoreCase(realExt, UFDR_EXT, ZIP_EXT);
    }

    private boolean isCaseEvidence(IItem evidence) {
        return ALEAPP_CASE_MEDIATYPE.equals(evidence.getMediaType());
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
        caseEvidence.setExtraAttribute(BasicProps.TREENODE, Boolean.valueOf(true));

        String extractionType;
        if (AndroidBackupParser.SUPPORTED_TYPES.contains(rootEvidence.getMediaType())) {
            extractionType = EXTRACTION_TYPE_ANDROID_BACKUP;
        } else if (dumpStartFolderNames.contains(rootEvidence.getName())) {
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

    private void processCaseEvidence(IItem caseEvidence) {

        // check if case is a real dump of Android extraction
        String extractionType = caseEvidence.getMetadata().get(ALEAPP_EXTRACTION_TYPE_META);
        if (StringUtils.equalsAny(extractionType, EXTRACTION_TYPE_DUMP, EXTRACTION_TYPE_ZIP) && !isInsideRealDump(caseEvidence)) {
            caseEvidence.setToIgnore(true);
            return;
        }

        // creates one subitem for each plugin execution
        for (PluginSpec plugin : selectedPlugins.values()) {

            Item pluginEvidence = (Item) caseEvidence.createChildItem();
            pluginEvidence.setMediaType(ALEAPP_PLUGIN_RESULTS_MEDIATYPE);

            String name = StringUtils.firstNonBlank((String) plugin.getArtifactInfo().get("name"), plugin.getName());
            pluginEvidence.setName(name);
            pluginEvidence.setExtension("");
            pluginEvidence.setPath(caseEvidence.getPath() + "/" + name);
            pluginEvidence.setIdInDataSource("");
            pluginEvidence.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

            pluginEvidence.getMetadata().set(ALEAPP_PLUGIN_KEYNAME_META, plugin.getName());
            pluginEvidence.getMetadata().set(ALEAPP_PLUGIN_METADATA_PREFIX + "moduleName", plugin.getModuleName());
            pluginEvidence.getMetadata().set(ALEAPP_PLUGIN_METADATA_PREFIX + "category", plugin.getCategory());
            for (Entry<String, Object> entry : plugin.getArtifactInfo().entrySet()) {
                if (artifactInfosToIgnore.contains(entry.getKey())) {
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

    @SuppressWarnings("unchecked")
    private void processPluginEvidence(IItem pluginEvidence) {

        try {
            stateThreadLocal.set(new State(caseData, worker, pluginEvidence));

            String pluginName = pluginEvidence.getMetadata().get(ALEAPP_PLUGIN_KEYNAME_META);
            PluginSpec plugin = selectedPlugins.get(pluginName);
            if (plugin == null) {
                throw new IllegalStateException("Plugin should have been found: " + pluginName);
            }

            // look for the files the plugin needs
            // (mimics https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L361)
            IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
            String rootPath = StringUtils.substringBefore(pluginEvidence.getPath(), "/" + CASE_EVIDENCE_NAME);
            FileSeeker seeker = new FileSeeker(rootPath, searcher);
            HashSet<String> filesFound = new HashSet<>();
            for (String regex : plugin.getSearchRegexes()) {
                filesFound.addAll((Collection<String>) seeker.search(regex, false));
            }

            if (filesFound.isEmpty()) {
                logger.warn("Ignoring Aleapp {} plugin: no files found", pluginName);
                pluginEvidence.setToIgnore(true);
                return;
            }

            try {
                // mimics https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L374
                Path categoryFolder = Paths.get(reportFolderBase, "_HTML", plugin.getCategory());
                Files.createDirectories(categoryFolder);

                // call the plugin method
                // (https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L383)
                plugin.getMethod().call(new ArrayList<>(filesFound), categoryFolder.toString(), seeker, false);

            } catch (Exception e) {
                logger.error("Aleapp {} plugin ended prematurely: {}", pluginName, ExceptionUtils.getMessage(e));
                logger.warn(pluginName, e);
            }

            if (!pluginEvidence.hasChildren()) {
                logger.warn("Ignoring Aleapp {} plugin: no children", pluginName);
                pluginEvidence.setToIgnore(true);
            }
        } finally {
            stateThreadLocal.remove();
        }
    }

    private void processDeviceInfoEvidence(IItem deviceInfoEvidence) throws Exception {
        Jep jep = getJep();

        // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L394
        jep.exec("import scripts.ilapfuncs");
        jep.exec("scripts.ilapfuncs.write_device_info()");

        byte[] deviceInfoBytes = Files.readAllBytes(Paths.get(deviceInfoPath));
        if (deviceInfoBytes.length > 0) {
            ExportFileTask.getLastInstance().insertIntoStorage(deviceInfoEvidence, deviceInfoBytes, deviceInfoBytes.length);
        } else {
            deviceInfoEvidence.setToIgnore(true);
        }
    }

    /*
     * Rules to check if evidence corresponds to an android Dump from where to start executing ALeapp plugins
     */
    private boolean isInsideRealDump(IItem caseEvidence) {

        String checkFolder = "/data/data/com.android.vending";
        String rootPath = StringUtils.substringBefore(caseEvidence.getPath(), "/" + CASE_EVIDENCE_NAME);

        IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
        List<IItemReader> result = searcher.search("path:\"" + rootPath + checkFolder + "\"");

        return !result.isEmpty();
    }

    @Override
    public void finish() throws Exception {
        jepThreadLocal.get().close();
        jepThreadLocal.remove();
    }
}
