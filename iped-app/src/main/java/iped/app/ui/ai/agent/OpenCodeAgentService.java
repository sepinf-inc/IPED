package iped.app.ui.ai.agent;

import iped.app.ui.ai.backend.AIStreamChatRequest;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service to interact with the local OpenCode agent CLI.
 */
public class OpenCodeAgentService {

    private final List<AIStreamChatRequest.AIMessage> chatHistory = new ArrayList<>();

    public void askAgentQuestion(String question, Consumer<String> uiCallback, Runnable onComplete, Consumer<String> onError) {
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

                // 4. Ler saída em tempo real em blocos de caracteres para suportar streaming
                StringBuilder fullResponse = new StringBuilder();
                try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] buffer = new char[1024];
                    int charsRead;
                    while ((charsRead = reader.read(buffer)) != -1) {
                        String chunk = new String(buffer, 0, charsRead);
                        uiCallback.accept(chunk);
                        fullResponse.append(chunk);
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

            // 2. Caminho de desenvolvimento executado a partir da raiz do repositório
            File devPath = new File(appRoot, "iped-app/resources/scripts/mcp/iped-mcp-server");
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
