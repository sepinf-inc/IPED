package iped.app.ui.ai.util;

import iped.data.IItem;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import iped.parsers.standard.StandardParser;
import iped.parsers.whatsapp.WhatsAppParser;

/**
 * A utility service responsible for validating and extracting text content 
 * from IPED digital evidence items, specifically targeting WhatsApp HTML exports.
 * <p>
 * This class acts as a boundary layer between IPED's raw file storage and the 
 * AI Backend payload builders. It ensures that only potentially valid text data 
 * is loaded into memory and sent across the network.
 * </p>
 */
public class AIWhatsappChatExtractor {

    private final String whatsAppChatContentType = WhatsAppParser.WHATSAPP_CHAT.toString();

    /**
     * <p>
    * Basic validation to check whether the item is a WhatsApp chat by content type.
     * Handles standard files and virtual files extracted from databases (which may lack extensions).
     * <p>
     * @param item The IPED evidence item to inspect.
    * @return true if the file indicates WhatsApp chat content; false otherwise.
     */
    public boolean isWhatsAppChatType(IItem item) {
        if (item == null) {
            return false;
        }

        if (item.getMediaType() != null && whatsAppChatContentType.equals(item.getMediaType().toString())) {
            return true;
        }

        return item.getMetadata() != null
            && whatsAppChatContentType.equals(item.getMetadata().get(StandardParser.INDEXER_CONTENT_TYPE));
    }

    /**
     * Extracts the raw file content from the IItem into a UTF-8 encoded String.
     * @param item The IPED evidence item containing the chat log.
     * @return The complete, raw HTML string.
     * @throws IllegalArgumentException if the item is null.
     * @throws IllegalStateException if the underlying file stream cannot be opened.
     * @throws Exception if reading the stream fails due to an I/O error.
     */
    public String extractHtml(IItem item) throws Exception {
        if (!isWhatsAppChatType(item)) {
            throw new IllegalArgumentException("Selected file does not appear to be a WhatsApp chat export.");
        }

        // Try-with-resources ensures streams are closed automatically, preventing memory/file handle leaks.
        try (InputStream is = item.getBufferedInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
             
            if (is == null) {
                throw new IllegalStateException("Could not open input stream for item ID: " + item.getId());
            }

            int nRead;
            byte[] data = new byte[16384]; // 16KB chunks
            
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}