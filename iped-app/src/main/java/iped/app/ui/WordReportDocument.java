package iped.app.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Inserts evidence blocks without reserializing unrelated DOCX package parts. */
public class WordReportDocument {

    static final String FILE_NAME = "Nome";
    private static final String FILE_INFORMATION = "Informações do arquivo";
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private static final int MAX_XML_BYTES = 32 * 1024 * 1024;

    private final Path source;
    private final String fingerprint;
    private final Document document;
    private final Document relationships;
    private final Document contentTypes;
    private final Document settings;
    private final Element body;
    private final List<Element> blocks = new ArrayList<>();
    private final Map<String, Integer> headingStyles = new LinkedHashMap<>();
    private final Map<String, byte[]> additions = new LinkedHashMap<>();
    private int drawingId;

    public static class Evidence {
        public String title;
        public String text;
        public String imageDescription;
        public BufferedImage image;
        public boolean message;
        public boolean audio;
        public boolean video;
        public boolean chatCapture;
        public boolean besidePreviousChat;
        public final List<BufferedImage> videoFrames = new ArrayList<>();
        public final List<Evidence> transcripts = new ArrayList<>();
        public final List<Evidence> messages = new ArrayList<>();
        public boolean imageUnavailable;
        public String participants;
        public final Map<String, String> metadata = new LinkedHashMap<>();
    }

    public static class Location {
        public final int index;
        public final String label;

        Location(int index, String label) {
            this.index = index;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class Section {
        public final String title;
        public final int level;
        public final int start;
        public final int end;

        Section(String title, int level, int start, int end) {
            this.title = title;
            this.level = level;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public WordReportDocument(Path source) throws Exception {
        this.source = source.toRealPath();
        fingerprint = fingerprint(this.source);
        try (ZipFile zip = new ZipFile(source.toFile())) {
            if (zip.getEntry("word/vbaProject.bin") != null || zip.stream().anyMatch(e -> e.getName().startsWith("_xmlsignatures/"))) {
                throw new IOException(Messages.getString("WordReport.Unsupported"));
            }
            document = readXml(zip, "word/document.xml");
            relationships = readXml(zip, "word/_rels/document.xml.rels");
            contentTypes = readXml(zip, "[Content_Types].xml");
            if (zip.getEntry("word/styles.xml") != null) {
                Document styles = readXml(zip, "word/styles.xml");
                NodeList definitions = styles.getElementsByTagNameNS(W, "style");
                for (int i = 0; i < definitions.getLength(); i++) {
                    Element style = (Element) definitions.item(i);
                    NodeList levels = style.getElementsByTagNameNS(W, "outlineLvl");
                    if (levels.getLength() != 0) {
                        String value = ((Element) levels.item(0)).getAttributeNS(W, "val");
                        if (value.matches("[0-8]")) {
                            headingStyles.put(style.getAttributeNS(W, "styleId"), Integer.parseInt(value));
                        }
                    }
                }
            }
            if (zip.getEntry("word/settings.xml") != null) {
                settings = readXml(zip, "word/settings.xml");
            } else {
                settings = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                settings.appendChild(settings.createElementNS(W, "w:settings"));
                Element rel = append(relationships.getDocumentElement(), REL, "Relationship");
                rel.setAttribute("Id", "rId" + UUID.randomUUID().toString().replace("-", ""));
                rel.setAttribute("Type", R + "/settings");
                rel.setAttribute("Target", "settings.xml");
                Element type = append(contentTypes.getDocumentElement(), CT, "Override");
                type.setAttribute("PartName", "/word/settings.xml");
                type.setAttribute("ContentType", "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml");
            }
        }
        Element update = (Element) settings.getElementsByTagNameNS(W, "updateFields").item(0);
        if (update == null) {
            update = append(settings.getDocumentElement(), W, "w:updateFields");
        }
        update.setAttributeNS(W, "w:val", "true");
        body = (Element) document.getElementsByTagNameNS(W, "body").item(0);
        if (body == null) {
            throw new IOException(Messages.getString("WordReport.InvalidDocument"));
        }
        for (Node child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && !"sectPr".equals(child.getLocalName())) {
                blocks.add((Element) child);
            }
        }
        NodeList drawings = document.getElementsByTagNameNS(WP, "docPr");
        for (int i = 0; i < drawings.getLength(); i++) {
            try {
                drawingId = Math.max(drawingId, Integer.parseInt(((Element) drawings.item(i)).getAttribute("id")));
            } catch (NumberFormatException e) {
                // Existing non-numeric IDs do not conflict with the numeric IDs added here.
            }
        }
    }

    public Path getSource() {
        return source;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public boolean isSourceCurrent() throws Exception {
        return fingerprint.equals(fingerprint(source));
    }

    private static String fingerprint(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new DigestInputStream(Files.newInputStream(path), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return java.util.Base64.getEncoder().encodeToString(digest.digest());
    }

    private static Document readXml(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() > MAX_XML_BYTES) {
            throw new IOException(Messages.getString("WordReport.InvalidDocument"));
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readNBytes(MAX_XML_BYTES + 1);
            if (bytes.length > MAX_XML_BYTES) {
                throw new IOException(Messages.getString("WordReport.InvalidDocument"));
            }
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        }
    }

    public List<String> getLocations() {
        List<String> locations = new ArrayList<>();
        locations.add(Messages.getString("WordReport.Start"));
        for (int i = 0; i < blocks.size(); i++) {
            String text = text(blocks.get(i)).replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) {
                text = Messages.getString("WordReport.EmptyBlock");
            }
            locations.add((i + 1) + " - " + text.substring(0, Math.min(text.length(), 110)));
        }
        return Collections.unmodifiableList(locations);
    }

    /** Navigation uses headings and readable text, while insertion retains the exact body index. */
    public List<Section> getSections() {
        List<Section> sections = new ArrayList<>();
        String title = Messages.getString("WordReport.DocumentBody");
        int start = 0;
        int level = 0;
        boolean hasHeading = false;
        for (int i = 0; i < blocks.size(); i++) {
            int heading = headingLevel(blocks.get(i));
            String value = getLocationText(i + 1);
            if (heading >= 0 && !value.isEmpty()) {
                if (hasHeading || i > 0) {
                    sections.add(new Section(title, level, start, i));
                }
                title = value;
                level = heading;
                start = i + 1;
                hasHeading = true;
            }
        }
        sections.add(new Section(title, level, start, blocks.size()));
        return Collections.unmodifiableList(sections);
    }

    public List<Location> getPositions(Section section) {
        List<Location> positions = new ArrayList<>();
        positions.add(new Location(section.end, Messages.getString("WordReport.SectionEnd")));
        if (section.start != section.end) {
            positions.add(new Location(section.start, Messages.getString("WordReport.SectionStart")));
        }
        for (int i = section.start; i < section.end; i++) {
            Element block = blocks.get(i);
            String value = getLocationText(i + 1);
            if (value.isEmpty() || isTableOfContents(block)) {
                continue;
            }
            String key = "tbl".equals(block.getLocalName()) ? "WordReport.AfterTable" : "WordReport.AfterText";
            String excerpt = value.length() <= 100 ? value : value.substring(0, 100) + "…";
            positions.add(new Location(i + 1, Messages.getString(key, excerpt)));
        }
        return Collections.unmodifiableList(positions);
    }

    private int headingLevel(Element block) {
        if (!"p".equals(block.getLocalName()) || isTableOfContents(block)) {
            return -1;
        }
        NodeList levels = block.getElementsByTagNameNS(W, "outlineLvl");
        if (levels.getLength() != 0) {
            String value = ((Element) levels.item(0)).getAttributeNS(W, "val");
            return value.matches("[0-8]") ? Integer.parseInt(value) : -1;
        }
        NodeList styles = block.getElementsByTagNameNS(W, "pStyle");
        if (styles.getLength() == 0) {
            return -1;
        }
        String style = ((Element) styles.item(0)).getAttributeNS(W, "val");
        Integer level = headingStyles.get(style);
        if (level != null) {
            return level;
        }
        return style.matches("(?i)heading[1-9]") ? Integer.parseInt(style.substring(style.length() - 1)) - 1 : -1;
    }

    private boolean isTableOfContents(Element block) {
        NodeList fields = block.getElementsByTagNameNS(W, "instrText");
        for (int i = 0; i < fields.getLength(); i++) {
            if (fields.item(i).getTextContent().trim().matches("(?is)^TOC(?:\\s.*)?$")) {
                return true;
            }
        }
        NodeList styles = block.getElementsByTagNameNS(W, "pStyle");
        for (int i = 0; i < styles.getLength(); i++) {
            String style = ((Element) styles.item(i)).getAttributeNS(W, "val");
            if (style.matches("(?i)(TOC|Sumrio|Sumario)[0-9]+")) {
                return true;
            }
        }
        return false;
    }

    public String getLocationText(int location) {
        return location == 0 ? "" : text(blocks.get(location - 1)).replaceAll("\\s+", " ").trim();
    }

    public int getLocationOccurrence(int location) {
        String anchor = getLocationText(location);
        int occurrence = 0;
        for (int i = 1; i < location; i++) {
            if (getLocationText(i).equals(anchor)) {
                occurrence++;
            }
        }
        return occurrence;
    }

    public int countTextOccurrencesBefore(int location, String anchor) {
        String needle = anchor.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 1; i <= location; i++) {
            String value = getLocationText(i).replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
            for (int start = 0; (start = value.indexOf(needle, start)) >= 0; start += needle.length()) {
                count++;
            }
        }
        return count;
    }

    private static String text(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList nodes = element.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < nodes.getLength(); i++) {
            text.append(nodes.item(i).getTextContent()).append(' ');
        }
        return text.toString();
    }

    /** Location is the number of existing body blocks preceding the insertion. */
    public int insert(int location, List<Evidence> evidence, boolean includeMetadata, boolean includeText) throws Exception {
        return insert(location, evidence, includeMetadata, includeText, 1);
    }

    public int insert(int location, List<Evidence> evidence, boolean includeMetadata, boolean includeText, int columns) throws Exception {
        if (location < 0 || location > blocks.size() || columns < 1 || columns > 2) {
            throw new IllegalArgumentException("Invalid insertion location or column count");
        }
        Node before = location < blocks.size() ? blocks.get(location) : null;
        if (before == null) {
            for (Node child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (W.equals(child.getNamespaceURI()) && "sectPr".equals(child.getLocalName())) {
                    before = child;
                }
            }
        }
        int inserted = 0;
        Element previousChat = previousChatTable(location);
        for (int i = 0; i < evidence.size();) {
            if (evidence.get(i).chatCapture) {
                Evidence chat = evidence.get(i);
                if (chat.besidePreviousChat) {
                    if (previousChat == null || !canPairChat(previousChat)) {
                        throw new IOException(Messages.getString("WordReport.PreviousChatUnavailable"));
                    }
                    pairChatTable(previousChat, chat, includeText);
                    previousChat = null;
                } else {
                    previousChat = chatTable(chat, includeText);
                    body.insertBefore(previousChat, before);
                    body.insertBefore(paragraph("", false), before);
                    inserted += 2;
                }
                i++;
                continue;
            }
            previousChat = null;
            if (evidence.get(i).video) {
                body.insertBefore(videoTable(evidence.get(i), includeText), before);
                body.insertBefore(paragraph("", false), before);
                inserted += 2;
                i++;
                continue;
            }
            boolean message = evidence.get(i).message;
            int end = i + 1;
            while (end < evidence.size() && !evidence.get(end).video && !evidence.get(end).chatCapture && evidence.get(end).message == message
                    && (message || end - i < columns)) {
                end++;
            }
            List<Evidence> group = evidence.subList(i, end);
            body.insertBefore(message ? messageTable(group, includeText) : evidenceTable(group, includeMetadata, includeText), before);
            body.insertBefore(paragraph("", false), before);
            inserted += 2;
            i = end;
        }
        return location + inserted;
    }

    private Element reportTable(long[] widths) {
        Element table = element(document, W, "w:tbl");
        Element properties = append(table, W, "w:tblPr");
        Element width = append(properties, W, "w:tblW");
        width.setAttributeNS(W, "w:w", Long.toString(pageWidthTwips()));
        width.setAttributeNS(W, "w:type", "dxa");
        append(properties, W, "w:tblLayout").setAttributeNS(W, "w:type", "fixed");
        Element borders = append(properties, W, "w:tblBorders");
        for (String name : new String[] { "top", "left", "bottom", "right", "insideH", "insideV" }) {
            Element border = append(borders, W, "w:" + name);
            border.setAttributeNS(W, "w:val", "single");
            border.setAttributeNS(W, "w:sz", "4");
            border.setAttributeNS(W, "w:color", "000000");
        }
        Element margins = append(properties, W, "w:tblCellMar");
        for (String name : new String[] { "top", "left", "bottom", "right" }) {
            Element margin = append(margins, W, "w:" + name);
            margin.setAttributeNS(W, "w:w", name.equals("left") || name.equals("right") ? "108" : "0");
            margin.setAttributeNS(W, "w:type", "dxa");
        }
        Element grid = append(table, W, "w:tblGrid");
        for (long column : widths) {
            append(grid, W, "w:gridCol").setAttributeNS(W, "w:w", Long.toString(column));
        }
        return table;
    }

    private long[] mediaWidths(int columns) {
        long width = pageWidthTwips();
        long left = width * 4815 / 9345;
        return columns == 1 ? new long[] { width } : new long[] { left, width - left };
    }

    /** Image frames follow the reference's image, information heading and three-field rows. */
    private Element evidenceTable(List<Evidence> evidence, boolean includeMetadata, boolean includeText) throws Exception {
        long[] widths = mediaWidths(evidence.size());
        Element table = reportTable(widths);
        Element content = append(table, W, "w:tr");
        for (int i = 0; i < evidence.size(); i++) {
            Evidence item = evidence.get(i);
            Element cell = reportCell(content, widths[i], null);
            if (item.image != null) {
                cell.appendChild(picture(item.image, item.imageDescription, widths[i]));
            }
            if (item.audio && includeText && item.text != null && !item.text.isEmpty()) {
                cell.appendChild(reportParagraph("Transcrição do áudio", true, false, true));
            }
            appendText(cell, includeText ? item.text : null);
            if (cell.getElementsByTagNameNS(W, "p").getLength() == 0) {
                cell.appendChild(reportParagraph("", false, false, false));
            }
        }
        if (includeMetadata) {
            Element heading = append(table, W, "w:tr");
            for (long width : widths) {
                reportCell(heading, width, "DDD9C3").appendChild(reportParagraph(FILE_INFORMATION, false, false, true));
            }
            Element information = append(table, W, "w:tr");
            for (int i = 0; i < evidence.size(); i++) {
                appendInformation(reportCell(information, widths[i], "EEEEEE"), evidence.get(i));
            }
        }
        return table;
    }

    public boolean hasPreviousChat(int location) {
        return previousChatTable(location) != null;
    }

    /** Only the immediately preceding, still unpaired export can receive another crop. */
    private Element previousChatTable(int location) {
        for (int i = Math.min(location, blocks.size()) - 1; i >= 0; i--) {
            Element block = blocks.get(i);
            if ("p".equals(block.getLocalName()) && text(block).trim().isEmpty()
                    && block.getElementsByTagNameNS(W, "drawing").getLength() == 0) {
                continue;
            }
            return canPairChat(block) ? block : null;
        }
        return null;
    }

    private boolean canPairChat(Element table) {
        if (!"tbl".equals(table.getLocalName()) || table.getElementsByTagNameNS(W, "tr").getLength() != 3
                || table.getElementsByTagNameNS(W, "drawing").getLength() != 1) {
            return false;
        }
        Element caption = (Element) table.getElementsByTagNameNS(W, "tblCaption").item(0);
        if (caption != null) {
            return "IPED_CHAT_SINGLE".equals(caption.getAttributeNS(W, "val"))
                    || "IPED_CHAT_TRANSCRIPT".equals(caption.getAttributeNS(W, "val"));
        }
        // Earlier exports have no caption. Recognize only our embedded screenshot and chat metadata.
        if (!text(table).contains("Chat_") || !text(table).contains(FILE_INFORMATION)) {
            return false;
        }
        Element blip = (Element) table.getElementsByTagNameNS(A, "blip").item(0);
        if (blip == null) {
            return false;
        }
        String id = blip.getAttributeNS(R, "embed");
        NodeList links = relationships.getElementsByTagNameNS(REL, "Relationship");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            if (id.equals(link.getAttribute("Id")) && link.getAttribute("Target").startsWith("media/iped-report-")) {
                return true;
            }
        }
        return false;
    }

    /** A lone screenshot uses the full frame; visible audio text keeps the reference's second column. */
    private Element chatTable(Evidence item, boolean includeText) throws Exception {
        boolean withTranscript = includeText && !item.transcripts.isEmpty();
        long[] widths = mediaWidths(withTranscript ? 2 : 1);
        Element table = reportTable(widths);
        append((Element) table.getFirstChild(), W, "w:tblCaption").setAttributeNS(W, "w:val",
                withTranscript ? "IPED_CHAT_TRANSCRIPT" : "IPED_CHAT_SINGLE");
        Element row = append(table, W, "w:tr");
        Element screenshot = reportCell(row, widths[0], null);
        screenshot.appendChild(picture(item.image, item.imageDescription, widths[0], 5486400));
        if (withTranscript) {
            appendChatTranscripts(reportCell(row, widths[1], null), item);
        }
        Element heading = append(table, W, "w:tr");
        Element information = append(table, W, "w:tr");
        for (long width : widths) {
            reportCell(heading, width, "DDD9C3").appendChild(reportParagraph(FILE_INFORMATION, false, false, true));
            appendInformation(reportCell(information, width, "EEEEEE"), item);
        }
        return table;
    }

    private void appendChatTranscripts(Element transcript, Evidence item) {
        if (!item.transcripts.isEmpty()) {
            transcript.appendChild(reportParagraph("Transcrição do áudio", true, false, true));
            for (Evidence audio : item.transcripts) {
                Element quote = reportParagraph("\u201c" + audio.text + "\u201d", false, false, false);
                Element properties = (Element) quote.getElementsByTagNameNS(W, "rPr").item(0);
                properties.insertBefore(element(document, W, "w:i"), properties.getElementsByTagNameNS(W, "sz").item(0));
                transcript.appendChild(quote);
                String hash = audio.metadata.get("Hash");
                if (hash != null && !hash.isEmpty()) {
                    Element p = reportParagraph("", false, true, false);
                    Element label = (Element) p.getElementsByTagNameNS(W, "r").item(0);
                    ((Element) label.getElementsByTagNameNS(W, "t").item(0)).setTextContent("Hash:");
                    append((Element) label.getFirstChild(), W, "w:b");
                    append((Element) label.getFirstChild(), W, "w:highlight").setAttributeNS(W, "w:val", "lightGray");
                    Element value = append(p, W, "w:r");
                    reportRun(value, false, true);
                    Element text = append(value, W, "w:t");
                    text.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve");
                    text.setTextContent(xmlText(" " + hash));
                    transcript.appendChild(p);
                }
                transcript.appendChild(reportParagraph("", false, false, false));
            }
        }
    }

    /** Reuse the approved left crop and its metadata, adding a distinct crop in the right column. */
    private void pairChatTable(Element table, Evidence item, boolean includeText) throws Exception {
        long[] widths = mediaWidths(2);
        NodeList rows = table.getElementsByTagNameNS(W, "tr");
        Element row = (Element) rows.item(0);
        Element left = (Element) row.getElementsByTagNameNS(W, "tc").item(0);
        Element oldTranscript = (Element) row.getElementsByTagNameNS(W, "tc").item(1);
        if (oldTranscript != null) {
            for (Node child = oldTranscript.getFirstChild(); child != null;) {
                Node next = child.getNextSibling();
                if (!"tcPr".equals(child.getLocalName())) {
                    left.appendChild(child);
                }
                child = next;
            }
        }
        Element grid = (Element) table.getElementsByTagNameNS(W, "tblGrid").item(0);
        while (grid.hasChildNodes()) {
            grid.removeChild(grid.getFirstChild());
        }
        for (long width : widths) {
            append(grid, W, "w:gridCol").setAttributeNS(W, "w:w", Long.toString(width));
        }
        for (int i = 0; i < 3; i++) {
            Element currentRow = (Element) rows.item(i);
            while (currentRow.getElementsByTagNameNS(W, "tc").getLength() > 1) {
                currentRow.removeChild(currentRow.getElementsByTagNameNS(W, "tc").item(1));
            }
            Element cell = (Element) currentRow.getElementsByTagNameNS(W, "tc").item(0);
            ((Element) cell.getElementsByTagNameNS(W, "tcW").item(0)).setAttributeNS(W, "w:w", Long.toString(widths[0]));
            Element right = reportCell(currentRow, widths[1], i == 1 ? "DDD9C3" : i == 2 ? "EEEEEE" : null);
            if (i == 0) {
                right.appendChild(picture(item.image, item.imageDescription, widths[1], 5486400));
                if (includeText) {
                    appendChatTranscripts(right, item);
                }
            } else if (i == 1) {
                right.appendChild(reportParagraph(FILE_INFORMATION, false, false, true));
            } else {
                appendInformation(right, item);
            }
        }
        NodeList extents = left.getElementsByTagNameNS(WP, "extent");
        for (int i = 0; i < extents.getLength(); i++) {
            Element extent = (Element) extents.item(i);
            long cx = Long.parseLong(extent.getAttribute("cx"));
            long maximum = (widths[0] - 216) * 635;
            if (cx > maximum) {
                String height = Long.toString(Long.parseLong(extent.getAttribute("cy")) * maximum / cx);
                extent.setAttribute("cx", Long.toString(maximum));
                extent.setAttribute("cy", height);
                Element shape = (Element) ((Element) extent.getParentNode()).getElementsByTagNameNS(A, "ext").item(0);
                shape.setAttribute("cx", Long.toString(maximum));
                shape.setAttribute("cy", height);
            }
        }
        Element properties = (Element) table.getFirstChild();
        Element caption = (Element) properties.getElementsByTagNameNS(W, "tblCaption").item(0);
        if (caption == null) {
            caption = append(properties, W, "w:tblCaption");
        }
        caption.setAttributeNS(W, "w:val", "IPED_CHAT_PAIR");
    }

    /** Other message items retain the reference's numbered four-column ledger. */
    private Element messageTable(List<Evidence> evidence, boolean includeText) throws Exception {
        long width = pageWidthTwips();
        long[] widths = { width * 426 / 9072, width * 1842 / 9072, width * 3969 / 9072, 0 };
        widths[3] = width - widths[0] - widths[1] - widths[2];
        Element table = reportTable(widths);
        Element heading = append(table, W, "w:tr");
        append(append(heading, W, "w:trPr"), W, "w:tblHeader");
        String[] labels = { "", "Interlocutores", "Conteúdo da mensagem", FILE_INFORMATION };
        for (int i = 0; i < labels.length; i++) {
            reportCell(heading, widths[i], "DDD9C3").appendChild(reportParagraph(labels[i], false, false, true));
        }
        List<Evidence> messages = new ArrayList<>();
        for (Evidence item : evidence) {
            messages.addAll(item.messages.isEmpty() ? java.util.Collections.singletonList(item) : item.messages);
        }
        for (int i = 0; i < messages.size(); i++) {
            Evidence item = messages.get(i);
            Element row = append(table, W, "w:tr");
            reportCell(row, widths[0], null).appendChild(reportParagraph(Integer.toString(i + 1), false, false, false));
            Element participants = reportCell(row, widths[1], null);
            appendText(participants, item.participants);
            Element content = reportCell(row, widths[2], null);
            if (item.image != null) {
                content.appendChild(picture(item.image, item.imageDescription, widths[2]));
            }
            appendText(content, includeText ? item.text : null);
            appendInformation(reportCell(row, widths[3], "EEEEEE"), item);
        }
        return table;
    }

    /** Six enlarged fragments and a chronological grid follow the source's video layout. */
    private Element videoTable(Evidence item, boolean includeText) throws Exception {
        long width = pageWidthTwips();
        long[] widths = { width * 2268 / 9072, width * 2127 / 9072, 0 };
        widths[2] = width - widths[0] - widths[1];
        Element table = reportTable(widths);
        List<BufferedImage> fragments = WordReportVideo.sample(item.videoFrames, 6);
        int rows = Math.max(1, (fragments.size() + 1) / 2);
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            Element row = append(table, W, "w:tr");
            for (int col = 0; col < 2; col++) {
                Element cell = reportCell(row, widths[col], null);
                int index = rowIndex * 2 + col;
                if (index < fragments.size()) {
                    cell.appendChild(picture(fragments.get(index), item.title, widths[col], 1463040));
                    cell.appendChild(videoCaption("Fragmentos do vídeo"));
                } else {
                    cell.appendChild(reportParagraph("", false, false, false));
                }
            }
            Element contact = reportCell(row, widths[2], null);
            Element merge = append((Element) contact.getFirstChild(), W, "w:vMerge");
            if (rowIndex == 0) {
                merge.setAttributeNS(W, "w:val", "restart");
                if (item.image != null) {
                    contact.appendChild(picture(item.image, item.title, widths[2]));
                }
                contact.appendChild(videoCaption("Vídeo separado em frames"));
                if (includeText && item.text != null && !item.text.isEmpty()) {
                    contact.appendChild(reportParagraph("Áudio constante no vídeo", true, true, false));
                    appendText(contact, item.text);
                }
            } else {
                contact.appendChild(reportParagraph("", false, false, false));
            }
        }
        Element header = reportCell(append(table, W, "w:tr"), width, "DDD9C3");
        span(header, 3);
        header.appendChild(reportParagraph(FILE_INFORMATION, false, false, true));
        Element info = reportCell(append(table, W, "w:tr"), width, "EEEEEE");
        span(info, 3);
        appendInformation(info, item);
        NodeList paragraphs = table.getElementsByTagNameNS(W, "p");
        for (int i = 0; i + 1 < paragraphs.getLength(); i++) {
            Element properties = (Element) ((Element) paragraphs.item(i)).getFirstChild();
            if (properties.getElementsByTagNameNS(W, "keepNext").getLength() == 0) {
                append(properties, W, "w:keepNext");
            }
        }
        return table;
    }

    private void span(Element cell, int columns) {
        Element properties = (Element) cell.getFirstChild();
        Element span = element(document, W, "w:gridSpan");
        span.setAttributeNS(W, "w:val", Integer.toString(columns));
        properties.insertBefore(span, properties.getElementsByTagNameNS(W, "shd").item(0));
    }

    private Element videoCaption(String text) {
        Element p = reportParagraph(text, false, true, true);
        Element properties = (Element) ((Element) p.getElementsByTagNameNS(W, "r").item(0)).getFirstChild();
        properties.insertBefore(element(document, W, "w:i"), properties.getElementsByTagNameNS(W, "sz").item(0));
        ((Element) properties.getElementsByTagNameNS(W, "sz").item(0)).setAttributeNS(W, "w:val", "20");
        return p;
    }

    private void appendText(Element cell, String text) {
        if (text != null && !text.isEmpty()) {
            for (String line : text.split("\\R", -1)) {
                cell.appendChild(reportParagraph(line, false, false, false));
            }
        }
        if (cell.getElementsByTagNameNS(W, "p").getLength() == 0) {
            cell.appendChild(reportParagraph("", false, false, false));
        }
    }

    private void appendInformation(Element cell, Evidence item) {
        for (String key : new String[] { "Hash", FILE_NAME, "Caminho" }) {
            Element p = reportParagraph("", false, true, false);
            Element label = append(p, W, "w:r");
            reportRun(label, true, true);
            append((Element) label.getFirstChild(), W, "w:highlight").setAttributeNS(W, "w:val", "lightGray");
            Element labelText = append(label, W, "w:t");
            labelText.setTextContent(key + ":");
            Element run = append(p, W, "w:r");
            reportRun(run, false, true);
            Element value = append(run, W, "w:t");
            value.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve");
            String content = item.metadata.get(key);
            if (content == null && FILE_NAME.equals(key)) {
                content = item.title;
            }
            value.setTextContent(xmlText(" " + (content == null ? "" : content)));
            cell.appendChild(p);
        }
    }

    private Element reportCell(Element row, long columnWidth, String fill) {
        Element cell = append(row, W, "w:tc");
        Element properties = append(cell, W, "w:tcPr");
        Element width = append(properties, W, "w:tcW");
        width.setAttributeNS(W, "w:w", Long.toString(columnWidth));
        width.setAttributeNS(W, "w:type", "dxa");
        if (fill != null) {
            append(properties, W, "w:shd").setAttributeNS(W, "w:fill", fill);
        }
        return cell;
    }

    private Element reportParagraph(String value, boolean bold, boolean metadata, boolean centered) {
        Element p = element(document, W, "w:p");
        Element properties = append(p, W, "w:pPr");
        Element spacing = append(properties, W, "w:spacing");
        spacing.setAttributeNS(W, "w:before", "0");
        spacing.setAttributeNS(W, "w:after", "0");
        spacing.setAttributeNS(W, "w:line", "240");
        spacing.setAttributeNS(W, "w:lineRule", "auto");
        Element indent = append(properties, W, "w:ind");
        indent.setAttributeNS(W, "w:firstLine", "0");
        indent.setAttributeNS(W, "w:left", "0");
        indent.setAttributeNS(W, "w:right", "0");
        append(properties, W, "w:jc").setAttributeNS(W, "w:val", centered ? "center" : "left");
        if (centered) {
            append(properties, W, "w:keepNext");
        }
        Element run = append(p, W, "w:r");
        reportRun(run, bold, metadata);
        Element text = append(run, W, "w:t");
        text.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve");
        text.setTextContent(xmlText(value == null ? "" : value));
        return p;
    }

    private void reportRun(Element run, boolean bold, boolean metadata) {
        Element properties = append(run, W, "w:rPr");
        Element fonts = append(properties, W, "w:rFonts");
        fonts.setAttributeNS(W, "w:ascii", metadata ? "Courier New" : "Arial");
        fonts.setAttributeNS(W, "w:hAnsi", metadata ? "Courier New" : "Arial");
        append(properties, W, "w:sz").setAttributeNS(W, "w:val", metadata ? "18" : "24");
        if (bold) {
            append(properties, W, "w:b");
        }
    }

    private long pageWidthTwips() {
        long width = Long.MAX_VALUE;
        NodeList sections = document.getElementsByTagNameNS(W, "sectPr");
        for (int i = 0; i < sections.getLength(); i++) {
            Element section = (Element) sections.item(i);
            Element size = (Element) section.getElementsByTagNameNS(W, "pgSz").item(0);
            Element margins = (Element) section.getElementsByTagNameNS(W, "pgMar").item(0);
            if (size != null && margins != null) {
                long available = Long.parseLong(size.getAttributeNS(W, "w"))
                        - Long.parseLong(margins.getAttributeNS(W, "left"))
                        - Long.parseLong(margins.getAttributeNS(W, "right"));
                String gutter = margins.getAttributeNS(W, "gutter");
                width = Math.min(width, available - (gutter.isEmpty() ? 0 : Long.parseLong(gutter)));
            }
        }
        return width == Long.MAX_VALUE ? 8640 : width;
    }

    private Element paragraph(String value, boolean bold) {
        Element p = element(document, W, "w:p");
        Element pr = append(p, W, "w:pPr");
        Element spacing = append(pr, W, "w:spacing");
        spacing.setAttributeNS(W, "w:after", "120");
        if (bold) {
            append(pr, W, "w:keepNext");
        }
        Element run = append(p, W, "w:r");
        if (bold) {
            append(append(run, W, "w:rPr"), W, "w:b");
        }
        Element t = append(run, W, "w:t");
        t.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve");
        t.setTextContent(xmlText(value == null ? "" : value));
        return p;
    }

    private static String xmlText(String text) {
        StringBuilder clean = new StringBuilder();
        text.codePoints().filter(c -> c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF).forEach(clean::appendCodePoint);
        return clean.toString();
    }

    private Element picture(BufferedImage image, String description, long columnWidth) throws IOException {
        return picture(image, description, columnWidth, 3657600);
    }

    private Element picture(BufferedImage image, String description, long columnWidth, long maxHeight) throws IOException {
        String name = "iped-report-" + UUID.randomUUID() + ".png";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        additions.put("word/media/" + name, bytes.toByteArray());
        String id = "rId" + UUID.randomUUID().toString().replace("-", "");
        Element relationship = append(relationships.getDocumentElement(), REL, "Relationship");
        relationship.setAttribute("Id", id);
        relationship.setAttribute("Type", R + "/image");
        relationship.setAttribute("Target", "media/" + name);
        Element type = append(contentTypes.getDocumentElement(), CT, "Override");
        type.setAttribute("PartName", "/word/media/" + name);
        type.setAttribute("ContentType", "image/png");

        long maxWidth = (columnWidth - 216) * 635;
        double scale = Math.min(9525, Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight()));
        String cx = Long.toString(Math.max(1, (long) (image.getWidth() * scale)));
        String cy = Long.toString(Math.max(1, (long) (image.getHeight() * scale)));
        Element p = reportParagraph("", false, false, true);
        Element drawing = append(append(p, W, "w:r"), W, "w:drawing");
        Element inline = append(drawing, WP, "wp:inline");
        Element extent = append(inline, WP, "wp:extent");
        extent.setAttribute("cx", cx);
        extent.setAttribute("cy", cy);
        Element docPr = append(inline, WP, "wp:docPr");
        docPr.setAttribute("id", Integer.toString(++drawingId));
        docPr.setAttribute("name", name);
        docPr.setAttribute("descr", xmlText(description == null ? "" : description));
        Element graphic = append(inline, A, "a:graphic");
        Element data = append(graphic, A, "a:graphicData");
        data.setAttribute("uri", PIC);
        Element pic = append(data, PIC, "pic:pic");
        Element nv = append(pic, PIC, "pic:nvPicPr");
        Element cpr = append(nv, PIC, "pic:cNvPr");
        cpr.setAttribute("id", "0");
        cpr.setAttribute("name", name);
        append(nv, PIC, "pic:cNvPicPr");
        Element fill = append(pic, PIC, "pic:blipFill");
        append(fill, A, "a:blip").setAttributeNS(R, "r:embed", id);
        append(append(fill, A, "a:stretch"), A, "a:fillRect");
        Element shape = append(pic, PIC, "pic:spPr");
        Element xfrm = append(shape, A, "a:xfrm");
        Element off = append(xfrm, A, "a:off");
        off.setAttribute("x", "0");
        off.setAttribute("y", "0");
        Element ext = append(xfrm, A, "a:ext");
        ext.setAttribute("cx", cx);
        ext.setAttribute("cy", cy);
        Element geom = append(shape, A, "a:prstGeom");
        geom.setAttribute("prst", "rect");
        append(geom, A, "a:avLst");
        return p;
    }

    static Element element(Document doc, String namespace, String name) {
        return doc.createElementNS(namespace, name);
    }

    private static Element append(Element parent, String namespace, String name) {
        Element child = element(parent.getOwnerDocument(), namespace, name);
        parent.appendChild(child);
        return child;
    }

    private static byte[] serialize(Document doc) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        javax.xml.transform.Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        return output.toByteArray();
    }

    public void write(Path output) throws Exception {
        if (!fingerprint.equals(fingerprint(source))) {
            throw new IOException(Messages.getString("WordReport.SourceChanged"));
        }
        if (source.toRealPath().equals(output.toAbsolutePath().normalize()) || Files.exists(output)) {
            throw new IOException(Messages.getString("WordReport.NewFileOnly"));
        }
        Map<String, byte[]> replaced = new LinkedHashMap<>(additions);
        replaced.put("word/document.xml", serialize(document));
        replaced.put("word/_rels/document.xml.rels", serialize(relationships));
        replaced.put("[Content_Types].xml", serialize(contentTypes));
        replaced.put("word/settings.xml", serialize(settings));
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".iped-report-", ".docx");
        try {
            try (ZipFile zip = new ZipFile(source.toFile()); OutputStream file = Files.newOutputStream(temporary);
                    ZipOutputStream out = new ZipOutputStream(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!replaced.containsKey(entry.getName())) {
                        out.putNextEntry(new ZipEntry(entry.getName()));
                        try (InputStream in = zip.getInputStream(entry)) {
                            in.transferTo(out);
                        }
                        out.closeEntry();
                    }
                }
                for (Map.Entry<String, byte[]> entry : replaced.entrySet()) {
                    out.putNextEntry(new ZipEntry(entry.getKey()));
                    out.write(entry.getValue());
                    out.closeEntry();
                }
            }
            if (!fingerprint.equals(fingerprint(source))) {
                throw new IOException(Messages.getString("WordReport.SourceChanged"));
            }
            Files.move(temporary, output); // Never replace the report or the original evidence.
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Save exactly the prepared document that was presented for review. */
    public void savePreparedCopy(Path prepared, Path output) throws Exception {
        if (!fingerprint.equals(fingerprint(source))) {
            throw new IOException(Messages.getString("WordReport.SourceChanged"));
        }
        if (source.toRealPath().equals(output.toAbsolutePath().normalize()) || Files.exists(output)) {
            throw new IOException(Messages.getString("WordReport.NewFileOnly"));
        }
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".iped-report-", ".docx");
        try {
            Files.copy(prepared, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!fingerprint.equals(fingerprint(source))) {
                throw new IOException(Messages.getString("WordReport.SourceChanged"));
            }
            Files.move(temporary, output);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Update only the selected working report with the exact reviewed bytes. */
    public void savePreparedReport(Path prepared) throws Exception {
        if (!isSourceCurrent()) {
            throw new IOException(Messages.getString("WordReport.SourceChanged"));
        }
        Path output = source.toRealPath();
        Path temporary = Files.createTempFile(output.getParent(), ".iped-report-", ".docx");
        try {
            Files.copy(prepared, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!isSourceCurrent()) {
                throw new IOException(Messages.getString("WordReport.SourceChanged"));
            }
            try {
                Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
