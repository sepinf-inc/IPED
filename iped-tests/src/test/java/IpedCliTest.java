import org.junit.Test;
import org.junit.BeforeClass;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Teste simples para verificar se o IPED CLI está funcionando
 */
public class IpedCliTest {

    private static final String CLI_DIR = "../target/release/iped-4.2.0";
    private static final String CLI_EXE = "iped.exe";
    private static final String CLI_JAR = "iped.jar";

    @BeforeClass
    public static void setup() {
        // Verifica se o diretório do CLI existe
        File cliDir = new File(CLI_DIR);
        assertTrue("IPED CLI directory should exist", cliDir.exists());
        assertTrue("IPED CLI directory should be a directory", cliDir.isDirectory());
    }

    @Test
    public void testCliFilesExist() {
        System.out.println("=== CLI FILES ===");

        File cliDir = new File(CLI_DIR);

        // Verifica se o executável existe no Windows
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            File exeFile = new File(cliDir, CLI_EXE);
            assertTrue("IPED EXE should exist on Windows", exeFile.exists());
            System.out.println("✓ IPED executable found");
        } else {
            File jarFile = new File(cliDir, CLI_JAR);
            assertTrue("IPED JAR should exist on Linux/Mac", jarFile.exists());
            System.out.println("✓ IPED JAR encontrado: " + jarFile.getAbsolutePath());
        }

        // Verifica outros arquivos importantes
        File configFile = new File(cliDir, "IPEDConfig.txt");
        assertTrue("IPEDConfig.txt should exist", configFile.exists());
        System.out.println("✓ IPEDConfig.txt found");

        File libDir = new File(cliDir, "lib");
        assertTrue("lib directory should exist", libDir.exists());
        System.out.println("✓ lib directory found");

        System.out.println("✓ All CLI files present");
    }

    @Test
    public void testCliHelp() throws IOException, InterruptedException {
        System.out.println("=== HELP COMMAND ===");

        File cliDir = new File(CLI_DIR);
        String osName = System.getProperty("os.name").toLowerCase();

        List<String> command = new ArrayList<>();
        if (osName.contains("win")) {
            File exeFile = new File(cliDir, CLI_EXE);
            command.add(exeFile.getAbsolutePath());
        } else {
            File jarFile = new File(cliDir, CLI_JAR);
            command.addAll(List.of("java", "-jar", jarFile.getAbsolutePath()));
        }

        // Adiciona parâmetro de ajuda
        command.add("-h");

        System.out.println("Comando: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cliDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Captura output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exit = process.waitFor();
        System.out.println("Exit code: " + exit);
        System.out.println("Output:");
        System.out.println(output.toString());

        // Verifica se o comando de ajuda funcionou
        assertTrue("Help command should work", exit == 0 || exit == 1);
        assertTrue("Help output should contain IPED information",
                  output.toString().toLowerCase().contains("iped"));

        System.out.println("✓ Help command working");
    }

    @Test
    public void testCliVersion() throws IOException, InterruptedException {
        System.out.println("=== VERSION COMMAND ===");

        File cliDir = new File(CLI_DIR);
        String osName = System.getProperty("os.name").toLowerCase();

        List<String> command = new ArrayList<>();
        if (osName.contains("win")) {
            File exeFile = new File(cliDir, CLI_EXE);
            command.add(exeFile.getAbsolutePath());
        } else {
            File jarFile = new File(cliDir, CLI_JAR);
            command.addAll(List.of("java", "-jar", jarFile.getAbsolutePath()));
        }

        // Adiciona parâmetro de versão
        command.add("-version");

        System.out.println("Comando: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cliDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Captura output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exit = process.waitFor();
        System.out.println("Exit code: " + exit);
        System.out.println("Output:");
        System.out.println(output.toString());

        // Verifica se o comando de versão funcionou
        assertTrue("Version command should work", exit == 0 || exit == 1);
        assertTrue("Version output should contain version information",
                  output.toString().toLowerCase().contains("version") ||
                  output.toString().toLowerCase().contains("4.2"));

        System.out.println("✓ Version command working");
    }
}
