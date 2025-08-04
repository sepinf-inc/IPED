import java.io.*;
import java.util.*;
import java.nio.file.*;

/**
 * Invokes the native IPED CLI located under iped-parent/target/release/iped-4.2.0
 */
public class IpedProcessor {
    private static final String CLI_DIR = "../target/release/iped-4.2.0";
    private static final String CLI_EXE = "iped.exe";
    private static final String CLI_JAR = "iped.jar";

    public static void process(String imagePath, String outputDir) throws IOException, InterruptedException {
        File cliDir = new File(CLI_DIR);
        if (!cliDir.isDirectory()) {
            throw new IllegalStateException("IPED CLI directory not found: " + cliDir.getAbsolutePath());
        }

        // Verifica se a imagem existe
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            throw new IllegalStateException("Image file not found: " + imagePath);
        }

        // Limpa o diretório de saída se existir
        File outputDirFile = new File(outputDir);
        if (outputDirFile.exists()) {
            System.out.println("Cleaning existing output directory: " + outputDir);
            deleteDirectory(outputDirFile);
        }

        // Cria o diretório de saída
        outputDirFile.mkdirs();

        List<String> command = buildCommand(cliDir, imagePath, outputDir);

        System.out.println("=== EXECUTING IPED CLI ===");
        System.out.println("Command: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cliDir);
        pb.redirectErrorStream(true);

        // Configura variáveis de ambiente
        Map<String, String> env = pb.environment();
        env.put("JAVA_HOME", System.getProperty("java.home"));
        env.put("PATH", System.getenv("PATH"));

        Process process = pb.start();

        // Captura output em tempo real
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[IPED] " + line);
                output.append(line).append("\n");
            }
        }

        int exit = process.waitFor();
                        System.out.println("=== IPED CLI FINISHED ===");
        System.out.println("Exit code: " + exit);

        if (exit != 0) {
                            System.err.println("=== IPED CLI ERROR ===");
            System.err.println("Output completo:");
            System.err.println(output.toString());
            throw new RuntimeException("IPED CLI exited with code " + exit + "\nOutput: " + output.toString());
        }

                        System.out.println("=== IPED CLI EXECUTED SUCCESSFULLY ===");
    }

    /**
     * Deleta um diretório e todo seu conteúdo recursivamente
     */
    private static void deleteDirectory(File dir) throws IOException {
        if (dir.exists()) {
            Files.walk(dir.toPath())
                .sorted((p1, p2) -> -p1.compareTo(p2)) // Ordem reversa para deletar arquivos antes dos diretórios
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Erro ao deletar: " + path + " - " + e.getMessage());
                    }
                });
        }
    }

    private static List<String> buildCommand(File cliDir, String imagePath, String outputDir) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        boolean isWindows = osName.contains("win");

        List<String> cmd = new ArrayList<>();

        if (isWindows) {
            File exeFile = new File(cliDir, CLI_EXE);
            if (!exeFile.exists()) {
                throw new IllegalStateException("IPED EXE not found: " + exeFile.getAbsolutePath());
            }
            cmd.add(exeFile.getAbsolutePath());
        } else {
            File jarFile = new File(cliDir, CLI_JAR);
            if (!jarFile.exists()) {
                throw new IllegalStateException("IPED JAR not found: " + jarFile.getAbsolutePath());
            }
            cmd.addAll(List.of("java", "-jar", jarFile.getAbsolutePath()));
        }

        // Adiciona parâmetros básicos
        cmd.addAll(List.of("-d", imagePath, "-o", outputDir));

        // Adiciona parâmetros para análise mais rápida (para testes)
        cmd.addAll(List.of("-profile", "triage"));

        return cmd;
    }
}
