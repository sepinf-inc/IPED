import java.io.File;
import java.io.IOException;

/**
 * Invokes the native IPED CLI located under iped-parent/target/release/iped-4.3.0-snapshot.
 */
public class IpedProcessor {
    private static final String CLI_DIR = "../target/release/iped-4.3.0-snapshot";
    private static final String CLI_EXE = "iped.exe";

    public static void process(String imagePath, String outputDir) throws IOException, InterruptedException {
        File cliDir = new File(CLI_DIR);
        if (!cliDir.exists() || !cliDir.isDirectory()) {
            throw new IllegalStateException("IPED CLI directory not found: " + cliDir.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(
            new File(cliDir, CLI_EXE).getAbsolutePath(),
            "-d", imagePath,
            "-o", outputDir
        );
        pb.directory(cliDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (var in = process.getInputStream();
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new RuntimeException("IPED CLI exited with code " + exit);
        }
    }
}
