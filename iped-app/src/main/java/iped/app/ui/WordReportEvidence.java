package iped.app.ui;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.IOException;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.search.IPEDSearcher;
import iped.engine.task.index.IndexItem;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.ImageUtil;
import iped.viewers.HtmlViewerCapture;
import iped.viewers.util.ImageMetadataUtil;

/** Reads the same items and previews used by the analysis interface. */
public class WordReportEvidence {

    static boolean isChat(String mime, String name) {
        return mime != null && (mime.contains("chat") || mime.equals("application/x-ufed-html-report"))
                || "text/html".equals(mime) && name != null && name.startsWith("Chat_");
    }

    static boolean isDocument(String mime) {
        return mime != null && (mime.startsWith("text/") || mime.equals("application/pdf")
                || mime.equals("application/xhtml+xml") || mime.equals("application/rtf")
                || mime.startsWith("application/msword") || mime.startsWith("application/vnd.ms-word")
                || mime.startsWith("application/vnd.ms-excel") || mime.startsWith("application/vnd.ms-powerpoint")
                || mime.startsWith("application/vnd.oasis.opendocument.")
                || mime.startsWith("application/vnd.openxmlformats-officedocument."));
    }

    static class ChatImage {
        final IItemId id;
        final String name;
        final String path;
        final BufferedImage thumbnail;

        ChatImage(IItemId id, String name, String path, BufferedImage thumbnail) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.thumbnail = thumbnail;
        }
    }

    /** Resolve linked thumbnails within the chat's evidence source. Keep duplicate paths selectable. */
    static Map<String, List<ChatImage>> findChatImages(IPEDMultiSource source, IItemId chat,
            List<HtmlViewerCapture.Attachment> images) throws Exception {
        Map<String, List<ChatImage>> matches = new LinkedHashMap<>();
        String uuid = source.getSearcher().doc(source.getLuceneId(chat)).get(IndexItem.EVIDENCE_UUID);
        for (HtmlViewerCapture.Attachment image : images) {
            if (matches.containsKey(image.hash)) {
                continue;
            }
            List<ChatImage> files = new ArrayList<>();
            matches.put(image.hash, files);
            TermQuery query = new TermQuery(new Term(IndexItem.HASH, image.hash.toLowerCase(Locale.ROOT)));
            for (IItemId id : new IPEDSearcher(source, query).multiSearch().getIterator()) {
                if (id.getSourceId() == chat.getSourceId()) {
                    Document doc = source.getSearcher().doc(source.getLuceneId(id));
                    if (uuid != null && uuid.equals(doc.get(IndexItem.EVIDENCE_UUID))
                            && MetadataUtil.isImageType(doc.get(IndexItem.CONTENTTYPE))) {
                        BytesRef bytes = doc.getBinaryValue(IndexItem.THUMB);
                        BufferedImage thumbnail = bytes == null ? null : ImageUtil.getSubSampledImage(
                                new ByteArrayInputStream(bytes.bytes, bytes.offset, bytes.length), 144);
                        if (thumbnail == null) {
                            IItem item = source.getItemByItemId(id);
                            if (item != null) {
                                try {
                                    thumbnail = readChatImage(item, 144);
                                } catch (IOException e) {
                                    // The crop dialog can still use the thumbnail visible in the chat.
                                } finally {
                                    item.dispose();
                                }
                            }
                        }
                        files.add(new ChatImage(id, doc.get(IndexItem.NAME), doc.get(IndexItem.PATH), thumbnail));
                    }
                }
            }
        }
        return matches;
    }

    private static BufferedImage readChatImage(IItem item, int size) throws IOException {
        BufferedImage image = ImageUtil.getSubSampledImage(item, size);
        if (image != null) {
            try (InputStream in = new BufferedInputStream(item.getSeekableInputStream())) {
                int orientation = ImageMetadataUtil.getOrientation(in);
                if (orientation > 1) {
                    image = ImageUtil.applyOrientation(image, orientation);
                }
            }
        }
        return image;
    }

    static WordReportDocument.Evidence readChatCapture(IPEDMultiSource source, IItemId id,
            WordReportChatSelection.Result capture) throws IOException {
        return readCapture(source, id, capture);
    }

    static WordReportDocument.Evidence readCapture(IPEDMultiSource source, IItemId id,
            WordReportChatSelection.Result capture) throws IOException {
        IItem item = source.getItemByItemId(id);
        try {
            WordReportDocument.Evidence result = capturedEvidence(item, capture);
            if (capture.attachment != null) {
                IItem image = source.getItemByItemId(capture.attachment);
                try {
                    WordReportDocument.Evidence attachment = new WordReportDocument.Evidence();
                    attachment.title = image.getName();
                    attachment.image = readChatImage(image, 2400);
                    if (attachment.image == null) {
                        throw new IOException(Messages.getString("WordReport.ChatImageUnreadable", image.getName()));
                    }
                    result.attachment = attachment;
                } finally {
                    image.dispose();
                }
            }
            return result;
        } finally {
            item.dispose();
        }
    }

    static WordReportDocument.Evidence capturedEvidence(IItem item, WordReportChatSelection.Result capture) {
        WordReportDocument.Evidence result = new WordReportDocument.Evidence();
        result.title = item.getName();
        result.metadata.putAll(readMetadata(item));
        result.chatCapture = !capture.document;
        result.image = capture.image;
        result.imageDescription = item.getName();
        result.transcripts.addAll(capture.transcripts);
        return result;
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

    /** Selection previews use one video frame; report review builds the full grid. */
    static WordReportDocument.Evidence preview(IPEDMultiSource source, IItemId id, boolean includeText, Path work) throws Exception {
        return read(source, id, includeText, work, false);
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
            } else if (type == 2) {
                try {
                    result.image = WordReportVideo.preview(item, work);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    // Keep the file information visible if no video frame can be displayed.
                }
            }
            if (type == 1 && result.image == null) {
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
