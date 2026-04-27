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
package iped.engine.datasource.paraben;

import iped.engine.data.Item;
import iped.properties.ExtraProperties;
import iped.utils.FileInputStreamFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Date;

// SE ENCARGA DE EXTRAER METADATOS DE BINARIOS CONTENIDOS EN EL XML.
public class ParabenItemBuilder {

    public static Item build(
            File root,
            Item rootItem,
            String itemId,
            Map<String, String> props,
            Map<String, String> propsTag,
            String currentXml) {

        File file = resolveFile(root, itemId, props);

        if (file == null || !file.exists())
            return null;

        Item item = new Item();

        // 🔹 NAME
        item.setName(resolveName(itemId, props));

        // 🔹 PATH
        item.setPath(resolvePath(rootItem, props, item.getName()));

        item.setParent(rootItem);
        item.setHasChildren(false);

        // 🔹 CONTENT
        item.setInputStreamFactory(new FileInputStreamFactory(file.toPath()));
        item.setLength(file.length());
        item.setIdInDataSource(file.getAbsolutePath());

        item.setExtraAttribute(
                ExtraProperties.DATASOURCE_READER,
                "ParabenXmlReader");
        // 🔹 METADATA (simple)
        props.forEach((k, v) -> {
            item.getMetadata().add("paraben:" + normalize(k), v);
        });

        if (currentXml != null) {
            item.getMetadata().add("paraben:source_xml", currentXml);
        }
        // 🔹 nombre físico exportado (forense clave)
        item.getMetadata().add("paraben:item_id", itemId);

        String link = props.get("Link");
        if (link != null) {
            item.getMetadata().add("paraben:link", link);
        }

        // 🔹 HASHES
        // MD5 → hash principal de IPED
        if (props.containsKey("MD5")) {
            item.setHash(props.get("MD5"));
        }

        // SHA1 → metadata adicional
        if (props.containsKey("SHA1")) {
            item.getMetadata().add("sha1", props.get("SHA1"));
        }

        // 🔹 DATES (usar Tag si existe)
        // 🔹 DATES (usar TAG si existe, por propiedad)
        try {

            // 🔥 CREATION
            if (propsTag.containsKey("Creation time")) {
                long ts = Long.parseLong(propsTag.get("Creation time"));
                item.setCreationDate(new Date(ts));

            } else if (props.containsKey("Creation time")) {
                SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                item.setCreationDate(df.parse(props.get("Creation time")));
            }

            // 🔥 LAST ACCESS
            if (propsTag.containsKey("Last access time")) {
                long ts = Long.parseLong(propsTag.get("Last access time"));
                item.setAccessDate(new Date(ts));

            } else if (props.containsKey("Last access time")) {
                SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                item.setAccessDate(df.parse(props.get("Last access time")));
            }

            // 🔥 MODIFICATION
            if (propsTag.containsKey("Last modification time")) {
                long ts = Long.parseLong(propsTag.get("Last modification time"));
                item.setModificationDate(new Date(ts));

            } else if (props.containsKey("Last modification time")) {
                SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                item.setModificationDate(df.parse(props.get("Last modification time")));
            }

        } catch (Exception ignored) {
        }

        return item;
    }

    // =====================================================

    private static File resolveFile(File root, String itemId, Map<String, String> props) {

        String link = props.get("Link");

        if (link != null) {
            File f = new File(root, link);
            if (f.exists())
                return f;
        }

        return new File(root, "Binary Files/" + itemId);
    }

    private static String resolveName(String itemId, Map<String, String> props) {

        String filePath = props.get("File Path");
        String fileName = props.get("File Name");
        String title = props.get("Title");

        if (filePath != null && !filePath.isEmpty())
            return new File(filePath).getName();

        if (fileName != null && !fileName.isEmpty())
            return new File(fileName).getName();

        if (title != null && !title.isEmpty())
            return new File(title).getName();

        return itemId;
    }

    private static String resolvePath(Item rootItem, Map<String, String> props, String name) {

        String filePath = props.get("File Path");

        if (filePath != null && !filePath.isEmpty()) {
            filePath = filePath.replace("\\", "/");
            return rootItem.getPath() + "/" + filePath;
        }

        return rootItem.getPath() + "/Binary Files/" + name;
    }

    private static String normalize(String s) {
        return s.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("/", "_");
    }
}