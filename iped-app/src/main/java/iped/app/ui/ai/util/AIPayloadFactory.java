package iped.app.ui.ai.util;

import iped.app.ui.ai.model.ContextFileEntry;
import iped.app.ui.ai.backend.AIInitMultiChatRequest;
import iped.app.ui.ai.backend.AIInitMultiChatFullRequest;
import iped.app.ui.ai.backend.AIInitMultiChatRequest.SummarizedChat;
import iped.app.ui.ai.util.AIWhatsappChatExtractor;
import iped.data.IItem;
import iped.properties.ExtraProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Factory utility that translates IPED's internal UI state into 
 * standard Data Transfer Objects (DTOs) for the network layer.
 * <p>
 * This class acts as a boundary layer. It ensures that the network client 
 * never needs to know about IPED-specific classes (like {@link ContextFileEntry}), 
 * and enforces strict validation rules before data is allowed to leave the application
 * </p>
 */
public class AIPayloadFactory {

    /**
     * Builds the Multi-Chat initialization payload by extracting summaries from the UI context.
     * <p>
     * <b>Memory Guardrail:</b> This method aggressively filters out files that do not 
     * have pre-computed summaries. Sending raw HTML for multiple files would instantly 
     * exceed the LLM's token context window and crash the backend.
     * </p>
     * * @param entries The raw list of files currently held in the UI's {@link AIContextManager}.
     * @return A clean, populated DTO ready for Gson serialization.
     * @throws IllegalArgumentException if the provided list yields zero valid summaries, 
     * meaning a Multi-Chat session cannot be legally formed.
     */
    public static AIInitMultiChatRequest buildMultiChatRequest(List<ContextFileEntry> entries) {
        List<SummarizedChat> summarizedChats = new ArrayList<>();

        for (ContextFileEntry entry : entries) {
            // Skip files that failed validation
            if (!entry.isValidForContext()) {
                continue;
            }

            // In Multi-Chat, summaries are strictly required
            // If it doesn't have a summary, skip it to avoid blowing up the LLM context window
            if (!entry.hasSummary()) {
                continue;
            }

            // Map IPED data to backend variables
            IItem item = entry.getItem();

            // Get the globally unique hash directly from the item
            String chatId = item.getHash();
            
            // Fallback to internal integer ID if the hash is missing 
            if (chatId == null || chatId.trim().isEmpty()) {
                chatId = String.valueOf(item.getId());
            }
            
            String chatName = entry.getFileName();
            
            // Adapter Pattern: The Python backend expects lists for summaries and IDs
            // Since IPED joined them into one block, send a list of size 1
            List<String> summariesList = extractList(item, ExtraProperties.SUMMARY);
            List<String> summaryIdsList = extractList(item, ExtraProperties.CHUNK_IDS);

            // If the extraction fails, fallback to the UI summary
            if (summariesList.isEmpty()) {
                summariesList = Collections.singletonList(entry.getSummary());
            } 

            // Ensure there is an exact 1:1 mapping of IDs to summaries
            // If the metadata is missing IDs or the sizes don't match, generate sequential IDs
            // to prevent crashing the citation engine
            if (summaryIdsList.isEmpty() || summaryIdsList.size() != summariesList.size()) {
                summaryIdsList.clear(); // Wipe any mismatched IDs
                for (int i = 0; i < summariesList.size(); i++) {
                    summaryIdsList.add("summary_fallback_" + (i + 1));
                }
            }

            // Build the inner DTO, representing a single summarized chat
            SummarizedChat chatDto = new SummarizedChat(chatId, chatName, summariesList, summaryIdsList);
            summarizedChats.add(chatDto);
        }

        if (summarizedChats.isEmpty()) {
            throw new IllegalArgumentException("No valid file with summary was found for multi-chat analysis");
        }

        // Return the final wrapper DTO
        return new AIInitMultiChatRequest(summarizedChats);
    }

    /**
     * Builds a payload for the Full Multi-Chat initialization endpoint.
     * This bypasses summaries entirely and extracts the raw HTML from the IPED items.
     *
     * @param contextEntries The valid files currently in the AI context.
     * @return A populated request object containing a list of raw HTML strings.
     * @throws IllegalArgumentException if no valid HTML content could be extracted.
     */
    public static AIInitMultiChatFullRequest buildMultiChatFullRequest(List<ContextFileEntry> contextEntries) {
        List<String> rawHtmlContents = new ArrayList<>();
        
        AIWhatsappChatExtractor extractor = new AIWhatsappChatExtractor();

        for (ContextFileEntry entry : contextEntries) {
            // Skip files that failed baseline validation
            if (!entry.isValidForContext()) {
                continue;
            }

            try {
                // Pull the raw HTML from the IPED IItem
                String rawHtml = extractor.extractHtml(entry.getItem());
                
                if (rawHtml != null && !rawHtml.trim().isEmpty()) {
                    rawHtmlContents.add(rawHtml);
                } else {
                    System.err.println("Warning: Extracted HTML was empty for item ID " + entry.getItem().getId());
                }
            } catch (Exception e) {
                System.err.println("Failed to extract HTML for item ID " + entry.getItem().getId() + ": " + e.getMessage());
            }
        }

        if (rawHtmlContents.isEmpty()) {
            throw new IllegalArgumentException("No valid HTML content was extracted for the full multi-chat analysis.");
        }

        return new AIInitMultiChatFullRequest(rawHtmlContents);
    }

    /**
     * Safely extracts a list of strings from IPED's flexible metadata/attribute storage.
     */
    private static List<String> extractList(IItem item, String key) {
        List<String> result = new ArrayList<>();
        if (item == null) return result;

        // Check runtime ExtraAttributes first
        Object extraValue = item.getExtraAttribute(key);
        if (extraValue instanceof String) {
            String str = ((String) extraValue).trim();
            if (!str.isEmpty()) result.add(str);
            
        } else if (extraValue instanceof java.util.Collection<?>) {
            for (Object val : (java.util.Collection<?>) extraValue) {
                if (val != null && !val.toString().trim().isEmpty()) {
                    result.add(val.toString().trim());
                }
            }
            
        } else if (extraValue instanceof Object[]) {
            for (Object val : (Object[]) extraValue) {
                if (val != null && !val.toString().trim().isEmpty()) {
                    result.add(val.toString().trim());
                }
            }
        }

        if (!result.isEmpty()) {
            return result; // Found via ExtraAttributes
        }

        // Fallback to Lucene stored Metadata
        if (item.getMetadata() != null) {
            String[] values = item.getMetadata().getValues(key);
            if (values != null && values.length > 0) {
                for (String val : values) {
                    if (val != null && !val.trim().isEmpty()) {
                        result.add(val.trim());
                    }
                }
            } else {
                String single = item.getMetadata().get(key);
                if (single != null && !single.trim().isEmpty()) {
                    result.add(single.trim());
                }
            }
        }
        
        return result;
    }
}
