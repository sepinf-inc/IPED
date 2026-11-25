package iped.engine.task.leapp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.datasource.IDataSource;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.datasource.UfedXmlReader;
import iped.engine.task.AbstractTask;
import iped.engine.task.ExportCSVTask;
import iped.engine.task.ExportFileTask;
import iped.engine.task.leapp.conversation.ConversationViewSpec;
import iped.parsers.python.PythonParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import jep.Jep;
import jep.python.PyObject;

/**
 * Base of the tasks that run the plugins of a LEAPP tool (ALEAPP, iLEAPP...) over an extraction of the case.
 *
 * <p>
 * Everything the LEAPP tools have in common is here: the Python setup, the plugin discovery, the item hierarchy that
 * holds the results (results item &rarr; category &rarr; plugin &rarr; rows), the plugin execution and the linking of
 * the results to the case items. A subclass only tells which tool it runs ({@link #getTool()}), where it is
 * installed, which plugins are enabled and how to recognize an extraction the tool can decode.
 *
 * <p>
 * NOTE: all the LEAPP tools use the same top level Python package names ("scripts", "leapp_functions"), and IPED
 * runs a single Python interpreter (Jep SharedInterpreter), where a package name can be bound to one folder only. So,
 * as it is, two LEAPP tools cannot be enabled in the same processing: see {@link #doSetup()}.
 */
public abstract class AbstractLeappTask extends AbstractTask {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractLeappTask.class);

    // Temp attribute of a plugin item, holding its category item
    public static final String LEAPP_PLUGIN_CATEGORY_KEY = "leapp_category";

    // Temp attribute (survives the ProcessTime.LATER queue) carrying the "<tool>:<header>" metadata keys of a row
    // subitem whose values are device file paths. Resolving each path needs an index search, too costly to run per
    // row inside the plugin call, so it is deferred: the subitem comes back through process() and each recorded path
    // is resolved to its case item and added to LINKED_ITEMS (see processDeferredFilePathLinks).
    public static final String LEAPP_METADATA_PATHS = "leapp_metadata_paths";

    // Temp attribute of a row subitem holding the embedded media (name -> data) registered by the plugin through
    // check_in_embedded_media, to become child items of the row (see processEmbeddedMedia).
    public static final String LEAPP_EMBEDDED_MEDIA = "leapp_embedded_media";

    private static final String DEVICE_INFO_HTML = "DeviceInfo.html";

    protected static final String UFDR_EXT = "ufdr";

    /** Extraction type of the file system of a ufdr (see {@link #getExtractionType}). */
    protected static final String EXTRACTION_TYPE_UFDR = "ufdr";

    // data_views is not copied to metadata: it is consumed by ConversationViewSpec
    // to build chat-preview items (see LavaInsertSqliteDataInterceptor)
    private static final Set<String> ARTIFACT_INFO_KEYS_TO_IGNORE = Set
            .of("function", "paths", "requirements", "output_types", "notes", "sample_data", "artifact_icon",
                    ConversationViewSpec.DATA_VIEWS_KEY);

    /**
     * Guards all access to the shared ilapfuncs.identifiers dict: device_info() writes (serialized by
     * IlapfuncsDeviceInfoInterceptor) and the write_device_info() iteration in processDeviceInfoEvidence().
     */
    public static final Object DEVICE_INFO_LOCK = new Object();

    // The Python module state is shared by all the interpreters (see doSetup), so these are global too: the
    // interceptors are installed once, and the folder of the tool whose "scripts" package got loaded is remembered.
    private static volatile boolean interceptorsInstalled = false;
    private static volatile String loadedToolFolder;

    private volatile boolean initialized = false;

    private Map<String, PluginSpec> selectedPlugins;

    private Path outputFolder;
    private String outputFolderBase;

    private Path exportFilesFolder;

    // The per-worker-thread Jep interpreter shared with PythonParser/PythonTask
    // (see PythonParser.getJep()). Jep is thread-confined, but every task
    // instance belongs to a single worker and process()/finish() always run on
    // that worker's thread, so no dedicated Python thread is needed. The
    // interpreter's lifecycle is owned by PythonParser: never close it here.
    private Jep jep;

    /** The tool this task runs. */
    protected abstract LeappTool getTool();

    /** The folder where the tool is installed (the one holding its "scripts" package). */
    protected abstract File getToolFolder();

    /**
     * Whether the plugins of the given module (python file stem) should run.
     */
    protected abstract boolean isPluginIncluded(String moduleName);

    /**
     * Whether the item is the root of an extraction that could be decoded by the tool. This runs for every item of
     * the case, so it must be cheap: the costly checks belong to {@link #isSupportedExtraction}.
     */
    protected abstract boolean isExtractionRoot(IItem item);

    /**
     * A tag identifying how the extraction is laid out, stored in the results item and handed back to
     * {@link #isSupportedExtraction}.
     */
    protected abstract String getExtractionType(IItem extractionRoot);

    /**
     * Checks if the extraction really holds what the tool decodes. It is called when the results item is processed,
     * after the items of the extraction were indexed, so the case can be searched here.
     *
     * @param resultsItem the results item created under the extraction root
     * @param extractionType the tag returned by {@link #getExtractionType}
     */
    protected abstract boolean isSupportedExtraction(IItem resultsItem, String extractionType);

    /**
     * Calls the plugin entry point. The default is the signature of ALEAPP plugins: method(files_found,
     * report_folder, seeker, wrap_text). Tools whose plugins take other arguments must override it.
     */
    protected void callPlugin(PluginSpec plugin, List<String> filesFound, String reportFolder, FileSeeker seeker)
            throws Exception {
        plugin.getMethod().call(filesFound, reportFolder, seeker, false);
    }

    /**
     * What to run over the file system of a ufdr, whose data was already decoded by UFED. The default runs every
     * enabled plugin.
     */
    protected UfdrDecodingMode getUfdrDecodingMode() {
        return UfdrDecodingMode.ALL;
    }

    /**
     * The UFED models holding the same data a plugin decodes, used by {@link UfdrDecodingMode#EXTERNAL}. The default
     * is none: the plugin always runs.
     *
     * @param moduleName the plugin module (python file stem)
     * @param artifactName the artifact of the module
     */
    protected List<UfedModelRule> getUfedModels(String moduleName, String artifactName) {
        return List.of();
    }

    @Override
    public void init(iped.engine.config.ConfigurationManager configurationManager) throws Exception {
        outputFolder = Files.createTempDirectory(getTool().getId() + "-output-");
    }

    private void initialize() {
        if (!initialized) {
            try {
                doSetup();
            } catch (Exception e) {
                // a broken tool installation (missing python dependency, invalid plugin...) must not abort the
                // whole processing: the task is disabled for this worker instead
                logger.error("{} setup failed, task disabled: {}", getTool().getDisplayName(),
                        ExceptionUtils.getMessage(e), e);
                jep = null;
            }
            initialized = true;
        }
    }

    private void doSetup() throws Exception {

        String toolName = getTool().getDisplayName();

        // reuses the worker thread's interpreter, shared with PythonParser/PythonTask.
        // NOTE: the interpreter namespace is shared with the python parsers/tasks
        // running on this thread, so every global this integration creates is either
        // prefixed with _iped_leapp_ or deleted right after use.
        jep = PythonParser.getJep();
        if (jep == null) {
            logger.error("Python environment not available, {} task disabled.", toolName);
            return;
        }

        String toolFolder = getToolFolder().getCanonicalPath();

        // SharedInterpreter instances all share the same Python module state, so the tool folder is added to
        // sys.path and the interceptors are installed only ONCE globally, even though each worker thread has its own
        // Jep instance
        synchronized (AbstractLeappTask.class) {
            if (loadedToolFolder == null) {
                jep.set("_iped_leapp_tool_folder", toolFolder);
                jep.exec("import sys");
                jep.exec("if _iped_leapp_tool_folder not in sys.path: sys.path.append(_iped_leapp_tool_folder)");
                jep.exec("del _iped_leapp_tool_folder");
                loadedToolFolder = toolFolder;

                checkRequirements(toolFolder);

            } else if (!loadedToolFolder.equals(toolFolder)) {
                // the "scripts" package is already bound to the folder of another LEAPP tool
                throw new IllegalStateException(toolName + " cannot run in the same processing of the LEAPP tool"
                        + " installed in " + loadedToolFolder + ": they share the same python package names");
            }

            if (!interceptorsInstalled) {
                new LeappInterceptors().install(jep);
                interceptorsInstalled = true;
            }
        }

        // load all available plugins
        // (mimics https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/aleapp.py#L269)
        jep.exec("import scripts.plugin_loader");
        jep.exec("_iped_leapp_plugins = list(scripts.plugin_loader.PluginLoader().plugins)");

        @SuppressWarnings("unchecked")
        List<PyObject> availablePlugins = (List<PyObject>) jep.getValue("_iped_leapp_plugins");

        selectedPlugins = availablePlugins
                .stream()
                .map(PluginSpec::new)
                .filter(plugin -> isPluginIncluded(plugin.getModuleName()))
                .collect(Collectors.toMap(PluginSpec::getName, Function.identity()));

        // the PyObjects held by PluginSpec keep their own references: the temp global can be removed
        jep.exec("del _iped_leapp_plugins");

        jep.exec("import scripts.ilapfuncs");
        jep.exec("import scripts.context");

        // mimics https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/aleapp.py#L419
        jep.set("_iped_leapp_out_folder", outputFolder.toString());
        jep.exec("_iped_leapp_out_params = scripts.ilapfuncs.OutputParameters(_iped_leapp_out_folder, '"
                + toolName + "_Reports')");
        outputFolderBase = jep.getValue("_iped_leapp_out_params.output_folder_base", String.class);

        // The seeker MUST stage the files in the data folder known by the Context: plugins turn the staged paths
        // back into extraction paths with Context.get_relative_path(), which strips that folder.
        // Each worker has its own folders (the Context is thread-local), so workers never touch each other's files.
        exportFilesFolder = Paths.get(jep.getValue("_iped_leapp_out_params.data_folder", String.class));

        // the Context keeps the reference: the temp globals can be removed
        jep.exec("scripts.context.Context.set_output_params(_iped_leapp_out_params)");
        jep.exec("del _iped_leapp_out_params");
        jep.exec("del _iped_leapp_out_folder");
    }

    /**
     * Checks if the python packages the tool needs are installed, logging what is missing along with the command line
     * that installs it in the python environment in use. Plugins whose packages are installed still work, so this
     * does not disable the task.
     */
    @SuppressWarnings("unchecked")
    private void checkRequirements(String toolFolder) {
        String toolName = getTool().getDisplayName();
        try {
            LeappInterceptors.execPythonResource(jep, "check_requirements.py");
            jep.set("_iped_leapp_tool_folder", toolFolder);
            jep.exec("_iped_leapp_requirements = _iped_leapp_check_requirements(_iped_leapp_tool_folder)");
            Map<String, Object> result = (Map<String, Object>) jep.getValue("_iped_leapp_requirements");
            jep.exec("del _iped_leapp_requirements, _iped_leapp_tool_folder, _iped_leapp_check_requirements");

            List<String> missing = (List<String>) result.get("missing");
            List<String> mismatched = (List<String>) result.get("mismatched");
            String command = (String) result.get("command");

            if (!missing.isEmpty()) {
                logger.error("{} python requirements not installed: {}. The plugins depending on them will fail. To install them, run: {}",
                        toolName, String.join(", ", missing), command);
            }
            if (!mismatched.isEmpty()) {
                logger.warn("{} python requirements installed in a version other than the one it was tested with: {}. To install the required versions, run: {}",
                        toolName, String.join(", ", mismatched), command);
            }

            int[] minVersion = getTool().getMinPythonVersion();
            List<Number> version = (List<Number>) jep.getValue("list(__import__('sys').version_info[:2])");
            if (version.get(0).intValue() * 1000 + version.get(1).intValue() < minVersion[0] * 1000 + minVersion[1]) {
                logger.error("{} needs python {}.{} or newer, but python {}.{} is in use: plugins may fail to load.", toolName,
                        minVersion[0], minVersion[1], version.get(0), version.get(1));
            }
        } catch (Exception e) {
            logger.warn("Could not check {} python requirements", toolName, e);
        }
    }

    @Override
    public void process(IItem item) throws Exception {

        initialize();

        if (jep == null) {
            // python environment or tool not available (see initialize)
            return;
        }

        LeappTool tool = getTool();

        if (isExtractionRoot(item)) {
            processExtractionRoot(item);
        } else if (tool.mediaType(LeappTool.CASE_SUBTYPE).equals(item.getMediaType())) {
            processCaseEvidence(item);
        } else if (tool.mediaType(LeappTool.CATEGORY_SUBTYPE).equals(item.getMediaType())) {
            processCategoryEvidence(item);
        } else if (tool.mediaType(LeappTool.PLUGIN_RESULTS_SUBTYPE).equals(item.getMediaType())) {
            processPluginEvidence(item);
        } else if (tool.mediaType(LeappTool.DEVICE_INFO_SUBTYPE).equals(item.getMediaType())) {
            processDeviceInfoEvidence(item);
        } else if (tool.owns(item.getMediaType())) {
            // a row subitem coming back through the pipeline, with what was left to do after it got enqueued
            if (item.getTempAttribute(LEAPP_METADATA_PATHS) != null) {
                processDeferredFilePathLinks(item);
            }
            if (item.getTempAttribute(LEAPP_EMBEDDED_MEDIA) != null) {
                processEmbeddedMedia(item);
            }
        }
    }

    /**
     * Checks if the item is a file system item of a ufdr: the virtual folder UfedXmlReader creates for the "fs"
     * attribute of report.xml entries (e.g. "extraction-001.ufdr/EXTRACTION_FFS.zip"). The device paths
     * hang from it, so it is the extraction root, and not the ufdr item.
     */
    protected boolean isUfdrFileSystem(IItem evidence) {

        // file system items are always virtual folders, direct children of the ufdr item
        if (evidence.isRoot() || !evidence.isDir()
                || UfedXmlReader.DECODED_DATA_FOLDER_NAME.equals(evidence.getName())) {
            return false;
        }

        String path = evidence.getPath();
        String parentPath = StringUtils.substringBeforeLast(path, "/");
        if (StringUtils.isEmpty(parentPath) || parentPath.equals(path)) {
            return false;
        }

        // a ufdr processed as a subitem of another evidence keeps its file name in the path
        if (UFDR_EXT.equalsIgnoreCase(LeappUtils.getExtension(parentPath))) {
            return true;
        }

        // a ufdr processed as a case root may have been renamed via -dname, so check the data source
        // file instead, requiring a direct child of the root (whose path has no separator)
        IDataSource dataSource = evidence.getDataSource();
        File sourceFile = dataSource != null ? dataSource.getSourceFile() : null;

        return sourceFile != null && !StringUtils.contains(parentPath, '/')
                && UFDR_EXT.equalsIgnoreCase(LeappUtils.getExtension(sourceFile.getName()));
    }

    /**
     * Discards an item created by this task that ended up with nothing to show (e.g. a plugin with no results). It
     * never was part of the evidence, so it is not listed in the file list.
     */
    private void discard(IItem item) {
        item.setToIgnore(true);
        item.setTempAttribute(ExportCSVTask.SKIP_FILE_LIST, true);
    }

    private String extractionTypeMetaKey() {
        return getTool().getMetaPrefix() + "extractionType";
    }

    private String pluginKeyMetaKey() {
        return getTool().getPluginMetaPrefix() + "key";
    }

    private void processExtractionRoot(IItem rootEvidence) {

        if (selectedPlugins.isEmpty()) {
            return;
        }

        // creates a subitem to represent the tool report
        Item caseEvidence = (Item) rootEvidence.createChildItem();
        caseEvidence.setMediaType(getTool().mediaType(LeappTool.CASE_SUBTYPE));
        caseEvidence.setSumVolume(false);

        String name = getTool().getResultsItemName();
        caseEvidence.setName(name);
        caseEvidence.setPath(rootEvidence.getPath() + "/" + name);
        caseEvidence.setIdInDataSource("");
        caseEvidence.setHasChildren(true);
        caseEvidence.setExtraAttribute(BasicProps.TREENODE, true);
        caseEvidence.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

        caseEvidence.getMetadata().set(extractionTypeMetaKey(), getExtractionType(rootEvidence));

        worker.processNewItem(caseEvidence, ProcessTime.LATER);
    }

    private void processCaseEvidence(IItem caseEvidence) throws Exception {

        if (!isSupportedExtraction(caseEvidence, caseEvidence.getMetadata().get(extractionTypeMetaKey()))) {
            discard(caseEvidence);
            return;
        }

        LeappTool tool = getTool();
        Map<String, Item> categoryItems = new HashMap<>();

        boolean preferUfedData = EXTRACTION_TYPE_UFDR.equals(caseEvidence.getMetadata().get(extractionTypeMetaKey()))
                && getUfdrDecodingMode() == UfdrDecodingMode.EXTERNAL;
        // a model is usually equivalent to several plugins: each one is searched in the case only once
        Map<UfedModelRule, Boolean> decodedByUfed = new HashMap<>();

        // creates one subitem for each plugin execution
        // (PluginSpec getters call PyObject.getAttr on this worker's interpreter)
        for (PluginSpec plugin : selectedPlugins.values()) {

            if (preferUfedData) {
                UfedModelRule ufedModel = findUfedDecodedModel(caseEvidence, plugin, decodedByUfed);
                if (ufedModel != null) {
                    logger.info("Skipping {} {} plugin: {} already has '{}' models decoded by UFED", tool.getDisplayName(),
                            plugin.getName(), getUfdrPath(caseEvidence), ufedModel);
                    caseEvidence.getMetadata().add(tool.getMetaPrefix() + "pluginsDecodedByUfed",
                            plugin.getModuleName() + "/" + plugin.getName() + " (" + ufedModel + ")");
                    continue;
                }
            }

            Item categoryItem = categoryItems.computeIfAbsent(plugin.getCategory(), name -> {
                Item categoryEvidence = (Item) caseEvidence.createChildItem();
                categoryEvidence.setMediaType(tool.mediaType(LeappTool.CATEGORY_SUBTYPE));
                categoryEvidence.setSumVolume(false);

                categoryEvidence.setName(name);
                categoryEvidence.setExtension("");
                categoryEvidence.setPath(caseEvidence.getPath() + "/" + name);
                categoryEvidence.setIdInDataSource("");
                categoryEvidence.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

                worker.processNewItem(categoryEvidence, ProcessTime.LATER);

                return categoryEvidence;
            });

            Item pluginEvidence = (Item) categoryItem.createChildItem();
            pluginEvidence.setMediaType(tool.mediaType(LeappTool.PLUGIN_RESULTS_SUBTYPE));
            pluginEvidence.setSumVolume(false);
            pluginEvidence.setTempAttribute(LEAPP_PLUGIN_CATEGORY_KEY, categoryItem);

            String name = StringUtils.firstNonBlank((String) plugin.getArtifactInfo().get("name"), plugin.getName());
            pluginEvidence.setName(name);
            pluginEvidence.setExtension("");
            pluginEvidence.setPath(categoryItem.getPath() + "/" + name);
            pluginEvidence.setIdInDataSource("");
            pluginEvidence.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

            pluginEvidence.getMetadata().set(pluginKeyMetaKey(), plugin.getName());
            pluginEvidence.getMetadata().set(tool.getPluginMetaPrefix() + "moduleName", plugin.getModuleName());
            for (Entry<String, Object> entry : plugin.getArtifactInfo().entrySet()) {
                if (ARTIFACT_INFO_KEYS_TO_IGNORE.contains(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                pluginEvidence.getMetadata().set(tool.getPluginMetaPrefix() + entry.getKey(), entry.getValue().toString());
            }

            worker.processNewItem(pluginEvidence, ProcessTime.LATER);
        }

        // creates subitem to hold device info collected
        Item deviceInfoEvidence = (Item) caseEvidence.createChildItem();
        deviceInfoEvidence.setName(DEVICE_INFO_HTML);
        deviceInfoEvidence.setMediaType(tool.mediaType(LeappTool.DEVICE_INFO_SUBTYPE));
        deviceInfoEvidence.setSumVolume(false);
        deviceInfoEvidence.setPath(caseEvidence.getPath() + "/" + DEVICE_INFO_HTML);
        deviceInfoEvidence.setIdInDataSource("");
        worker.processNewItem(deviceInfoEvidence, ProcessTime.LATER);
    }

    /**
     * The in-case path of the ufdr item the given results item belongs to: the parent of its file system item, which
     * is the extraction root (see {@link #isUfdrFileSystem}).
     */
    private String getUfdrPath(IItem caseEvidence) {
        return StringUtils.substringBeforeLast(getExtractionRootPath(caseEvidence), "/");
    }

    /**
     * Looks, among the UFED models equivalent to the plugin, for one the ufdr really holds: UFED does not decode
     * everything in every extraction, so the plugin is skipped only when its data is known to be in the case already.
     *
     * @param decodedByUfed cache of the models already searched in this ufdr
     * @return the first model found, or null when the ufdr has none of them
     */
    private UfedModelRule findUfedDecodedModel(IItem caseEvidence, PluginSpec plugin,
            Map<UfedModelRule, Boolean> decodedByUfed) {

        List<UfedModelRule> models = getUfedModels(plugin.getModuleName(), plugin.getName());
        if (models.isEmpty()) {
            return null;
        }

        IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
        String ufdrPath = getUfdrPath(caseEvidence);

        for (UfedModelRule model : models) {
            if (decodedByUfed.computeIfAbsent(model, m -> m.existsIn(searcher, ufdrPath))) {
                return model;
            }
        }
        return null;
    }

    /**
     * The in-case path of the extraction root, i.e. the path prefix shared by every item of this extraction.
     *
     * @param leappItem the results item or any of its descendants
     */
    protected String getExtractionRootPath(IItem leappItem) {
        return StringUtils.substringBeforeLast(leappItem.getPath(), "/" + getTool().getResultsItemName());
    }

    /**
     * Locates the case item of a device path of the extraction the given results item belongs to, trying the path
     * aliases of the tool too. Returns null when there is none.
     */
    protected IItemReader findExtractionItem(IItem leappItem, String devicePath) {
        IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
        return LeappUtils.findItemByPath(searcher, getExtractionRootPath(leappItem), devicePath, getTool().getPathAliases());
    }

    private void processCategoryEvidence(IItem categoryEvidence) throws Exception {
        if (!categoryEvidence.hasChildren()) {
            logger.info("Ignoring {} category {}: no children", getTool().getDisplayName(), categoryEvidence.getName());
            discard(categoryEvidence);
        }
    }

    private void processPluginEvidence(IItem pluginEvidence) throws Exception {

        String toolName = getTool().getDisplayName();
        String pluginName = pluginEvidence.getMetadata().get(pluginKeyMetaKey());
        PluginSpec plugin = selectedPlugins.get(pluginName);
        if (plugin == null) {
            throw new IllegalStateException("Plugin should have been found: " + pluginName);
        }

        // look for the files the plugin needs
        // (mimics https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/aleapp.py#L567)
        IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
        FileSeeker seeker = new FileSeeker(getExtractionRootPath(pluginEvidence), exportFilesFolder, searcher);

        try {

            List<IItemReader> filesFound = seeker.searchItems(plugin.getSearchGlobs());

            if (filesFound.isEmpty()) {
                logger.debug("Ignoring {} {} plugin: no files found", toolName, pluginName);
                discard(pluginEvidence);
                return;
            }

            LeappContext.create(getTool(), seeker, worker, jep, plugin, pluginEvidence, filesFound);

            try {
                // mimics https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/aleapp.py#L597
                Path categoryFolder = Paths.get(outputFolderBase, "_HTML", plugin.getCategory());
                Files.createDirectories(categoryFolder);

                // export all files found (and -wal/-journal companions of sqlite items)
                ArrayList<String> filesFoundStringList = seeker.exportItems(filesFound);

                // https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/aleapp.py#L608
                callPlugin(plugin, filesFoundStringList, categoryFolder.toString(), seeker);

            } catch (Exception e) {
                logger.error("{} {} plugin ended prematurely: {}", toolName, pluginName, ExceptionUtils.getMessage(e));
                logger.warn(pluginName, e);
            }

            if (!pluginEvidence.hasChildren()) {
                logger.debug("Ignoring {} {} plugin: no children", toolName, pluginName);
                discard(pluginEvidence);
            }
        } finally {
            try {
                // clears this thread's Python Context state, mirroring the per-artifact
                // Context.clear() of the tool (state is thread-local, see context_thread_local_patch.py)
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

        // https://github.com/abrignoni/ALEAPP/blob/v2026.4.5/aleapp.py#L638
        jep.exec("import scripts.ilapfuncs");

        Path deviceInfoPath = Files.createTempFile("screen_output_file_path_devinfo", ".html");
        try {
            // OutputParameters attributes are class-level and identifiers is a module
            // global, both shared across all workers: serialize the path switch + write
            // against concurrent device_info() calls (see IlapfuncsDeviceInfoInterceptor)
            synchronized (DEVICE_INFO_LOCK) {
                jep.set("_iped_leapp_devinfo_path", deviceInfoPath.toString());
                jep.exec("scripts.ilapfuncs.OutputParameters.screen_output_file_path_devinfo = _iped_leapp_devinfo_path");
                jep.exec("del _iped_leapp_devinfo_path");
                jep.exec("scripts.ilapfuncs.write_device_info()");
            }

            byte[] deviceInfoBytes = Files.readAllBytes(deviceInfoPath);

            if (deviceInfoBytes.length > 0) {
                ExportFileTask.getLastInstance().insertIntoStorage(deviceInfoEvidence, deviceInfoBytes, deviceInfoBytes.length);
            } else {
                discard(deviceInfoEvidence);
            }
        } finally {
            Files.deleteIfExists(deviceInfoPath);
        }
    }

    /**
     * Resolves the deferred file-path links of a plugin row subitem. The results processor recorded the
     * "&lt;tool&gt;:&lt;header&gt;" metadata keys whose values are device file paths in the
     * {@link #LEAPP_METADATA_PATHS} temp attribute; here each value is resolved to its case item (via
     * {@link LeappUtils#findItemByPath}) and added to LINKED_ITEMS. Doing it here (instead of inside the plugin call)
     * spreads the one-search-per-path cost across the worker pool.
     */
    private void processDeferredFilePathLinks(IItem item) {

        @SuppressWarnings("unchecked")
        List<String> metadataKeys = (List<String>) item.getTempAttribute(LEAPP_METADATA_PATHS);

        for (String metadataKey : metadataKeys) {
            String pathValue = StringUtils.trimToEmpty(item.getMetadata().get(metadataKey));
            if (pathValue.isEmpty() || pathValue.equals("/")) {
                continue;
            }
            IItemReader found = findExtractionItem(item, pathValue);
            if (found != null) {
                item.getMetadata().add(ExtraProperties.LINKED_ITEMS,
                        ExtraProperties.GLOBAL_ID + ":" + found.getExtraAttribute(ExtraProperties.GLOBAL_ID));
            }
        }
    }

    /**
     * Creates the child items holding the embedded media of a row subitem: data that is not a file of the extraction,
     * but stored inside one (e.g. a picture kept in a database blob). Their type is not set: they go through the whole
     * processing pipeline, as any file extracted from a container does.
     */
    private void processEmbeddedMedia(IItem rowItem) {

        @SuppressWarnings("unchecked")
        Map<String, byte[]> embeddedMedia = (Map<String, byte[]>) rowItem.getTempAttribute(LEAPP_EMBEDDED_MEDIA);
        // releases the data as soon as it is stored
        rowItem.setTempAttribute(LEAPP_EMBEDDED_MEDIA, null);

        int subitemId = 0;
        for (Entry<String, byte[]> media : embeddedMedia.entrySet()) {
            String name = media.getKey();
            try {
                Item mediaItem = (Item) rowItem.createChildItem();
                mediaItem.setName(name);
                mediaItem.setPath(rowItem.getPath() + "/" + name);
                mediaItem.setIdInDataSource("");
                mediaItem.setSubItem(true);
                mediaItem.setSubitemId(subitemId++);
                mediaItem.setSumVolume(false);

                ExportFileTask.getLastInstance().insertIntoStorage(mediaItem, media.getValue(), media.getValue().length);
                worker.processNewItem(mediaItem, ProcessTime.LATER);

            } catch (Exception e) {
                // losing one media must not lose the row
                logger.warn("Failed to create embedded media {} of {}", name, rowItem.getPath(), e);
            }
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
