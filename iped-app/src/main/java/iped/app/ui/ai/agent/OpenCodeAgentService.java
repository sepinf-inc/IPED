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

    public void askAgentQuestion(String question, Consumer<String> uiCallback, Consumer<String> onSessionIdFound, Runnable onComplete, Consumer<String> onError) {
        new Thread(() -> {
            try {
                uiCallback.accept("**[Agent]:** Executando opencode...\n\n");

                // 1. Localizar o diretório do mcp-server dinamicamente
                File mcpServerDir = findMcpServerDir();

                // 2. Resolver caminho do executável do opencode
                String opencodeCmd = resolveOpencodeCommand();

                // 3. Construir o comando
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

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(mcpServerDir);

                // Configurar variável de ambiente OPENCODE_CONFIG caso o opencode.json exista
                File configFile = new File(mcpServerDir, "opencode.json");
                if (configFile.exists()) {
                    pb.environment().put("OPENCODE_CONFIG", configFile.getAbsolutePath());
                }

                pb.redirectErrorStream(true);
                Process process = pb.start();

                // Fechar stdin do subprocesso para evitar que ele trave esperando entrada
                process.getOutputStream().close();

                // 4. Ler saída em tempo real, extrair session ID e converter JSON para texto
                StringBuilder fullResponse = new StringBuilder();
                StringBuilder buffer = new StringBuilder();
                boolean sessionIdExtracted = false;
                
                try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] charBuffer = new char[1024];
                    int charsRead;
                    while ((charsRead = reader.read(charBuffer)) != -1) {
                        String chunk = new String(charBuffer, 0, charsRead);
                        buffer.append(chunk);
                        
                        // Tentar extrair session ID do buffer acumulado
                        if (!sessionIdExtracted) {
                            String bufferContent = buffer.toString();
                            Matcher matcher = SESSION_ID_PATTERN.matcher(bufferContent);
                            if (matcher.find()) {
                                String sessionId = matcher.group(1);
                                onSessionIdFound.accept(sessionId);
                                sessionIdExtracted = true;
                            }
                        }
                        
                        // Processar JSON Lines e converter para texto
                        String textContent = extractTextFromJsonLines(buffer.toString());
                        if (!textContent.isEmpty()) {
                            uiCallback.accept(textContent);
                            fullResponse.append(textContent);
                        }
                        
                        // Limpar buffer após processamento bem-sucedido
                        buffer.setLength(0);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    String errMsg = "O processo opencode terminou com código de erro: " + exitCode;
                    uiCallback.accept("\n\n**[Erro do Agent]:** " + errMsg + "\n");
                    onError.accept(errMsg);
                } else {
                    // Salvar no histórico local da conversa
                    chatHistory.add(new AIStreamChatRequest.AIMessage("user", question));
                    chatHistory.add(new AIStreamChatRequest.AIMessage("assistant", fullResponse.toString()));
                }

            } catch (Exception e) {
                String errMsg = "Erro no Agent: " + e.getMessage();
                uiCallback.accept("\n\n**[Erro do Agent]:** " + errMsg + "\n");
                onError.accept(errMsg);
            } finally {
                onComplete.run();
            }
        }).start();
    }

    /**
     * Extrai conteúdo legível do formato JSON Lines do opencode.
     * Mantém o mesmo formato visual: model info, tool calls, e texto.
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
                    // step_start não produz texto visível diretamente
                    // O modelo aparece no texto normal
                } else if ("text".equals(type)) {
                    // Extrair conteúdo de texto
                    String text = extractTextContent(json);
                    if (text != null && !text.isEmpty()) {
                        output.append(text);
                    }
                } else if ("tool_use".equals(type)) {
                    // Extrair tool calls mantendo o formato original
                    String toolInfo = extractToolInfo(json);
                    if (toolInfo != null && !toolInfo.isEmpty()) {
                        output.append(toolInfo).append("\n");
                    }
                } else if ("step_finish".equals(type)) {
                    // Ignorar step_finish
                } else {
                    // Para outros tipos, tentar extrair texto diretamente
                    String text = extractTextContent(json);
                    if (text != null && !text.isEmpty()) {
                        output.append(text);
                    }
                }
            } catch (Exception e) {
                // Se falhar na parsing, ignora esta linha
                // Isso pode acontecer com chunks parciais
            }
        }
        
        return output.toString();
    }

    /**
     * Extrai o conteúdo de texto do JSON, lidando com campos aninhados.
     */
    private String extractTextContent(JsonObject json) {
        // Tentar extrair de "text" field diretamente
        if (json.has("text")) {
            return unescapeJsonString(json.get("text").getAsString());
        }
        
        // Tentar extrair de "part" -> "text"
        if (json.has("part")) {
            JsonObject part = json.getAsJsonObject("part");
            if (part.has("text")) {
                return unescapeJsonString(part.get("text").getAsString());
            }
        }
        
        // Tentar extrair de "parts" array
        if (json.has("parts")) {
            var parts = json.getAsJsonArray("parts");
            for (var i = 0; i < parts.size(); i++) {
                JsonObject part = parts.get(i).getAsJsonObject();
                if (part.has("text")) {
                    return unescapeJsonString(part.get("text").getAsString());
                }
            }
        }
        
        // Tentar extrair de "content" field
        if (json.has("content")) {
            return unescapeJsonString(json.get("content").getAsString());
        }
        
        // Tentar extrair de "part" -> "content"
        if (json.has("part")) {
            JsonObject part = json.getAsJsonObject("part");
            if (part.has("content")) {
                return unescapeJsonString(part.get("content").getAsString());
            }
        }
        
        return null;
    }

    /**
     * Extrai informações de tool calls do JSON.
     * Formato: "? toolName {arguments}"
     */
    private String extractToolInfo(JsonObject json) {
        // Extrair do "part" object
        if (!json.has("part")) {
            return null;
        }
        
        JsonObject part = json.getAsJsonObject("part");
        String toolName = null;
        String arguments = null;
        
        // Tentar extrair tool name de diferentes campos
        if (part.has("tool")) {
            toolName = part.get("tool").getAsString();
        } else if (part.has("toolName")) {
            toolName = part.get("toolName").getAsString();
        }
        
        // Tentar extrair arguments
        if (part.has("state")) {
            JsonObject state = part.getAsJsonObject("state");
            if (state.has("input")) {
                arguments = state.get("input").toString();
            }
        }
        
        if (toolName == null) {
            return null;
        }
        
        // Formatar output no estilo original
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
     * Remove escaping de strings JSON.
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
            // A configuração pode não estar ativa/inicializada em testes ou ambientes específicos
        }

        if (appRoot != null) {
            // 1. Caminho de produção/instalação empacotada
            File prodPath = new File(appRoot, "scripts/mcp/iped-mcp-server");
            if (prodPath.exists() && prodPath.isDirectory()) {
                return prodPath;
            }

            // 2. Caminho de desenvolvimento
            File devPath = new File(appRoot, "resources/scripts/mcp/iped-mcp-server");
            if (devPath.exists() && devPath.isDirectory()) {
                return devPath;
            }
        }

        // 3. Fallback para execução direta sem appRoot
        File fallbackPath = new File("iped-app/resources/scripts/mcp/iped-mcp-server");
        if (fallbackPath.exists() && fallbackPath.isDirectory()) {
            return fallbackPath;
        }

        return new File(".");
    }

    private String resolveOpencodeCommand() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            // Tenta localizar no diretório AppData npm padrão do Windows
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                File npmOpencode = new File(appData, "npm/opencode.cmd");
                if (npmOpencode.exists()) {
                    return npmOpencode.getAbsolutePath();
                }
            }
            
            // Backup em USERPROFILE caso APPDATA falhe
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null) {
                File npmOpencode = new File(userProfile, "AppData/Roaming/npm/opencode.cmd");
                if (npmOpencode.exists()) {
                    return npmOpencode.getAbsolutePath();
                }
            }
        }
        
        // Fallback para comando padrão global resolvido pelo OS
        return "opencode";
    }

    public void clearHistory() {
        this.chatHistory.clear();
    }

    public List<AIStreamChatRequest.AIMessage> getChatHistory() {
        return chatHistory;
    }
}
