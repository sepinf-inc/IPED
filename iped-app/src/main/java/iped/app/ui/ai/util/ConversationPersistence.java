package iped.app.ui.ai.util;

import iped.engine.data.IPEDMultiSource;
import iped.app.ui.App;
import iped.app.ui.ai.model.Conversation;
import iped.app.ui.ai.model.StandardConversation;
import iped.app.ui.ai.model.AgentConversation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles saving and loading AI Chat conversations to/from the local IPED case directory.
 * Ensures strict forensic isolation so chats do not bleed between different cases.
 */
public class ConversationPersistence {

    // Subfolder inside the case's iped/data directory
    private static final String CHATS_DIR_NAME = "iped/data/ai_chats";
    
    // Pretty printing makes the JSON human-readable
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Resolves the case-isolated directory for storing AI chats
     * Creates the directory if it does not exist
     * Safely handles IPEDMultiSource (Multi-case mode) by falling back to the first available case
     */
    private static File getStorageDirectory() {
        try {

            if (App.get() == null || App.get().appCase == null) {
                return null; // Failsafe if accessed outside of an open case
            }
            
            File caseDir = App.get().appCase.getCaseDir();
            
            // Handle the Multi-Case edge case where the global case directory might be null
            if (caseDir == null && App.get().appCase instanceof IPEDMultiSource) {
                IPEDMultiSource multiSource = (IPEDMultiSource) App.get().appCase;
                if (!multiSource.getAtomicSources().isEmpty()) {
                    // Fallback: Save the multi-case chats into the first case's directory
                    caseDir = multiSource.getAtomicSources().get(0).getCaseDir();
                }
            }
            
            // Absolute failsafe if the directory is still somehow unresolvable
            if (caseDir == null) {
                caseDir = new File(System.getProperty("user.home"), ".iped_ai_chats");
            }

            File chatsDir = new File(caseDir, CHATS_DIR_NAME);
            
            if (!chatsDir.exists()) {
                chatsDir.mkdirs();
            }
            
            return chatsDir;
        } catch (Exception e) {
            System.err.println("Safe fallback: Could not resolve AI storage dir - " + e.getMessage());
            return null;
        }
    }

    /**
     * Serializes a Conversation object to a distinct JSON file
     */
    public static void saveConversation(Conversation conversation) {
        File dir = getStorageDirectory();
        if (dir == null || conversation == null) return;

        String prefix = conversation.isAgentConversation() ? "agent_" : "chat_";
        File chatFile = new File(dir, prefix + conversation.getId() + ".json");
        
        try (FileWriter writer = new FileWriter(chatFile)) {
            GSON.toJson(conversation, writer);
        } catch (IOException e) {
            System.err.println("Failed to save AI conversation: " + e.getMessage());
        }
    }

    /**
     * Reads all JSON files in the case's chat directory and returns them sorted by newest first.
     */
    public static List<Conversation> loadAllConversations() {
        List<Conversation> conversations = new ArrayList<>();
        File dir = getStorageDirectory();
        
        if (dir == null || !dir.exists()) return conversations;

        File[] files = dir.listFiles((d, name) -> 
            (name.startsWith("chat_") || name.startsWith("agent_")) && name.endsWith(".json")
        );
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    Class<? extends Conversation> targetClass = file.getName().startsWith("agent_")
                            ? AgentConversation.class
                            : StandardConversation.class;
                    Conversation conv = GSON.fromJson(reader, targetClass);
                    if (conv != null) {
                        if (!"deleted".equals(conv.getStatus())) {
                            conversations.add(conv);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load AI conversation file " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        // Sort by last modified, newest first, so the sidebar is ordered correctly
        conversations.sort(Comparator.comparingLong(Conversation::getLastModified).reversed());
        return conversations;
    }

    /**
     * Flags the conversation as deleted by removing, but does not permanently delete the file
     */
    public static void deleteConversation(Conversation conversation) {
        if (conversation == null) return;
        conversation.setStatus("deleted");
        saveConversation(conversation);
    }
}