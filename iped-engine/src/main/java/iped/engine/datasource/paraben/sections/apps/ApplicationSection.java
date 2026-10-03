/**
 * Paraben Data Parser Integration for IPED
 *
 * Developed by: Ariel E Aburo
 * Contact: infmpfch@gmail.com
 *
 * Description:
 * Implements parsing of data extracted via Paraben tools,
 * converting XML and associated database content into
 * IPED-compatible structures.
 *
 * This module supports multiple artifacts such as:
 * - Conversations
 * - Messages
 * - Attachments
 * - Device and user data and more.
 *
 * Notes:
 * - Uses XML as primary source
 * - Designed to be extensible for additional Paraben artifacts
 */
package iped.engine.datasource.paraben.sections.apps;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;

import org.w3c.dom.*;

import java.io.File;
import java.util.*;
import java.nio.file.Files;

public class ApplicationSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        List<Element> appNodes = index.get("Installed Application List");
        List<Element> permNodes = index.get("Application Permissions");

        if (appNodes == null || appNodes.isEmpty())
            return;

        Map<String, AppData> apps = extractApps(appNodes);

        if (permNodes != null) {
            Map<String, Map<String, String>> perms = extractPermissions(permNodes);

            for (String key : apps.keySet()) {
                if (perms.containsKey(key)) {
                    apps.get(key).permissions = perms.get(key);
                }
            }
        }

        for (AppData app : apps.values()) {

            Item item = new Item();

            String name = normalize(app.name);

            item.setName(name);
            item.setPath(rootItem.getPath() + "/Applications/" + name);
            item.setParent(rootItem);
            item.setHasChildren(false);

            item.setMediaType(
                    org.apache.tika.mime.MediaType.parse("application/x-paraben-app"));

            item.setIdInDataSource("paraben-app-" + app.key);

            String html = buildHtml(app, root);
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            ExportFileTask.getLastInstance()
                    .insertIntoStorage(item, bytes, bytes.length);

            item.setLength((long) bytes.length);

            item.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
            item.getMetadata().set(BasicProps.CATEGORY, "Applications");

            item.getMetadata().set(BasicProps.NAME, name);
            item.getMetadata().add("paraben:app:package", app.packageName);
            item.getMetadata().add("paraben:app:version", app.version);
            item.getMetadata().add("paraben:app:category", app.category);
            item.getMetadata().add("paraben:app:manufacturer", app.manufacturer);
            item.getMetadata().add("paraben:app:removed", app.removed);
            item.getMetadata().add("paraben:app:malware", app.malware);

            if (app.permissions != null) {
                for (Map.Entry<String, String> p : app.permissions.entrySet()) {
                    if ("1".equals(p.getValue())) {
                        item.getMetadata().add("paraben:permission", p.getKey());
                    }
                }
            }

            caseData.incDiscoveredEvidences(1);
            Manager.getInstance().addItemToQueue(item);
        }
    }

    private Map<String, AppData> extractApps(List<Element> nodes) {

        Map<String, AppData> map = new HashMap<>();

        for (Element node : nodes) {

            NodeList rows = node.getElementsByTagName("Row");

            for (int j = 0; j < rows.getLength(); j++) {

                Element row = (Element) rows.item(j);

                Element values = getFirst(row, "Values");
                if (values == null)
                    continue;

                NodeList cells = values.getElementsByTagName("Cell");

                AppData app = new AppData();

                for (int k = 0; k < cells.getLength(); k++) {

                    Element cell = (Element) cells.item(k);

                    String column = get(cell, "Column");
                    String value = get(cell, "Value");

                    switch (column) {
                        case "Application Name":
                            app.name = value;
                            break;
                        case "Version":
                            app.version = value;
                            break;
                        case "Internal Application Name":
                            app.packageName = value;
                            break;
                        case "Category":
                            app.category = value;
                            break;
                        case "Manufacturer":
                            app.manufacturer = value;
                            break;
                        case "Is Removed":
                            app.removed = value;
                            break;
                        case "Malware Suspicious":
                            app.malware = value;
                            break;
                        case "Icon":
                            app.iconPath = get(cell, "Link");
                            break;
                    }
                }

                if (app.name != null && app.version != null) {
                    app.key = app.name + "|" + app.version;
                    map.put(app.key, app);
                }
            }
        }

        return map;
    }

    private Map<String, Map<String, String>> extractPermissions(List<Element> nodes) {

        Map<String, Map<String, String>> map = new HashMap<>();

        for (Element node : nodes) {

            NodeList rows = node.getElementsByTagName("Row");

            for (int j = 0; j < rows.getLength(); j++) {

                Element row = (Element) rows.item(j);

                Element values = getFirst(row, "Values");
                if (values == null)
                    continue;

                NodeList cells = values.getElementsByTagName("Cell");

                String name = null;
                String version = null;

                Map<String, String> perms = new HashMap<>();

                for (int k = 0; k < cells.getLength(); k++) {

                    Element cell = (Element) cells.item(k);

                    String column = get(cell, "Column");
                    String value = get(cell, "Value");

                    if ("Application Name".equals(column))
                        name = value;

                    else if ("Version".equals(column))
                        version = value;

                    else if (!column.equals("Icon")
                            && !column.equals("Source")
                            && !column.equals("Application Permissions")
                            && !column.equals("Malware Suspicious")) {

                        perms.put(column, value);
                    }
                }

                if (name != null && version != null) {
                    map.put(name + "|" + version, perms);
                }
            }
        }

        return map;
    }

    private String buildHtml(AppData a, File root) {

        StringBuilder html = new StringBuilder();

        html.append("<html><body>");
        html.append("<h2>Application</h2>");
        html.append("<table border='1'>");

        html.append(row("Name", a.name));
        html.append(row("Package", a.packageName));
        html.append(row("Version", a.version));
        html.append(row("Category", a.category));
        html.append(row("Manufacturer", a.manufacturer));
        html.append(row("Removed", a.removed));
        html.append(row("Malware", a.malware));
        String iconBase64 = loadIconBase64(root, a.iconPath);

        if (iconBase64 != null) {
            html.append("<tr><td><b>Icon</b></td><td>");
            html.append("<img src='").append(iconBase64).append("' width='64'/>");
            html.append("</td></tr>");
        }
        if (a.permissions != null) {
            html.append("<tr><td colspan='2'><b>Permissions</b></td></tr>");

            for (Map.Entry<String, String> p : a.permissions.entrySet()) {
                if ("1".equals(p.getValue())) {
                    html.append(row(p.getKey(), "YES"));
                }
            }
        }

        html.append("</table></body></html>");

        return html.toString();
    }

    private String row(String k, String v) {
        return "<tr><td><b>" + normalize(k) + "</b></td><td>" + normalize(v) + "</td></tr>";
    }

    private String normalize(String v) {
        if (v == null || v.trim().isEmpty())
            return "-";
        return v.trim();
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }

    private Element getFirst(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? (Element) n.item(0) : null;
    }

    private String loadIconBase64(File root, String path) {
        try {
            if (path == null || path.isEmpty())
                return null;

            File f = new File(root, path);

            if (!f.exists())
                return null;

            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);

            return "data:image/png;base64," + base64;

        } catch (Exception e) {
            return null;
        }
    }

    private static class AppData {
        String key;
        String name;
        String version;
        String packageName;
        String category;
        String manufacturer;
        String removed;
        String malware;
        String iconPath;
        Map<String, String> permissions;
    }
}