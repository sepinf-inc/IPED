package iped.app.ui.ai;

import iped.app.ui.ai.backend.AIBackendService;
import iped.app.ui.ai.backend.AIInitMultiChatRequest;
import iped.app.ui.ai.backend.AIStreamChatRequest;
import iped.app.ui.ai.backend.AIInitMultiChatFullRequest;
import iped.app.ui.ai.util.AIWhatsappChatExtractor;
import iped.app.ui.ai.util.AIPayloadFactory;
import iped.app.ui.ai.util.ConversationPersistence;
import iped.app.ui.ai.model.ContextFileEntry;
import iped.app.ui.ai.model.AIChatMessage;
import iped.app.ui.ai.model.Conversation;
import iped.app.ui.ai.context.AIContextManager;
import iped.app.ui.ai.context.ConversationManager;
import iped.data.IItem;
import iped.properties.ExtraProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.Collection;

/**
 * The central orchestrator that coordinates the flow of data between the UI, 
 * the file extraction utilities, and the AI backend service.
 * <p>
 * This class isolates the complexity of threading, session caching, and error handling 
 * from the presentation layer. 
 * </p>
 */
public class AIChatCoordinator {

    private final AIBackendService backendService;
    private final AIWhatsappChatExtractor extractor;
    
    // Track lists to support both single and multi-chat
    private List<String> currentChatHashes = new ArrayList<>();
    private List<Integer> currentContextItemIds = new ArrayList<>();

    private final List<AIStreamChatRequest.AIMessage> chatHistory = new ArrayList<>();

    /**
     * Constructs a new coordinator.
     * * @param backendService The backend client injected 
     * for handling actual AI communication.
     */
    public AIChatCoordinator(AIBackendService backendService) {
        this.backendService = backendService;
        this.extractor = new AIWhatsappChatExtractor();
    }

    /**
     * Executes the complete AI request pipeline: validates the selected files,
     * initializes or reuses the chat context (if unchanged), and streams the AI's response.
     * @param question   The text prompt from the user
     * @param uiCallback A callback used to push status updates and streamed text back to the UI
     * @param onComplete A callback triggered when the entire process finishes (success or fail),
     * typically used to re-enable UI buttons
     * @param onError    A callback used to push error messages back to the UI
     */
    public void askQuestion(String question, Consumer<String> uiCallback, Runnable onComplete, Consumer<String> onError) {
        
        // Fetch only valid entries from the Context Manager
        List<ContextFileEntry> validEntries = AIContextManager.getInstance().getContextEntriesForUI()
                .stream()
                .filter(ContextFileEntry::isValidForContext)
                .collect(Collectors.toList());
        
        // Calculate the total chunks across all selected chats
        final int totalChunks = calculateTotalChunks(validEntries);

        // If a question is typed before the background thread finishes restoring the UI, 
        // this safely blocks the user for that fraction of a second.
        if (validEntries.isEmpty()) {
            onError.accept("Please, add at least one valid file to context before asking.");
            return;
        }

        // Check if the context changed since the last question
        List<Integer> newContextIds = validEntries.stream()
                .map(e -> e.getItem().getId())
                .collect(Collectors.toList());

        // Since the UI was restored, check if context changed OR if backend hashes are lacking
        boolean contextChanged = !newContextIds.equals(currentContextItemIds);
        boolean needsInitialization = contextChanged || currentChatHashes.isEmpty();

        // Offload heavy lifting to a background thread
        new Thread(() -> {

            // Flag used to discern whether the hashes and context ids need clearing
            boolean initializationCompleted = !needsInitialization;

            try {
                // Step A: Initialize the Chat
                if (needsInitialization) {
                    uiCallback.accept("**[System]:** Initializing context...\n\n");
                    chatHistory.clear();
                    currentChatHashes.clear();
                    
                    if (validEntries.size() == 1) {
                        // Single chat
                        IItem item = validEntries.get(0).getItem();
                        String html = extractor.extractHtml(item);
                        String hash = backendService.initChat(html);
                        currentChatHashes.add(hash);
                    } else if (totalChunks <= 10) {
                        // Multi chat full (raw HTML - fewer than 11 chunks)
                        AIInitMultiChatFullRequest request = AIPayloadFactory.buildMultiChatFullRequest(validEntries);
                        List<String> hashes = backendService.initMultiChatFull(request);
                        currentChatHashes.addAll(hashes);
                    } else {
                        // Normal Multi chat (summarized, 11 or more chunks)
                        AIInitMultiChatRequest request = AIPayloadFactory.buildMultiChatRequest(validEntries);
                        List<String> hashes = backendService.initMultiChat(request);
                        currentChatHashes.addAll(hashes);
                    }
                    
                    // Update cache state
                    currentContextItemIds = newContextIds; 

                    // Save the backend state to the Conversation Manager so it persists
                    Conversation activeConv = ConversationManager.getInstance().getActiveConversation();
                    if (activeConv != null) {
                        activeConv.setContextIds(new ArrayList<>(currentContextItemIds));
                        activeConv.setChatHashes(new ArrayList<>(currentChatHashes));
                        activeConv.updateLastModified();

                        // Save the hydrated object to disk
                        ConversationPersistence.saveConversation(activeConv);
                    }

                    // Update the flag
                    initializationCompleted = true;
                }

                // Step B: Stream the response
                StringBuilder fullResponse = new StringBuilder(); 
                
                // Route to the correct streaming endpoint
                if (currentChatHashes.size() == 1) {
                    // Single chat stream
                    backendService.streamChatResponse(currentChatHashes.get(0), question, chatHistory, token -> {
                        uiCallback.accept(token);
                        fullResponse.append(token);
                    });
                } else if (currentChatHashes.size() > 1) {
                    // Multi chat stream
                    // Check chunk size to route to the correct endpoint
                    if (totalChunks <= 10) {
                        // Multi chat full stream
                        backendService.streamMultiChatFullResponse(currentChatHashes, question, chatHistory, token -> {
                            uiCallback.accept(token);
                            fullResponse.append(token);
                        });
                    } else {
                        // Multi chat summarized stream
                        backendService.streamMultiChatResponse(currentChatHashes, question, chatHistory, token -> {
                            uiCallback.accept(token);
                            fullResponse.append(token);
                        });
                    }
                } else {
                    throw new IllegalStateException("Cannot stream response: No active chat hashes found.");
                }

                uiCallback.accept("\n\n");

                // Step C: Save the turn to history
                chatHistory.add(new AIStreamChatRequest.AIMessage("user", question));
                chatHistory.add(new AIStreamChatRequest.AIMessage("assistant", fullResponse.toString()));

            } catch (Exception e) {
                String errorMessage = e.getMessage(); 
                onError.accept("Backend error: " + e.getMessage());

                // If the backend restarted and its cache was wiped, it will throw a "not found" error.
                // Clear the hashes to force the next attempt to re-upload the HTM
                if (errorMessage != null && (errorMessage.toLowerCase().contains("nao encontrado") || errorMessage.toLowerCase().contains("não encontrado"))) {
                    currentChatHashes.clear();
                }
                
                // Only invalidate cache if initialization itself failed
                // If error happened during streaming, preserve the hashes so the user can retry
                if (!initializationCompleted) {
                    currentContextItemIds.clear();
                    currentChatHashes.clear();
                }
            } finally {
                onComplete.run(); 
            }
        }).start();
    }


    public void clearHistory() {
        this.chatHistory.clear();

        // Also clear chat currentChatHashes and currentContextItemIds
        currentChatHashes.clear();
        currentContextItemIds.clear();
    }

    /**
     * Hydrates the coordinator's memory with historical state, allowing the LLM 
     * to resume a previous conversation seamlessly without re-uploading files.
     */
    public void loadHistoricalContext(List<String> hashes, List<Integer> itemIds, List<AIChatMessage> uiMessages) {
        // Restore backend session hashes
        this.currentChatHashes.clear();
        if (hashes != null) this.currentChatHashes.addAll(hashes);
        
        // Restore IPED context IDs
        this.currentContextItemIds.clear();
        if (itemIds != null) this.currentContextItemIds.addAll(itemIds);
        
        // Restore the LLM conversation memory
        this.chatHistory.clear();
        if (uiMessages != null) {
            for (AIChatMessage msg : uiMessages) {
                // The backend only wants to remember user and assistant turns.
                // We do NOT send "system" or "error" messages back to the LLM.
                if ("user".equals(msg.getType()) || "assistant".equals(msg.getType())) {
                    this.chatHistory.add(new AIStreamChatRequest.AIMessage(msg.getType(), msg.getContent()));
                }
            }
        }
    }

    /**
     * Calculates the total number of chunks across a list of chat files.
     */
    public static int calculateTotalChunks(List<ContextFileEntry> entries) {
        int total = 0;
        for (ContextFileEntry entry : entries) {
            IItem item = entry.getItem();
            if (item == null) continue;
            
            int count = 0;
            
            // Try ExtraAttributes (Runtime memory)
            Object extraValue = item.getExtraAttribute(ExtraProperties.CHUNK_IDS);
            if (extraValue instanceof java.util.Collection<?>) {
                count = ((Collection<?>) extraValue).size();
            } else if (extraValue instanceof Object[]) {
                count = ((Object[]) extraValue).length;
            } else if (extraValue instanceof String) {
                String str = ((String) extraValue).trim();
                if (!str.isEmpty()) {
                    count = str.split(",").length;
                }
            } 
            // Try Lucene Metadata (Disk)
            else if (item.getMetadata() != null) {
                String[] values = item.getMetadata().getValues(ExtraProperties.CHUNK_IDS);
                if (values != null && values.length > 0) {
                    count = values.length;
                } else {
                    String single = item.getMetadata().get(ExtraProperties.CHUNK_IDS);
                    if (single != null && !single.trim().isEmpty()) {
                        count = single.split(",").length;
                    }
                }
            }
            
            total += count;
        }
        return total;
    }
}