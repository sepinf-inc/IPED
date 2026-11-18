package iped.app.ui;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.ImageUtil;

/** Reads the same items and previews used by the analysis interface. */
public class WordReportEvidence {

    static boolean isChat(String mime, String name) {
        return mime != null && (mime.contains("chat") || mime.equals("application/x-ufed-html-report"))
                || "text/html".equals(mime) && name != null && name.startsWith("Chat_");
    }

    static WordReportDocument.Evidence readChatCapture(IPEDMultiSource source, IItemId id,
            WordReportChatSelection.Result capture) {
        IItem item = source.getItemByItemId(id);
        try {
            WordReportDocument.Evidence result = new WordReportDocument.Evidence();
            result.title = item.getName();
            result.metadata.putAll(readMetadata(item));
            result.chatCapture = true;
            result.image = capture.image;
            result.imageDescription = item.getName();
            result.transcripts.addAll(capture.transcripts);
            return result;
        } finally {
            item.dispose();
        }
    }

    public static int mediaType(String mime) {
        if (MetadataUtil.isImageType(mime)) {
            return 1;
        }
        if (MetadataUtil.isVideoType(mime)) {
            return 2;
        }
        if (mime.startsWith("audio/")) {
            return 3;
        }
        if (mime.contains("message") || mime.contains("chat")) {
            return 4;
        }
        return 5;
    }

    public static WordReportDocument.Evidence read(IPEDMultiSource source, IItemId id, boolean includeText, Path work) throws Exception {
        return read(source, id, includeText, work, true);
    }

    /** Selection previews reuse existing thumbnails; full video generation waits for report review. */
    static WordReportDocument.Evidence preview(IPEDMultiSource source, IItemId id, boolean includeText) throws Exception {
        return read(source, id, includeText, null, false);
    }

    private static WordReportDocument.Evidence read(IPEDMultiSource source, IItemId id, boolean includeText,
            Path work, boolean generateFrames) throws Exception {
        IItem item = source.getItemByItemId(id);
        try {
            if (isChat(item.getMediaType().toString(), item.getName())) {
                throw new java.io.IOException(Messages.getString("WordReport.OpenChatPreview"));
            }
            WordReportDocument.Evidence result = new WordReportDocument.Evidence();
            result.title = item.getName();
            result.metadata.putAll(readMetadata(item));
            int type = mediaType(item.getMediaType().toString());
            result.message = type == 4;
            result.audio = type == 3;
            result.video = type == 2;
            result.participants = result.message ? participants(item) : null;
            if (type == 1) {
                result.image = ImageUtil.getSubSampledImage(item, 1600);
                result.imageDescription = item.getName();
            } else if (type == 2 && generateFrames) {
                result.videoFrames.addAll(WordReportVideo.read(item, work));
                result.image = WordReportVideo.contactSheet(result.videoFrames);
                result.imageDescription = item.getName();
            } else if (type == 2 && item.getViewFile() != null) {
                result.image = ImageIO.read(item.getViewFile());
            }
            if ((type == 1 || type == 2) && result.image == null) {
                if (item.getThumb() != null) {
                    result.image = ImageIO.read(new ByteArrayInputStream(item.getThumb()));
                }
                result.imageDescription = item.getName();
            }
            result.imageUnavailable = (type == 1 || type == 2) && result.image == null;
            if (includeText) {
                result.text = readText(item);
            }
            return result;
        } finally {
            item.dispose();
        }
    }

    /** The reference report identifies files with these three fields only. */
    static Map<String, String> readMetadata(IItem item) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Hash", item.getHash() == null ? "" : item.getHash());
        values.put(WordReportDocument.FILE_NAME, item.getName());
        values.put("Caminho", item.getPath());
        return values;
    }

    private static String participants(IItem item) {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        for (String property : new String[] { ExtraProperties.COMMUNICATION_FROM,
                ExtraProperties.COMMUNICATION_TO, ExtraProperties.PARTICIPANTS,
                ExtraProperties.CONVERSATION_PARTICIPANTS }) {
            values.addAll(Arrays.asList(item.getMetadata().getValues(property)));
        }
        return String.join("\n", values);
    }

    static String readText(IItem item) throws java.io.IOException {
        Object transcription = item.getExtraAttribute(ExtraProperties.TRANSCRIPT_ATTR);
        if (transcription != null) {
            return transcription.toString();
        }
        String metadataTranscription = item.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR);
        if (metadataTranscription != null && !metadataTranscription.isEmpty()) {
            return metadataTranscription;
        }
        String body = item.getMetadata().get(ExtraProperties.MESSAGE_BODY);
        if (body != null && !body.isEmpty()) {
            return body;
        }
        try (Reader reader = item.getTextReader()) {
            if (reader == null) {
                return null;
            }
            char[] buffer = new char[8192];
            StringBuilder value = new StringBuilder();
            int read;
            while ((read = reader.read(buffer)) != -1) {
                value.append(buffer, 0, read);
            }
            return value.toString();
        }
    }

}
