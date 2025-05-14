import java.io.*;
import java.util.*;

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

        List<String> command = buildCommand(cliDir, imagePath, outputDir);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cliDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        printProcessOutput(process.getInputStream());

        int exit = process.waitFor();
        if (exit != 0) {
            throw new RuntimeException("IPED CLI exited with code " + exit);
        }
    }

    private static List<String> buildCommand(File cliDir, String imagePath, String outputDir) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        boolean isWindows = osName.contains("win");

        List<String> cmd = new ArrayList<>();
        if (isWindows) {
            File exeFile = new File(cliDir, CLI_EXE);
            cmd.add(exeFile.getAbsolutePath());
        } else {
            File jarFile = new File(cliDir, CLI_JAR);
            if (!jarFile.exists()) {
                throw new IllegalStateException("IPED JAR not found: " + jarFile.getAbsolutePath());
            }
            cmd.addAll(List.of("java", "-jar", jarFile.getAbsolutePath()));
        }

        cmd.addAll(List.of("-d", imagePath, "-o", outputDir));
        return cmd;
    }

    private static void printProcessOutput(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}
