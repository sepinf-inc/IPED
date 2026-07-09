package iped.app.ui.ai.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import iped.app.ui.ai.backend.AIStreamChatRequest;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to interact with the local OpenCode agent CLI.
 */
public class OpenCodeAgentService {

    private final List<AIStreamChatRequest.AIMessage> chatHistory = new ArrayList<>();
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\"sessionID\":\"(ses_[^\"]+)\"");

    public void askAgentQuestion(String question, Consumer<String> uiCallback, Consumer<String> onSessionIdFound, String sessionId, Runnable onComplete, Consumer<String> onError) {
        new Thread(() -> {
            try {
                uiCallback.accept("**[Agent]:** Executando opencode...\n\n");

                // 1. Locate the mcp-server directory dynamically
                File mcpServerDir = findMcpServerDir();

                // 2. Resolve the opencode executable path
                String opencodeCmd = resolveOpencodeCommand();

                // 3. Build the command
                List<String> command = new ArrayList<>();
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                if (isWindows && !opencodeCmd.endsWith(".cmd") && !opencodeCmd.endsWith(".bat") && !opencodeCmd.endsWith(".exe")) {
                    command.add("cmd.exe");
                    command.add("/c");
                }
                command.add(opencodeCmd);
                command.add("run");
                command.add(question);
                command.add("--auto");
                command.add("--format");
                command.add("json");
                
                // Add session ID if available to maintain context
                if (sessionId != null && !sessionId.isEmpty()) {
                    command.add("--session");
                    command.add(sessionId);
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(mcpServerDir);

                // Set OPENCODE_CONFIG environment variable if opencode.json exists
                File configFile = new File(mcpServerDir, "opencode.json");
                if (configFile.exists()) {
                    pb.environment().put("OPENCODE_CONFIG", configFile.getAbsolutePath());
                }

                pb.redirectErrorStream(true);
                Process process = pb.start();

                // Close subprocess stdin to prevent it from blocking waiting for input
                process.getOutputStream().close();

                // 4. Read output in real-time, extract session ID and convert JSON to text
                StringBuilder fullResponse = new StringBuilder();
                StringBuilder buffer = new StringBuilder();
                boolean sessionIdExtracted = false;
                
                try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] charBuffer = new char[1024];
                    int charsRead;
                    while ((charsRead = reader.read(charBuffer)) != -1) {
                        String chunk = new String(charBuffer, 0, charsRead);
                        buffer.append(chunk);
                        
                        // Try to extract session ID from accumulated buffer
                        if (!sessionIdExtracted) {
                            String bufferContent = buffer.toString();
                            Matcher matcher = SESSION_ID_PATTERN.matcher(bufferContent);
                            if (matcher.find()) {
                                String extractedSessionId = matcher.group(1);
                                onSessionIdFound.accept(extractedSessionId);
                                sessionIdExtracted = true;
                            }
                        }
                        
                        // Process JSON Lines and convert to text
                        String textContent = extractTextFromJsonLines(buffer.toString());
                        if (!textContent.isEmpty()) {
                            uiCallback.accept(textContent);
                            fullResponse.append(textContent);
                        }
                        
                        // Clear buffer after successful processing
                        buffer.setLength(0);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    String errMsg = "The opencode process terminated with error code: " + exitCode;
                    uiCallback.accept("\n\n**[Agent Error]:** " + errMsg + "\n");
                    onError.accept(errMsg);
                } else {
                    // Save to local conversation history
                    chatHistory.add(new AIStreamChatRequest.AIMessage("user", question));
                    chatHistory.add(new AIStreamChatRequest.AIMessage("assistant", fullResponse.toString()));
                }

            } catch (Exception e) {
                String errMsg = "Agent Error: " + e.getMessage();
                uiCallback.accept("\n\n**[Agent Error]:** " + errMsg + "\n");
                onError.accept(errMsg);
            } finally {
                onComplete.run();
            }
        }).start();
    }

    /**
     * Extracts readable content from the OpenCode JSON Lines format.
     * Preserves the same visual format: model info, tool calls, and text.
     */
    private String extractTextFromJsonLines(String jsonLines) {
        StringBuilder output = new StringBuilder();
        String[] lines = jsonLines.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            try {
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : null;
                
                if ("step_start".equals(type)) {
                    // step_start does not produce visible text directly
                    // The model appears in the regular text
                } else if ("text".equals(type)) {
                    // Extract text's content
                    String text = extractTextContent(json);
                    if (text != null && !text.isEmpty()) {
                        output.append(text);
                    }
                } else if ("tool_use".equals(type)) {
                    // Extract tool calls while preserving the original format
                    String toolInfo = extractToolInfo(json);
                    if (toolInfo != null && !toolInfo.isEmpty()) {
                        output.append(toolInfo).append("\n");
                    }
                } else if ("step_finish".equals(type)) {
                    // Ignore step_finish
                } else {
                    // For other types, try to extract the text directly
                    String text = extractTextContent(json);
                    if (text != null && !text.isEmpty()) {
                        output.append(text);
                    }
                }
            } catch (Exception e) {
                // If parsing fails, ignore this line
                // This can happen with partial chunks
            }
        }
        
        return output.toString();
    }

    /**
     * Extracts text content from the JSON, handling nested fields.
     */
    private String extractTextContent(JsonObject json) {
        // Try to extract directly from the "text" field
        if (json.has("text")) {
            return unescapeJsonString(json.get("text").getAsString());
        }
        
        // Try to extract from "part" -> "text"
        if (json.has("part")) {
            JsonObject part = json.getAsJsonObject("part");
            if (part.has("text")) {
                return unescapeJsonString(part.get("text").getAsString());
            }
        }
        
        // Try to extract from the "parts" array
        if (json.has("parts")) {
            var parts = json.getAsJsonArray("parts");
            for (var i = 0; i < parts.size(); i++) {
                JsonObject part = parts.get(i).getAsJsonObject();
                if (part.has("text")) {
                    return unescapeJsonString(part.get("text").getAsString());
                }
            }
        }
        
        // Try to extract from the "content" field
        if (json.has("content")) {
            return unescapeJsonString(json.get("content").getAsString());
        }
        
        // Try to extract from "part" -> "content"
        if (json.has("part")) {
            JsonObject part = json.getAsJsonObject("part");
            if (part.has("content")) {
                return unescapeJsonString(part.get("content").getAsString());
            }
        }
        
        return null;
    }

    /**
     * Extracts tool call information from the JSON.
     * Format: "? toolName {arguments}"
     */
    private String extractToolInfo(JsonObject json) {
        // Extract from the "part" object
        if (!json.has("part")) {
            return null;
        }
        
        JsonObject part = json.getAsJsonObject("part");
        String toolName = null;
        String arguments = null;
        
        // Try to extract the tool name from different fields
        if (part.has("tool")) {
            toolName = part.get("tool").getAsString();
        } else if (part.has("toolName")) {
            toolName = part.get("toolName").getAsString();
        }
        
        // Try to extract the arguments
        if (part.has("state")) {
            JsonObject state = part.getAsJsonObject("state");
            if (state.has("input")) {
                arguments = state.get("input").toString();
            }
        }
        
        if (toolName == null) {
            return null;
        }
        
        // Format the output in the original style
        StringBuilder result = new StringBuilder();
        result.append("? ").append(toolName);
        
        if (arguments != null) {
            // Format arguments nicely - remove outer quotes if JSON string
            String cleanArgs = arguments;
            if (cleanArgs.startsWith("\"") && cleanArgs.endsWith("\"")) {
                cleanArgs = cleanArgs.substring(1, cleanArgs.length() - 1);
            }
            result.append(" ").append(cleanArgs);
        }
        
        return result.toString();
    }

    /**
     * Removes JSON string escaping.
     */
    private String unescapeJsonString(String text) {
        if (text == null) return "";
        return text.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private File findMcpServerDir() {
        String appRoot = null;
        try {
            appRoot = iped.engine.config.Configuration.getInstance().appRoot;
        } catch (Throwable t) {
            // Configuration may not be active/initialized in tests or specific environments
        }

        if (appRoot != null) {
            // 1. Production/packaged installation path
            File prodPath = new File(appRoot, "scripts/mcp/iped-mcp-server");
            if (prodPath.exists() && prodPath.isDirectory()) {
                return prodPath;
            }

            // 2. Development path
            File devPath = new File(appRoot, "resources/scripts/mcp/iped-mcp-server");
            if (devPath.exists() && devPath.isDirectory()) {
                return devPath;
            }
        }

        // 3. Fallback for direct execution without appRoot
        File fallbackPath = new File("iped-app/resources/scripts/mcp/iped-mcp-server");
        if (fallbackPath.exists() && fallbackPath.isDirectory()) {
            return fallbackPath;
        }

        return new File(".");
    }

    private String resolveOpencodeCommand() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            // Try to locate the standard Windows AppData npm directory
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                File npmOpencode = new File(appData, "npm/opencode.cmd");
                if (npmOpencode.exists()) {
                    return npmOpencode.getAbsolutePath();
                }
            }
            
            // Fallback to USERPROFILE if APPDATA is unavailable
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null) {
                File npmOpencode = new File(userProfile, "AppData/Roaming/npm/opencode.cmd");
                if (npmOpencode.exists()) {
                    return npmOpencode.getAbsolutePath();
                }
            }
        }
        
        // Fallback to the default global command resolved by the OS
        return "opencode";
    }

    public void clearHistory() {
        this.chatHistory.clear();
    }

    public List<AIStreamChatRequest.AIMessage> getChatHistory() {
        return chatHistory;
    }
}