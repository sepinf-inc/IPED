package iped.engine.task.leappbridge;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.SystemUtils;
import org.apache.lucene.document.Document;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ALeappConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.search.IPEDSearcher;
import iped.engine.task.AbstractPythonTask;
import iped.engine.task.ExportFileTask;
import iped.engine.task.index.IndexItem;
import iped.engine.util.ParentInfo;
import iped.parsers.android.backup.AndroidBackupParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.SearchResult;
import iped.utils.DateUtil;
import iped.utils.pythonhook.FileHook;
import iped.utils.pythonhook.PythonHook;
import jep.Jep;
import jep.JepException;

/**
 * @author Patrick Dalla Bernardina <patrick.dalla@gmail.com>
 * 
 *         Class that implement an IPED task that will call ALeapp python
 *         plugins to be executed against IPED processed found evidences. The
 *         artifacts found by ALeapp will be extracted.
 */
public class LeappBridgeTask extends AbstractPythonTask {

    public static AtomicInteger pluginTimeCounter = new AtomicInteger(0);
    public static AtomicInteger pluginSearchCounter = new AtomicInteger(0);

    protected static Logger logger = LoggerFactory.getLogger(LeappBridgeTask.class);

    public static MediaType DEVICEDETAILS = MediaType.application("x-leapp-devicedetails");

    private static final String DEVICE_DETAILS_HTML = "DeviceDetails.html";

    public static final MediaType ALEAPP_DUMP_REPORT_MEDIATYPE = MediaType.application("x-aleapp-dump-report");
    public static final MediaType ALEAPP_ANDROID_BACKUP_REPORT_MEDIATYPE = MediaType
            .application("x-aleapp-android-backup-report");

    public static final String ALEAPP_METADATA_PREFIX = "ALEAPP";
    public static final String ALEAPP_ISREPORT = ALEAPP_METADATA_PREFIX + ":isReport";
    public static final String ALEAPP_ISPLUGIN = ALEAPP_METADATA_PREFIX + ":isPlugin";
    public static final String ALEAPP_PLUGIN = ALEAPP_METADATA_PREFIX + ":PLUGIN";
    public static final String ALEAPP_ISARTIFACT = ALEAPP_METADATA_PREFIX + ":isArtifact";

    static final String REPORT_EVIDENCE_NAME = "LEAPP_Reports";
    static final String REPORT_FOLDER_NAME = "LEAPP_Reports_";

    private static final String ALEAPP_DEVICE_DETAILS = ALEAPP_METADATA_PREFIX + "DEVICE_DETAILS";

    private static final String PLUGIN_EXECUTION_MESSAGE = "ALeapp plugin execution";

    static AtomicInteger taskCount = new AtomicInteger(0);

    HashMap<String, Item> mappedEvidences = new HashMap<String, Item>();

    public LeappBridgeTask() {
    }

    static ALeappPluginsManager pluginsManager = new ALeappPluginsManager();

    private File tmpFileRef;
    private File reportPath;

    private ExecutorService service;

    //map between filesFound paths and correspondent Lucene Documents
    private HashMap<String, Document> filesFoundDocuments;

    private ALeappConfig config;

    static private File aleappDir;

    static private File tmp;

    static private int START_QUEUE_PRIORITY = 2;

    public static Object open(Collection args, Map kargs) {
        Iterator iargs = args.iterator();
        return new FileHook(iargs.next().toString());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        int incremented = taskCount.incrementAndGet();

        config = (ALeappConfig) configurationManager.findObject(ALeappConfig.class);

        moduleName = "JLeapp";
        if (incremented == 1) {
            super.init(configurationManager);
        }
        
        Jep jep = super.getJep();

        File aleappPath = getAleappScriptsDir();
        File scriptsPath = new File(aleappPath, "scripts");
        File artifactsPath = new File(scriptsPath, "artifacts");
        if (artifactsPath.exists()) {
            jep.eval("sys.path.append('" + preparePythonLiteralPath(aleappPath.getCanonicalPath()) + "')");
            jep.eval("sys.path.append('" + preparePythonLiteralPath(scriptsPath.getCanonicalPath()) + "')");
            jep.eval("sys.path.append('" + preparePythonLiteralPath(artifactsPath.getCanonicalPath()) + "')");
            jep.eval("from geopy.geocoders import Nominatim");

            Ilapfuncs.install(jep);
            PythonHook pt = PythonHook.installHook(jep);
            pt.wrapsClass("scripts.artifact_report", "ArtifactHtmlReport", ArtifactJavaReport.class);

            pluginsManager.init(jep, getAleappScriptsDir());
        } else {
            throw new Exception("ALeapp plugin scripts path not found:" + artifactsPath.getCanonicalPath());
        }
    }

    @Override
    public void finish() throws Exception {
        int decremented = taskCount.decrementAndGet();
        if (decremented == 0) {
            logger.warn("ALeapp total plugin files search time:" + pluginSearchCounter.get());
            logger.warn("ALeapp total plugin execution time:" + pluginTimeCounter.get());
        }
    }

    //gets modified time from lucene document properties
    public double getmtime(String file) {
        Document doc = filesFoundDocuments.get(file);
        String mtime = doc.get(BasicProps.MODIFIED);
        try {
            return DateUtil.stringToDate(mtime).getTime() / 1000;
        } catch (ParseException e) {
            return 0;
        }
    }

    public void executePlugin(IItem evidence, LeapArtifactsPlugin p, List<String> filesFound, File reportDumpPath) {
        Jep jep = getJep();
        Date plugginStart = new Date();
        try {
            // some plugins depend on a sorted list
            Collections.sort(filesFound);

            StringBuffer list = new StringBuffer("[");
            for (String fileFound : filesFound) {
                list.append(" '" + fileFound + "',");
            }
            String lists = list.toString().substring(0, list.length() - 1) + "]";
            try {
                jep.eval("from scripts.search_files import FileSeekerBase");
                jep.eval("import sys");
                jep.eval("from java.lang import System");
                jep.eval("from scripts.ilapfuncs import OutputParameters");
                jep.eval("from scripts.ilapfuncs import logfunc");

                File scriptsDir = new File(getAleappScriptsDir(), "scripts");

                jep.eval("sys.path.append('" + preparePythonLiteralPath(scriptsDir.getCanonicalPath()) + "')");

                jep.eval("import scripts.artifact_report");
                jep.eval("from multiprocessing import Process");
                jep.eval("import os");
                jep.eval("import sys");
                jep.eval("from java.lang import System");
                jep.eval("from iped.engine.task.leappbridge import ArtifactJavaReport");

                if (p.getMethodName().contains("lambda")) {
                    jep.eval("from " + p.getModuleName() + " import *");
                    jep.set("parse", p.getMethod());
                } else {
                    jep.eval("from " + p.getModuleName() + " import " + p.getMethodName() + " as parse");
                }


                // creates a dumb file seeker. some plugins refers to directory although not
                // using it.
                jep.eval("dumb = FileSeekerBase()");
                jep.eval("dumb.directory='" + reportPath.getCanonicalPath().replace("\\", "\\\\") + "'");

                jep.set("evidence", evidence);
                jep.set("worker", worker);
                jep.set("reportDumpPath", reportDumpPath);
                jep.set("reportPath", reportPath);
                jep.set("leappTask", this);

                //overrides getmtime to get modified tipe from lucene document
                jep.eval("os.path.old_iped_getmtime = os.path.getmtime");
                try {
                    jep.eval("os.path.getmtime = leappTask.getmtime"); // overrides getmtime to get last modified time
                                                                       // from
                    // processed item evidence

                    jep.set("moduleDir", this.output);
                    jep.set("pluginName", p.getModuleName());

                    jep.set("mappedEvidences", mappedEvidences);

                    jep.eval("logfunc('" + PLUGIN_EXECUTION_MESSAGE + ":" + p.getModuleName() + "')");
                    jep.eval("parse(" + lists + ",'"
                            + reportPath.getCanonicalPath().replace("\\", "\\\\") + "',dumb,True,'UTC')");
                } finally {
                    // restore overriden method
                    jep.eval("os.path.getmtime = os.path.old_iped_getmtime");
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
            }

        } finally {
            Date  plugginEnd = new Date();
            long delta = plugginEnd.getTime() - plugginStart.getTime();
            pluginTimeCounter.addAndGet((int) delta);
            logger.warn("ALeapp plugin "+p.getName()+" execution time:"+delta);
            
            // jep.close();
        }
    }

    private File getAleappScriptsDir() {
        if (aleappDir == null) {
            if (config.getAleapScriptsDir() != null) {
                aleappDir = new File(config.getAleapScriptsDir());
            } else {
                File pythonDir = new File(Configuration.getInstance().appRoot, "tools");
                aleappDir = new File(pythonDir, "ALEAPP");
            }

            try {
                logger.info("ALeapp scripts dir:" + aleappDir.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return aleappDir;
    }

    @Override
    public String getName() {
        return "LeappBridgeTask";
    }

    static HashSet<String> dumpStartFolderNames = new HashSet<String>();
    static {
        dumpStartFolderNames.add("Dump");
        dumpStartFolderNames.add("backup");
    };

    @Override
    public void process(IItem evidence) throws Exception {
        String realName = evidence.getName();
        if (evidence.isRoot()) {
            // if evidence is root, its realname can be changed via -dname parameter, so we
            // need to get it from other source.
            realName = evidence.getDataSource().getSourceFile().getName();
        }
        // first rule to check a supposed android Dump folder or android backup
        if (dumpStartFolderNames.contains(realName)
                || AndroidBackupParser.SUPPORTED_TYPES.contains(evidence.getMediaType())) {
            // if true, creates a subitem to represent the ALeapp report
            Item subItem = (Item) evidence.createChildItem();
            ParentInfo parentInfo = new ParentInfo(evidence);

            String name = REPORT_EVIDENCE_NAME;
            subItem.setName(name);
            subItem.setPath(parentInfo.getPath() + "/" + name);
            subItem.setSubItem(true);
            subItem.setSubitemId(1);
            subItem.setHasChildren(true);
            subItem.getMetadata().set(ALEAPP_ISREPORT, "true");
            subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

            if (AndroidBackupParser.SUPPORTED_TYPES.contains(evidence.getMediaType())) {
                subItem.setMediaType(ALEAPP_ANDROID_BACKUP_REPORT_MEDIATYPE);
            } else if (dumpStartFolderNames.contains(evidence.getName())) {
                subItem.setMediaType(ALEAPP_DUMP_REPORT_MEDIATYPE);
            }

            worker.processNewItem(subItem);
        }

        // if the item is a supposed ALeapp report, checks if the current queue
        // priority is the correct to process the report
        String isReportStr = evidence.getMetadata().get(ALEAPP_ISREPORT);
        boolean isReport = (isReportStr != null && isReportStr.equals("true"));
        if (isReport) {
            int priority = worker.manager.getProcessingQueues().getCurrentQueuePriority();
            if (priority < START_QUEUE_PRIORITY) {
                reEnqueueItem(evidence, START_QUEUE_PRIORITY);
                throw new ItemReEnqueuedException();
            }
        }

        if (isReport) {
            // check additional rules to confirm that the item is inside an Android Dump
            // Folder
            if (ALEAPP_ANDROID_BACKUP_REPORT_MEDIATYPE.equals(evidence.getMediaType()) || isInsideRealDump(evidence)) {
                ParentInfo parentInfo = new ParentInfo(evidence);

                // creates one subitem for each plugin execution
                for (LeapArtifactsPlugin p : pluginsManager.getPlugins()) {
                    if (config.isPluginIncluded(p.getModuleName())) {
                        Item psubItem = (Item) evidence.createChildItem();

                        String moduleName = p.moduleName;
                        psubItem.setName(moduleName);
                        psubItem.setPath(parentInfo.getPath() + "/" + moduleName);
                        psubItem.setSubItem(true);
                        psubItem.setSubitemId(1);
                        psubItem.getMetadata().set(ALEAPP_PLUGIN, moduleName);
                        psubItem.getMetadata().set(ALEAPP_ISPLUGIN, "true");
                        psubItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
                        worker.processNewItem(psubItem);
                    }
                }

                // creates subitem to hold device info collected
                Item devDetailsSubItem = (Item) evidence.createChildItem();
                ParentInfo pparentInfo = new ParentInfo(evidence);
                devDetailsSubItem.setName(DEVICE_DETAILS_HTML);
                devDetailsSubItem.setMediaType(DEVICEDETAILS);
                devDetailsSubItem.setPath(parentInfo.getPath() + "/" + DEVICE_DETAILS_HTML);
                devDetailsSubItem.setSubItem(true);
                devDetailsSubItem.setSubitemId(1);
                devDetailsSubItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
                worker.processNewItem(devDetailsSubItem);
            } else {
                // ignores the item create to represent ALeapp report, as it wasn't confirmed
                // that it was created inside an Android Dump Folder
                evidence.setToIgnore(true);
            }
        }

        // if item is an plugin, checks if the current queue priority
        // is the correct to process the plugin
        String isPlugin = evidence.getMetadata().get(ALEAPP_ISPLUGIN);
        if (isPlugin != null && isPlugin.equals("true")) {
            int priority = worker.manager.getProcessingQueues().getCurrentQueuePriority();
            if (priority < START_QUEUE_PRIORITY + 1) {
                reEnqueueItem(evidence, START_QUEUE_PRIORITY + 1);
                throw new ItemReEnqueuedException();
            } else {
                String pluginName = evidence.getMetadata().get(ALEAPP_PLUGIN);
                LeapArtifactsPlugin p = pluginsManager.getPlugin(pluginName);
                processEvidence(evidence, p);
            }
        }
        
        // the device details must be the last to process as it gets output from every
        // plugin execution
        MediaType mt = evidence.getMediaType();
        if (mt != null && mt.equals(DEVICEDETAILS)) {
            int priority = worker.manager.getProcessingQueues().getCurrentQueuePriority();
            if (priority < START_QUEUE_PRIORITY + 2) {
                reEnqueueItem(evidence, START_QUEUE_PRIORITY + 2);
                throw new ItemReEnqueuedException();
            } else {
                processDeviceDetails(evidence);
            }
        }

        String isArtifact = evidence.getMetadata().get(ALEAPP_ISARTIFACT);
        if (isArtifact != null && isArtifact.equals("true")) {
            processClassificationAndMappings(evidence);
        }
    }

    private void processDeviceDetails(IItem evidence) {
        /*
         * Leapp declares static references to temporary "device details" html
         * OutputParameters. This causes a new concurrent plugin execution to overwrite
         * this static variable. So, there is no garantee that the content become
         * scrambled. It is not a problem though, as all this writings are to be merged
         * anyway in a single DeviceInfo.html.
         */
        Integer leappRepEvidence = evidence.getParentId();
        StringBuffer stringBuffer = Ilapfuncs.getDeviceInfoBuffer(leappRepEvidence);

        if (stringBuffer != null) {
            try {
                ExportFileTask extractor = new ExportFileTask();
                extractor.setWorker(worker);
                StringBuffer sb = new StringBuffer();
                sb.append("<html><body>");
                sb.append(stringBuffer);
                sb.append("</body></html>");
                // export thumb data to internal database
                ByteArrayInputStream is = new ByteArrayInputStream(sb.toString().getBytes());
                extractor.extractFile(is, evidence, evidence.getLength());

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // if there is no device detail, ignores next processing removing the item from
            // the case
            evidence.setToIgnore(true);
        }
    }

    static private void moveDir(File fromDir, File toDir) throws IOException {
        toDir.mkdirs();
        // moves the directory
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(fromDir.toPath())) {
            for (Path child : ds) {
                if (Files.isDirectory(child)) {
                    Path targetFile = toDir.toPath().resolve(child.getFileName());
                    moveDir(child.toFile(), targetFile.toFile());
                } else {
                    Path targetFile = toDir.toPath().resolve(child.getFileName());
                    Files.move(child, targetFile);
                }
            }
        }
    }

    static HashSet<LeapArtifactsPlugin> processedPlugins = new HashSet<LeapArtifactsPlugin>();

    static public String preparePythonLiteralPath(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            // prepares to file path str to be used as a string literal inside python
            return path.replace("\\", "\\\\");
        } else {
            // does not makes the replacement as the input does not uses "\" as path
            // separator avoiding unnecessary CPU usage
            return path;
        }
    }

    /*
     * Rules to check if evidence corresponds to an android Dump from where to start
     * executing ALeapp plugins
     */
    private boolean isInsideRealDump(IItem reportEvidence) {
        if(ipedCase==null){
            ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer);
        }

        String checkFolder = "/data/data/com.android.vending";

        String parentPath = reportEvidence.getPath();
        parentPath = parentPath.substring(0, parentPath.length() - (REPORT_EVIDENCE_NAME.length() + 1));

        IPEDSearcher filesSearcher = new IPEDSearcher(ipedCase);
        String query = "path:\"" + parentPath + checkFolder + "\"";

        filesSearcher.setQuery(query);
        try {
            SearchResult filesResult = filesSearcher.search();
            if (filesResult.getLength() > 0) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            // ignores
        }

        return false;

    }

    /**
     * Find the evidence files that matches the plugin's patterns and calls plugin
     * execution.
     * 
     * @param p
     *            Plugin to execute
     * @param evidence
     *            Plugin evidence item.
     * @param dumpEvidence
     *            Dump evidence root, so the query will apply only to its children.
     * @param reportDumpPath
     *            Complete path to the effective source report dump folder. If IPED
     *            processing is against a Folder the path will be this folder.
     *            Otherwise, if IPED processing is against a ZIP, E01, or any
     *            container, this parameter must contain a path to
     */
    private void processPlugin(LeapArtifactsPlugin p, IItem evidence, IItem dumpEvidence, File reportDumpPath)
            throws IOException {
        List<String> filesFound = new ArrayList<String>();

        try {
            boolean temporaryReportDumpPath = false;

            filesFoundDocuments = new HashMap<String, Document>();

            Date plugginFileSearchStart = new Date();

            try {
                String dumpPath = dumpEvidence.getPath();

                // find files on dump that is needed by the plugin and exports them
                // to tmp folder if needed. ALeapp plugins will work on
                // these tmp copies of the files.
                for (String pattern : p.patterns) {
                    IPEDSearcher filesSearcher = new IPEDSearcher(ipedCase);
                    
                    
                    String query = patternToLuceneQuery(dumpEvidence, pattern);
                    filesSearcher.setQuery(query);
                    SearchResult filesResult = filesSearcher.search();
                    for (int j = 0; j < filesResult.getLength(); j++) {
                        int artLuceneId = ipedCase.getLuceneId(filesResult.getId(j));
                        Document artdoc = ipedCase.getReader().document(artLuceneId);
                        String decoded = artdoc.get(iped.properties.ExtraProperties.DECODED_DATA);
                        if (decoded == null || !decoded.equals("true")) {
                            // only raw files are expected by ALeapp plugin (not iped extracted items)
                            String artpath = artdoc.get(BasicProps.PATH).substring(dumpPath.length());

                            if (!artpath.startsWith(">>") && artpath.contains(">>")) {
                                // item is a decoded data, so it is not the source of the informations
                                continue;
                            }

                            artpath = replaceSpecialChars(artpath);

                            if (pluginsManager.hasPatternMatch(artpath, p)) {
                                IItem item = ipedCase.getItemByLuceneID(artLuceneId);
                                File tmp = item.getTempFile();

                                String sourcePath = new File(
                                        ipedCase.getCaseDir() + "/" + artdoc.get(IndexItem.SOURCE_PATH))
                                                .getCanonicalPath();


                                if (tmp.getCanonicalPath().startsWith(sourcePath)) {
                                    reportDumpPath = new File(sourcePath);
                                    // the file returned by getTempFile() is the file itself
                                    String fileStr = preparePythonLiteralPath(tmp.getCanonicalPath());
                                    filesFound.add(fileStr);
                                    filesFoundDocuments.put(fileStr, artdoc);
                                } else {
                                    // the file returned by getTempFile() is a copy to the file in a temp folder
                                    // so recreate the path structure inside the temp folder
                                    // and move it accordingly to be recognizable by
                                    // ALeapp scripts
                                    String artParentPath = artpath.substring(0, artpath.lastIndexOf("/"));
                                    String artname = artpath.substring(artParentPath.length());
                                    File artfolder = new File(reportDumpPath, artParentPath);
                                    artfolder.mkdirs();

                                    try {
                                        File file_found = new File(artfolder, artname);
                                        if (!file_found.exists()) {
                                            // if the file wasn't already placed by prior iterations, move it

                                            file_found.getParentFile().mkdirs();
                                            // try to move if exception is thrown on symbolic link creation
                                            if (!tmp.isDirectory()) {
                                                Files.move(tmp.toPath(), file_found.toPath());
                                            } else {
                                                moveDir(tmp, file_found);
                                            }
                                        }
                                        String fileStr = preparePythonLiteralPath(file_found.getCanonicalPath());
                                        filesFound.add(fileStr);
                                        filesFoundDocuments.put(fileStr, artdoc);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                            }
                        }
                    }
                }
            } finally {
                Date plugginFileSearchEnd = new Date();
                long delta = plugginFileSearchEnd.getTime() - plugginFileSearchStart.getTime();
                pluginSearchCounter.addAndGet((int) delta);
                logger.warn("ALeapp plugin " + p.getName() + " files search time:" + delta);
            }

            if (filesFound.size() <= 0) {
                evidence.setToIgnore(true);
                return;
            } else {
                Metadata m = evidence.getMetadata();
                for (String file : filesFound) {
                    String filel = file.substring(preparePythonLiteralPath(reportDumpPath.getCanonicalPath()).length());
                    filel = prepareIPEDLiteralPath(filel);
                    String filename = filel.substring(filel.lastIndexOf("/") + 1);
                    m.add(ExtraProperties.LINKED_ITEMS, "path:\"*" + filel + "\" && name:\"" + filename + "\"");

                }

                executePlugin(evidence, p, filesFound, reportDumpPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    private String prepareIPEDLiteralPath(String filel) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return filel.replace("\\\\", "/");
        } else {
            return filel;
        }
    }

    static HashMap<String, String> escapedFiles = new HashMap<String, String>();

    synchronized static public String replaceSpecialChars(String artpath) {
        String escaped = artpath.replaceAll("[:*?\"<>|]", "_");
        if (!escaped.equals(artpath)) {
            escapedFiles.put(escaped, artpath);
            return escaped;
        }
        return artpath;
    }

    synchronized static public String revertSpecialChars(String escaped) {
        if (escapedFiles.containsKey(escaped)) {
            return escapedFiles.get(escaped);
        } else {
            return escaped;
        }
    }

    /*
     * Process the ALeapp plugin specified, represented by the evidence passed as
     * parameter
     */
    private void processEvidence(IItem evidence, LeapArtifactsPlugin p) {
        try {
            ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer);

            TemporaryResources tmpResources = new TemporaryResources();
            tmpFileRef = tmpResources.createTemporaryFile();
            tmpFileRef.deleteOnExit();
            reportPath = new File(tmpFileRef.getParentFile().getParentFile().getAbsolutePath(),
                    REPORT_FOLDER_NAME + tmpFileRef.getName());
            reportPath.mkdirs();
            reportPath.deleteOnExit();

            File reportDumpPath = new File(reportPath, "Dump");
            reportDumpPath.mkdirs();
            reportDumpPath.deleteOnExit();

            IItem leappRepEvidence = ipedCase.getItemByID(evidence.getParentId());
            IItem dumpEvidence = ipedCase.getItemByID(leappRepEvidence.getParentId());

            processPlugin(p, evidence, dumpEvidence, reportDumpPath);

            if (!evidence.hasChildren()) {
                evidence.setToIgnore(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }

    @Override
    protected void loadScript(Jep jep, boolean init) throws JepException {
        if (jep == null) {
            return;
        }

        setGlobalVars(jep);

        jep.eval("import sys");
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        ArrayList<Configurable<?>> c = new ArrayList<Configurable<?>>();
        c.add(new ALeappConfig());
        return c;
    }

    private void processClassificationAndMappings(IItem e) {
        try {
            Metadata m = e.getMetadata();
            String pluginName = m.get(ALEAPP_PLUGIN);

            if (pluginName.equals("accounts_ce") || pluginName.equals("accounts_de")) {
                e.setCategory("User Accounts");
                return;
            }
            if (pluginName.equals("accounts_ce_authtokens")) {
                e.setCategory("Passwords");
                return;
            }
            if (pluginName.equals("siminfo")) {
                e.setCategory("SIM Data");
                return;
            }
            if (pluginName.equals("Cello")) {
                e.setCategory("GDrive File Entries");
                return;
            }
            if (pluginName.equals("roles")) {
                e.setCategory("AppRoles");
                return;
            }
            if (pluginName.equals("frosting")) {
                e.setCategory("Update information");
                return;
            }
            if (pluginName.equals("smsmms")) {
                String type = m.get("ALEAPP:Type");
                String thisPhone = "This phone";
                if (type != null && type.equals("Received")) {
                    m.add("Communitactions:TO", thisPhone);
                    m.add("Communitactions:FROM", m.get("ALEAPP:Address"));
                } else {
                    if (type != null && type.equals("Sent")) {
                        m.add("Communitactions:TO", m.get("ALEAPP:Address"));
                        m.add("Communitactions:FROM", thisPhone);
                    }
                    // other types are DRAFT, OUTBOX, QUEUED and Failed
                }
            }
            String mime = e.getMediaType().toString();
            if (pluginName.equals("gmailEmails")) {
                if (mime.contains("appemails")) {
                    e.setCategory("Emails");
                    m.add("Communitactions:TO", m.get("ALEAPP:To"));
                    String from = m.get("ALEAPP:FROM");
                    if (from == null || from.isBlank()) {
                        from = m.get("ALEAPP:Reply To");
                    }
                    m.add("Communitactions:FROM", from);
                }
                return;
            }
            if (pluginName.equals("FacebookMessenger")) {
                if (mime.contains("contacts")) {
                    e.setCategory("Contacts");
                }
                if (mime.contains("userid")) {
                    e.setCategory("User Accounts");
                }
                if (mime.contains("chats")) {
                    e.setCategory("Instant Messages");
                    var linked = m.get("linkedItems");
                    var start = linked.indexOf("msys_database_") + 14;
                    var uid = linked.substring(start, linked.indexOf("\"", start));
                    if (m.get("ALEAPP:Sender ID").equals(uid)) {
                        m.add(ExtraProperties.COMMUNICATION_FROM, uid);
                        m.add(ExtraProperties.COMMUNICATION_TO, m.get("ALEAPP:Thread Key"));
                    } else {
                        m.add(ExtraProperties.COMMUNICATION_FROM, m.get("ALEAPP:Sender ID"));
                        m.add(ExtraProperties.COMMUNICATION_TO, uid);
                    }
                }
                return;
            }
            if (pluginName.equals("settingsSecure")) {
                String name = m.get("ALEAPP:Name");
                String value = m.get("ALEAPP:Value");
                if ((name == "bluetooth_address") || (name == "bluetooth_name")) {
                    e.setCategory("Bluetooth Devices");
                }
                return;
            }
            if (pluginName.equals("chromeCookies")) {
                e.setCategory("Cookies");
            }
        } catch (Exception ex) {
            logger.debug("Exception while trying to classify evidence:" + e.getName());
            ex.printStackTrace();
        }
    }

    /**
     * Get a query to filter out lucene results to be tested based on the path
     * pattern.
     * 
     * @param dumpEvidence
     *            Dump evidence root, so the query will apply only to its children.
     * @param pattern
     *            GLOB search Pattern of the items to be searched
     */
    private String patternToLuceneQuery(IItem dumpEvidence, String pattern) {
        String query = "path:\"" + dumpEvidence.getPath() + "\"";

        StringTokenizer st = new StringTokenizer(pattern, "/");
        String token = null;
        String field = "path:";
        while (st.hasMoreTokens()) {
            token = st.nextToken();
            if (!st.hasMoreTokens()) {
                field = "name:";// for last token we use the name field
            }
            if (!token.contains("*")) {
                query += " && " + field + "\"" + token + "\"";
            } else {
                StringTokenizer st2 = new StringTokenizer(token, "*");
                String token2 = null;
                while (st2.hasMoreTokens()) {
                    token2 = st2.nextToken();
                    String value = token2;
                    if (field.equals("name:")) {
                        int index = token2.lastIndexOf(".");
                        if (index >= 0) {// extension found
                            value = token2.substring(0, index);
                            String ext = token2.substring(index + 1);
                            String parts[] = ext.split("[^a-zA-Z0-9]");
                            for (String part : parts) {
                                query += " && ext:*" + part + "*";
                            }
                        }
                    }
                    String parts[] = value.split("[^a-zA-Z0-9]");
                    for (String part : parts) {
                        query += " && " + field + "*" + part + "*";
                    }
                }
            }
        }
        return query;
    }
}