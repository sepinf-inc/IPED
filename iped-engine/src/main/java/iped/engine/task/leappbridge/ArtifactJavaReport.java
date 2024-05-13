package iped.engine.task.leappbridge;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.engine.core.Worker;
import iped.engine.data.Item;
import iped.engine.task.ExportFileTask;
import iped.engine.task.index.IndexItem;
import iped.engine.util.ParentInfo;
import iped.engine.util.Util;
import iped.parsers.python.PythonParser;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;
import jep.Jep;

/**
 * @author Patrick Dalla Bernardina <patrick.dalla@gmail.com>
 * 
 *         Class that will be injected to replace ArtifactHtmlReport of
 *         scripts.artifact_report module in ALeapp plugin execution so results
 *         can be extracted by java code and inserted as IPED items.
 */

public class ArtifactJavaReport {
    Jep jep;
    IItem pluginEvidence;
    Item currentReportEvidence;
    int count = 0;
    String repName;
    private Worker worker;
    HashMap<String, Item> mappedEvidences;
    String name;
    String category;
    private File reportDumpPath;
    private LeappBridgeTask leappTask;
    private File moduleDir;
    String pluginName;
    private MediaType currentReportMediaType;
    private File reportPath;

    static private String KEY_PROPERTY_NAME = "key";
    private String lastKeyValue = null;

    static private Logger LOGGER = LoggerFactory.getLogger(ArtifactJavaReport.class);


    public ArtifactJavaReport() {
        this("", "");
    }

    public ArtifactJavaReport(String name) {
        this(name, "");
    }

    public ArtifactJavaReport(String name, String category) {
        jep = PythonParser.getJep();
        this.name = name;
        this.category = category;
        this.pluginEvidence = (IItem) jep.getValue("evidence");
        this.worker = (Worker) jep.getValue("worker");
        this.leappTask = (LeappBridgeTask) jep.getValue("leappTask");
        this.reportDumpPath = (File) jep.getValue("reportDumpPath");
        this.reportPath = (File) jep.getValue("reportPath");
        this.moduleDir = (File) jep.getValue("moduleDir");
        this.pluginName = (String) jep.getValue("pluginName");
        this.mappedEvidences = (HashMap<String, Item>) jep.getValue("mappedEvidences");
    }

    static public MediaType celPhoneExtractionMediaType = new MediaType("application", "celphone-extraction");

    public void start_artifact_report(String repFolder) {
        start_artifact_report(repFolder, "", "");
    }

    public void start_artifact_report(String repFolder, String repName) {
        start_artifact_report(repFolder, repName, "");
    }

    public void start_artifact_report(String repFolder, String repName, String artifact_description) {
        try {
            this.repName = repName;
            currentReportMediaType = MediaType.application(
                    "aleapp-" + repName.replaceAll(" ", "").replace("(", "-").replace(")", "").toLowerCase());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void add_script() {
        nope();
    }

    public void add_script(String script) {
        nope();
    }

    public void add_section_heading(String heading, String size) {
        nope();
    }

    public void write_minor_header(Object heading, Object heading_tag) {
        nope();
    }

    public void write_lead_text(String text) {
        nope();
    }

    public void write_raw_html(String code) {
        nope();
    }

    public void write_artifact_data_table(Object headers, Object data_list, String file) {
        if (data_list != null) {
            if (data_list instanceof Collection) {
                Collection coll = (Collection) data_list;
                if (coll.size() > 0) {
                    pluginEvidence.setHasChildren(true);
                }
                for (Object data_fields : coll) {
                    lastKeyValue = null;
                    write_artifact_data_item(headers, data_fields, file);
                }
            }
        }
    }

    public void write_artifact_data_item(Object headers, Object data_fields, String file) {
        try {
            file = LeappBridgeTask.replaceSpecialChars(file);

            if (currentReportEvidence == null) {
                currentReportEvidence = (Item) pluginEvidence;
            }
            Item subItem = (Item) currentReportEvidence.createChildItem();
            ParentInfo parentInfo = new ParentInfo(currentReportEvidence);

            count++;
            String name = repName + "_" + count;
            subItem.setName(name);
            subItem.setPath(currentReportEvidence.getPath() + "/" + name);
            subItem.setSubItem(true);
            subItem.setSubitemId(count);

            subItem.setMediaType(currentReportMediaType);

            Metadata m = subItem.getMetadata();

            // if file parameters corresponds to a dump path, add a link to it
            String linkFile = null;
            if (file.startsWith(reportDumpPath.getCanonicalPath())) {
                linkFile = file.substring(reportDumpPath.getCanonicalPath().length());
            } else {
                String dumpRelativePath = reportDumpPath.getCanonicalPath()
                        .substring(reportPath.getCanonicalPath().length());
                if (file.startsWith(dumpRelativePath)) {
                    linkFile = file.substring(dumpRelativePath.length());
                }
            }

            if (linkFile != null) {
                linkFile = LeappBridgeTask.revertSpecialChars(linkFile).replace("/", "//");
                String filename = linkFile.substring(linkFile.lastIndexOf("/") + 1);
                m.add(ExtraProperties.LINKED_ITEMS, "path:\"*" + linkFile + "\" && name:\"" + filename + "\"");
            }

            m.add(LeappBridgeTask.ALEAPP_PLUGIN, this.pluginName);
            m.add(LeappBridgeTask.ALEAPP_ISARTIFACT, "true");

            int i = 0;
            Object[] data = ((Collection) data_fields).toArray();
            if (data != null) {
                for (String property : (Collection<String>) headers) {
                    if (data[i] != null) {
                        addMetadata(subItem, property, data[i].toString());
                    }
                    i++;
                }
            }
            subItem.setExtraAttribute(IndexItem.PARENT_TRACK_ID, parentInfo.getTrackId());
            subItem.setExtraAttribute(IndexItem.CONTAINER_TRACK_ID, Util.getTrackID(pluginEvidence));
            subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);

            Date d1 = new Date();
            worker.processNewItem(subItem);
            Date d2 = new Date();
            int delta = (int) (d2.getTime() - d1.getTime());
            LeappBridgeTask.pluginTimeCounter.addAndGet(-delta);// removes from plugin time counter the time to process
                                                                // the item
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    StringBuffer location;

    private void checkAndAddLocation(Metadata m, String locationStr) {
        try {
            String[] coords = locationStr.toString().split(";");
            double lat = Double.valueOf(coords[0].trim());
            double lon = Double.valueOf(coords[1].trim());

            // if whel formatted, no exception will be throw, so add the location
            m.add(ExtraProperties.LOCATIONS, locationStr);
        } catch (Exception e) {
            m.add(LeappBridgeTask.ALEAPP_METADATA_PREFIX + ":" + "location", locationStr);
        } finally {
            location = null;
        }
    }

    /*
     * Properly maps, format and add leap html columns as IPED item metadata
     */
    private void addMetadata(Item subItem, String property, String value) {
        Metadata m = subItem.getMetadata();

        if (value == null || value.trim().equals("")) {
            return;
        }
        if (property.contains("Latitude")) {
            if (location == null) {
                location = new StringBuffer();
                location.append(value);
            } else {
                location.insert(0, ";");
                location.insert(0, value);
            }
            if (location.indexOf(";") > 0) {
                checkAndAddLocation(m, location.toString());
            }
        }
        if (property.contains("Longitude")) {
            if (location == null) {
                location = new StringBuffer();
            } else {
                location.append(";");
            }
            location.append(value);
            if (location.indexOf(";") > 0) {
                checkAndAddLocation(m, location.toString());
            }
        }

        try {
            if (value.startsWith("/storage/emulated/0")) {
                value = value.replace("/storage/emulated/0", reportDumpPath.getCanonicalPath() + "/data/media/0");
            }
            // some plugins have linked items per artifact record
            if (value.startsWith(reportDumpPath.getCanonicalPath())) {
                String filel = value.toString().substring(reportDumpPath.getCanonicalPath().length());
                addLinkMetadata(m, property, filel);
                return;
            }
        } catch (IOException e) {
            LeappBridgeTask.logger.warn("Error creation link to:" + value);
            e.printStackTrace();
        }

        int refpos = value.indexOf("href=");
        int sizeTag = 5;
        if (refpos == -1) {
            refpos = value.indexOf("src=");
            sizeTag = 4;
        }
        if (refpos >= 0) {
            String refFileStr = value.substring(refpos + sizeTag);// from end of "href=" till end
            String strDelimiter = refFileStr.substring(0, 1); // read used string delimiter (quote or double quote)
            int end = refFileStr.length();
            if (strDelimiter.equals("'") || strDelimiter.equals("\"")) {
                end = refFileStr.indexOf(strDelimiter, 2);
            }

            try {
                refFileStr = refFileStr.substring(1, end); // to end string delimiter
                if (refFileStr.startsWith("." + File.pathSeparator)) {
                    refFileStr = reportDumpPath + File.pathSeparator + refFileStr.substring(1);
                }

                File refFile = new File(refFileStr);

                if (refFile.getCanonicalPath().startsWith(reportDumpPath.getCanonicalPath())) {
                    // the referenced file is an existent file inside the dump
                    // so adds a link to it
                    String filel = refFileStr.toString().substring(reportDumpPath.getCanonicalPath().length());
                    addLinkMetadata(m, filel);
                } else if (refFileStr.startsWith(reportPath.getName())) {
                    // file is in report path.
                    byte[] bytes = Files
                            .readAllBytes(Path.of(reportPath.getParentFile().getAbsolutePath() + "/" + refFile));

                    ExportFileTask extractor = new ExportFileTask();
                    extractor.setWorker(worker);
                    ByteArrayInputStream is = new ByteArrayInputStream(bytes);
                    extractor.extractFile(is, subItem, subItem.getLength());

                    return; // do not add metadata with this value as it contains temporary reference
                } else {
                    nope();
                }
            } catch (IOException e) {
                LeappBridgeTask.logger.warn("Error in link creation to:" + refFileStr);
                e.printStackTrace();
            }
        }

        if (property.toLowerCase().contains(KEY_PROPERTY_NAME)) {
            // some htmls are formated as key value pairs. So keep the last read key value
            // to name following properties. This is used mainly to better
            // name some timestamps event types.
            lastKeyValue = value;
        }

        Date d = DateUtil.tryToParseDate(value);
        if (d != null) {
            Property p;
            if (property.toLowerCase().contains("value") && lastKeyValue != null) {
                String eventType = "";
                if (pluginName.contains("FCMQueuedMessagesDump")) {
                    eventType = "fcm_dump_" + repName.substring(repName.lastIndexOf(".") + 1);
                } else {
                    eventType = pluginName;
                }
                MetadataUtil.setMetadataType(
                        LeappBridgeTask.ALEAPP_METADATA_PREFIX + ":" + eventType + "_" + lastKeyValue, Date.class);
                p = Property
                        .internalDate(LeappBridgeTask.ALEAPP_METADATA_PREFIX + ":" + eventType + "_" + lastKeyValue);
            } else {
                MetadataUtil.setMetadataType(LeappBridgeTask.ALEAPP_METADATA_PREFIX + ":" + pluginName + "_" + property,
                        Date.class);
                p = Property.internalDate(LeappBridgeTask.ALEAPP_METADATA_PREFIX + ":" + pluginName + "_" + property);
            }
            m.set(p, d);
            return;
        }

        m.add(LeappBridgeTask.ALEAPP_METADATA_PREFIX + ":" + property, value);
    }

    private void addLinkMetadata(Metadata m, String filel) throws IOException {
        filel = LeappBridgeTask.revertSpecialChars(filel).replace("/", "////");
        String filename = filel.substring(filel.lastIndexOf("/") + 1);
        m.add(ExtraProperties.LINKED_ITEMS, "path:\"*" + filel + "\" && name:\"" + filename + "\"");
    }

    private void addLinkMetadata(Metadata m, String property, String filel) throws IOException {
        addLinkMetadata(m, filel);
        m.add(LeappBridgeTask.ALEAPP_METADATA_PREFIX + ":" + property, filel);
    }

    public void add_section_heading(String heading, int size) {
        System.out.println("add_section_heading:" + heading + "(" + size + ")");
    }


    public void end_artifact_report(String... ending) {
    }

    public void add_image_file(Object param, Object param1, Object param2, Object secondImage) {
        nope();
    }

    public void add_html_to_artifact(Object param, Object param1) {
        nope();
    }

    public void add_map(Object param) {
        nope();
    }

    public void add_json_to_artifact(Object param1, Object param2, Object hidden, String idJ, Object gcm) {
        nope();
    }

    public void add_invisible_data(Object param, Object param1) {
        nope();
    }

    public void add_heat_map(Object json) {
        nope();
    }

    public void add_chart_script(Object id, Object type, Object data, Object labels, Object title, Object xLabel,
            Object yLabel) {
        nope();
    }

    public void add_timeline(Object id, Object dataDict) {
        nope();
    }

    public void add_timeline_script() {
        nope();
    }

    public void filter_by_date(String id, String col1) {
        nope();
    }

    public void add_chat() {
        nope();
    }

    public void add_chat_invisble(Object id, Object text) {
        nope();
    }

    static public void nope() {
    }

    static public void ipedlog(String message) {
        LOGGER.warn(message);
    }
}
