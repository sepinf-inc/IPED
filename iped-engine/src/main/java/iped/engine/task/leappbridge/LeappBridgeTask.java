package iped.engine.task.leappbridge;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.document.Document;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

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
import iped.parsers.python.PythonParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.SearchResult;
import iped.utils.pythonhook.PythonHook;
import jep.Jep;
import jep.JepException;

public class LeappBridgeTask extends AbstractPythonTask {

    public static Logger logger = LoggerFactory.getLogger(LeappBridgeTask.class);

    public static MediaType DEVICEDETAILS = MediaType.application("x-leapp-devicedetails");

    private static final String DEVICE_DETAILS_HTML = "DeviceDetails.html";

    private static final String ALEAPP_PLUGIN = "ALEAPP:PLUGIN";

    static final String REPORT_EVIDENCE_NAME = "ALeapp_Report";

    private static final String ALEAPP_DEVICE_DETAILS = "ALEAPP:DEVICE_DETAILS";

    private static final String PLUGIN_EXECUTION_MESSAGE = "ALeapp plugin execution";

    static AtomicInteger count = new AtomicInteger(0);

    HashMap<String, Item> mappedEvidences = new HashMap<String, Item>();

    static HashMap<Integer, StringBuffer> devInfoBuffers = new HashMap<Integer, StringBuffer>();

    public LeappBridgeTask() {
    }

    static ALeappPluginsManager pluginsManager = new ALeappPluginsManager();

    private File tmpFileRef;
    private File reportPath;

    private Future<Jep> fjep;

    private ExecutorService service;

    private File aleappDir;

    static private File tmp;

    static private int START_QUEUE_PRIORITY = 3;

    @Override
    public List<Configurable<?>> getConfigurables() {
        ArrayList<Configurable<?>> c = new ArrayList<Configurable<?>>();
        c.add(new ALeappConfig());
        return c;
    }

    public static void logfunc(String message) {
        logger.info(message);
    }

    public static void logdevinfo(String message) {
        Jep jep = PythonParser.getJep();
        Item evidence = (Item) jep.getValue("evidence");
        int leappRepEvidence = evidence.getParentId();
        StringBuffer stringBuffer;
        synchronized (devInfoBuffers) {
            stringBuffer = devInfoBuffers.get(leappRepEvidence);
            if (stringBuffer == null) {
                stringBuffer = new StringBuffer();
                devInfoBuffers.put(leappRepEvidence, stringBuffer);
            }
        }
        synchronized (stringBuffer) {
            stringBuffer.append(message + "<br/>");
        }
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        int incremented = count.incrementAndGet();
        moduleName = "JLeapp";
        if (incremented == 1) {
            super.init(configurationManager);
        }

        Jep jep = super.getJep();

        File aleappPath = getAleappScriptsDir();
        File scriptsPath = new File(aleappPath, "scripts");
        File artifactsPath = new File(scriptsPath, "artifacts");
        if (artifactsPath.exists()) {
            jep.eval("sys.path.append('" + aleappPath.getCanonicalPath().replace("\\", "\\\\") + "')");
            jep.eval("sys.path.append('" + scriptsPath.getCanonicalPath().replace("\\", "\\\\") + "')");
            jep.eval("sys.path.append('" + artifactsPath.getCanonicalPath().replace("\\", "\\\\") + "')");
            jep.eval("from geopy.geocoders import Nominatim");

            PythonHook pt = new PythonHook(jep);
            pt.overrideModuleFunction("scripts.ilapfuncs", "logfunc",
                    LeappBridgeTask.class.getMethod("logfunc", String.class));
            pt.overrideModuleFunction("scripts.ilapfuncs", "logdevinfo",
                    LeappBridgeTask.class.getMethod("logdevinfo", String.class));
            pt.wrapsClass("scripts.artifact_report", "ArtifactHtmlReport", ArtifactJavaReport.class);

            pluginsManager.init(jep, getAleappScriptsDir());
        } else {
            throw new Exception("ALeapp plugin scripts path not found:" + artifactsPath.getCanonicalPath());
        }
    }

    @Override
    public void finish() throws Exception {
    }

    public void executePlugin(IItem evidence, LeapArtifactsPlugin p, List<String> filesFound, File reportDumpPath) {
        Jep jep = getJep();
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

                jep.eval("sys.path.append('" + scriptsDir.getCanonicalPath().replace("\\", "\\\\") + "')");

                jep.eval("import scripts.artifact_report");
                jep.eval("from multiprocessing import Process");
                jep.eval("import os");
                jep.eval("import sys");
                jep.eval("from java.lang import System");
                jep.eval("from iped.engine.task.leappbridge import ArtifactJavaReport");
                jep.eval("from " + p.getModuleName() + " import " + p.getMethodName() + " as parse");


                // creates a dumb file seeker. some plugins refers to directory although not
                // using it.
                jep.eval("dumb = FileSeekerBase()");
                jep.eval("dumb.directory='" + reportPath.getCanonicalPath() + "'");

                jep.set("evidence", evidence);
                jep.set("worker", worker);
                jep.set("reportDumpPath", reportDumpPath);
                jep.set("reportPath", reportPath);
                jep.set("leappTask", this);
                jep.set("moduleDir", this.output);
                jep.set("pluginName", p.getModuleName());
                jep.set("mappedEvidences", mappedEvidences);

                jep.eval("logfunc('" + PLUGIN_EXECUTION_MESSAGE + ":" + p.getModuleName() + "')");
                jep.eval("parse(" + lists + ",'" + reportPath.getCanonicalPath() + "',dumb,True)");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
            }

        } finally {
            // jep.close();
        }
    }

    private File getAleappScriptsDir() {
        if (aleappDir == null) {
            ALeappConfig config = (ALeappConfig) getConfigurables().get(0);

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
        return "LeappTask";
    }

    static HashSet<String> dumpStatFolderNames = new HashSet<String>();
    static {
        dumpStatFolderNames.add("Dump");
        dumpStatFolderNames.add("backup");
    };

    @Override
    public void process(IItem evidence) throws Exception {
        if (dumpStatFolderNames.contains(evidence.getName())) {
            Item subItem = (Item) evidence.createChildItem();
            ParentInfo parentInfo = new ParentInfo(evidence);

            String name = REPORT_EVIDENCE_NAME;
            subItem.setName(name);
            subItem.setPath(parentInfo.getPath() + "/" + name);
            subItem.setSubItem(true);
            subItem.setSubitemId(1);
            subItem.setHasChildren(true);
            subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
            worker.processNewItem(subItem);

            // creates on subitem for each plugin execution
            for (LeapArtifactsPlugin p : pluginsManager.getPlugins()) {
                Item psubItem = (Item) subItem.createChildItem();
                ParentInfo pparentInfo = new ParentInfo(subItem);

                String moduleName = p.moduleName;
                psubItem.setName(moduleName);
                psubItem.setPath(parentInfo.getPath() + "/" + moduleName);
                psubItem.setSubItem(true);
                psubItem.setSubitemId(1);
                psubItem.getMetadata().set(ALEAPP_PLUGIN, moduleName);
                psubItem.setHasChildren(true);
                psubItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
                worker.processNewItem(psubItem);
            }

            // creates subitem to hold device info collected
            Item devDetailsSubItem = (Item) subItem.createChildItem();
            ParentInfo pparentInfo = new ParentInfo(subItem);
            devDetailsSubItem.setName(DEVICE_DETAILS_HTML);
            devDetailsSubItem.setMediaType(DEVICEDETAILS);
            devDetailsSubItem.setPath(parentInfo.getPath() + "/" + DEVICE_DETAILS_HTML);
            devDetailsSubItem.setSubItem(true);
            devDetailsSubItem.setSubitemId(1);
            devDetailsSubItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
            worker.processNewItem(devDetailsSubItem);
        }

        String pluginName = evidence.getMetadata().get(ALEAPP_PLUGIN);
        if (pluginName != null) {
            int priority = worker.manager.getProcessingQueues().getCurrentQueuePriority();
            if (priority < START_QUEUE_PRIORITY) {
                reEnqueueItem(evidence, START_QUEUE_PRIORITY);
                throw new ItemReEnqueuedException();
            } else {
                LeapArtifactsPlugin p = pluginsManager.getPlugin(pluginName);
                processEvidence(evidence, p);
            }
        }
        
        // the device details must be the last to process as it gets output from every
        // plugin execution
        MediaType mt = evidence.getMediaType();
        if (mt != null && mt.equals(DEVICEDETAILS)) {
            int priority = worker.manager.getProcessingQueues().getCurrentQueuePriority();
            if (priority < START_QUEUE_PRIORITY + 1) {
                reEnqueueItem(evidence, START_QUEUE_PRIORITY + 1);
                throw new ItemReEnqueuedException();
            } else {
                processDeviceDetails(evidence);
            }
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
        StringBuffer stringBuffer = devInfoBuffers.get(leappRepEvidence);

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

    static HashSet<LeapArtifactsPlugin> processedPlugins = new HashSet<LeapArtifactsPlugin>();

    private void processPlugin(LeapArtifactsPlugin p, IItem evidence, IItem dumpEvidence, String dumpPath,
            File reportDumpPath) throws IOException {
        try {

            List<String> filesFound = new ArrayList<String>();
            for (String pattern : p.patterns) {
                IPEDSearcher filesSearcher = new IPEDSearcher(ipedCase);
                String query = "path:\"" + dumpEvidence.getPath() + "\"";

                StringTokenizer st = new StringTokenizer(pattern, "*/");
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    query += " && path:\"" + token + "\"";
                }

                filesSearcher.setQuery(query);
                SearchResult filesResult = filesSearcher.search();
                for (int j = 0; j < filesResult.getLength(); j++) {
                    int artLuceneId = ipedCase.getLuceneId(filesResult.getId(j));
                    Document artdoc = ipedCase.getReader().document(artLuceneId);
                    String decoded = artdoc.get(iped.properties.ExtraProperties.DECODED_DATA);
                    if (decoded == null || !decoded.equals("true")) {
                        String artpath = artdoc.get(BasicProps.PATH).substring(dumpPath.length());

                        if (artpath.contains(">>")) {
                            // item is a decoded data, so it is not the source of the informations
                            continue;
                        }

                        if (pluginsManager.hasPatternMatch(artpath, p)) {
                            IItem item = ipedCase.getItemByLuceneID(artLuceneId);
                            File tmp = item.getTempFile();
                            String sourcePath = new File(
                                    ipedCase.getCaseDir() + "/" + artdoc.get(IndexItem.SOURCE_PATH)).getCanonicalPath();
                            if (tmp.getCanonicalPath().startsWith(sourcePath)) {
                                reportDumpPath = new File(sourcePath);
                                // the file returned by getTempFile() is the file itself
                                filesFound.add(tmp.getCanonicalPath());
                                // mappedEvidences.put(tmp.getCanonicalPath(), (Item) item);
                            } else {
                                // the file returned by getTempFile() is a copy to the file in a temp folder
                                String artParentPath = artpath.substring(0, artpath.lastIndexOf("/"));
                                String artname = artpath.substring(artParentPath.length());
                                File artfolder = new File(reportDumpPath, artParentPath);
                                artfolder.mkdirs();

                                try {
                                    File file_found = new File(artfolder, artname);
                                    Files.move(tmp, file_found);
                                    filesFound.add(file_found.getCanonicalPath());
                                    // mappedEvidences.put(file_found.getCanonicalPath(), (Item) item);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                        }
                    }
                }
            }

            if (filesFound.size() <= 0) {
                evidence.setToIgnore(true);
                return;
            } else {
                Metadata m = evidence.getMetadata();
                for (String file : filesFound) {
                    String filel = file.substring(reportDumpPath.getCanonicalPath().length());
                    String filename = filel.substring(filel.lastIndexOf("/") + 1);
                    m.add(ExtraProperties.LINKED_ITEMS, "path:\"*" + filel + "\" && name:\"" + filename + "\"");
                }
                executePlugin(evidence, p, filesFound, reportDumpPath);
            }

        } finally {
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
            reportPath = new File(tmpFileRef.getParentFile().getAbsolutePath(),
                    "/leapptaskreps/" + REPORT_EVIDENCE_NAME + "_" + tmpFileRef.getName());
            reportPath.mkdirs();
            reportPath.deleteOnExit();

            File reportDumpPath = new File(reportPath, "Dump");
            reportDumpPath.mkdirs();
            reportDumpPath.deleteOnExit();

            IItem leappRepEvidence = ipedCase.getItemByID(evidence.getParentId());
            IItem dumpEvidence = ipedCase.getItemByID(leappRepEvidence.getParentId());
            String dumpPath = dumpEvidence.getPath();

            processPlugin(p, evidence, dumpEvidence, dumpPath, reportDumpPath);
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

}