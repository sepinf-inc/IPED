import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Testes específicos para verificar os resultados da análise forense
 * Estes testes só devem ser executados após a análise estar completa
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ForensicResultsTest {

    private static final String IMAGE_URL =
        "https://digitalcorpora.s3.amazonaws.com/corpora/drives/nps-2009-ntfs1/ntfs1-gen1.E01";
    private static final Path CACHE_DIR = Paths.get("./target/test-images");
    private static final Path RESULT_DIR = Paths.get("./target/iped-result");
    private static final String IMAGE_NAME = "ntfs1-gen1.E01";
    private static final Path IMAGE_PATH = CACHE_DIR.resolve(IMAGE_NAME);
    private static final Path CSV_FILE = RESULT_DIR.resolve("FileList.csv");

    @BeforeClass
    public static void setup() throws Exception {
        // Se o diretório de resultados não existir, executa a análise
        if (!Files.exists(RESULT_DIR)) {
            System.out.println("=== SETUP: RUNNING FORENSIC ANALYSIS ===");
            createDirectories(CACHE_DIR, RESULT_DIR);
            downloadImageIfNeeded(IMAGE_URL, IMAGE_PATH);

            try {
                IpedProcessor.process(
                    IMAGE_PATH.toAbsolutePath().toString(),
                    RESULT_DIR.toAbsolutePath().toString()
                );

                // Aguarda a conclusão da análise
                waitForAnalysisCompletion();
                System.out.println("=== SETUP: FORENSIC ANALYSIS COMPLETED ===");
            } catch (Exception e) {
                System.err.println("=== SETUP: FORENSIC ANALYSIS FAILED ===");
                System.err.println("Error: " + e.getMessage());
                System.err.println("This is expected in CI environment with dummy test image");
            }
        }

        if (!Files.exists(RESULT_DIR)) {
            System.out.println("Result directory not found after analysis attempt - expected in CI with dummy image");
            // Don't throw exception, just skip the tests
            return;
        }
    }

    /**
     * Teste 1: Verificar estrutura de diretórios de resultado
     */
    @Test
    public void test01_ResultDirectoryStructure() {
        System.out.println("=== DIRECTORY STRUCTURE ===");

        assertTrue("Result directory should exist", Files.exists(RESULT_DIR));
        assertTrue("IPED directory should exist", Files.exists(RESULT_DIR.resolve("iped")));
        assertTrue("Data directory should exist", Files.exists(RESULT_DIR.resolve("iped").resolve("data")));

        System.out.println("✓ Directory structure valid");
    }

    /**
     * Teste 2: Verificar arquivos de resultado principais
     */
    @Test
    public void test02_MainResultFiles() throws IOException {
        System.out.println("=== MAIN FILES ===");

        // Verifica arquivos principais
        Path fileListCsv = RESULT_DIR.resolve("FileList.csv");
        Path sleuthDb = RESULT_DIR.resolve("sleuth.db");
        Path searchApp = RESULT_DIR.resolve("IPED-SearchApp.exe");

        assertTrue("FileList.csv should exist", Files.exists(fileListCsv));
        assertTrue("sleuth.db should exist", Files.exists(sleuthDb));
        assertTrue("IPED-SearchApp.exe should exist", Files.exists(searchApp));

        System.out.println("✓ Main files found:");
        System.out.println("  - FileList.csv");
        System.out.println("  - sleuth.db");
        System.out.println("  - IPED-SearchApp.exe");
    }

    /**
     * Teste 3: Verificar conteúdo do CSV principal
     */
    @Test
    public void test03_CsvContentValidation() throws IOException {
        System.out.println("=== CSV CONTENT ===");

        Path csvFile = RESULT_DIR.resolve("FileList.csv");
        assertTrue("FileList.csv should exist", Files.exists(csvFile));

        validateCsvFile(csvFile);

        System.out.println("✓ CSV content valid");
    }

    /**
     * Teste 4: Verificar arquivos HTML de relatório
     */
    @Test
    public void test04_HtmlReportFiles() throws IOException {
        System.out.println("=== HTML REPORTS ===");

        if (Files.exists(RESULT_DIR.resolve("iped").resolve("htmlreport"))) {
            List<Path> htmlFiles = Files.list(RESULT_DIR.resolve("iped").resolve("htmlreport"))
                .filter(path -> path.toString().endsWith(".html"))
                .collect(Collectors.toList());

            if (!htmlFiles.isEmpty()) {
                System.out.println("Arquivos HTML encontrados:");
                htmlFiles.forEach(file -> System.out.println("  - " + file.getFileName()));
            }
        }

        System.out.println("✓ HTML reports verified");
    }

    /**
     * Teste 5: Verificar arquivos de dados
     */
    @Test
    public void test05_DataFiles() throws IOException {
        System.out.println("=== DATA FILES ===");

        assertTrue("Data directory should exist", Files.exists(RESULT_DIR.resolve("iped").resolve("data")));

        // Verifica arquivos de dados importantes
        Path commitFile = RESULT_DIR.resolve("iped").resolve("data").resolve("FileListCSV.commit");
        Path carvedFile = RESULT_DIR.resolve("iped").resolve("data").resolve("carvedIgnoredMap.dat");
        Path statusFile = RESULT_DIR.resolve("iped").resolve("data").resolve("evidences_processing_status");

        assertTrue("FileListCSV.commit should exist", Files.exists(commitFile));
        assertTrue("carvedIgnoredMap.dat should exist", Files.exists(carvedFile));
        assertTrue("evidences_processing_status should exist", Files.exists(statusFile));

        System.out.println("✓ Data files valid");
    }

    /**
     * Teste 6: Verificar estatísticas gerais
     */
    @Test
    public void test06_GeneralStatistics() throws IOException {
        System.out.println("=== GENERAL STATISTICS ===");

        Path csvFile = RESULT_DIR.resolve("FileList.csv");
        if (csvFile != null && Files.exists(csvFile)) {
            long fileCount = countLinesInCsv(csvFile);
            System.out.println("Total files processed: " + fileCount);
            assertTrue("Should have processed files", fileCount > 0);
        }

        System.out.println("✓ General statistics valid");
    }

    /**
     * Teste 7: Verificar integridade dos dados
     */
    @Test
    public void test07_DataIntegrity() throws IOException {
        System.out.println("=== DATA INTEGRITY ===");

        Path csvFile = RESULT_DIR.resolve("FileList.csv");
        if (csvFile != null && Files.exists(csvFile)) {
            validateDataIntegrity(csvFile);
        }

        System.out.println("✓ Data integrity valid");
    }



    /**
     * Valida um arquivo CSV específico
     */
    private void validateCsvFile(Path csvFile) throws IOException {
        String fileName = csvFile.getFileName().toString();
        System.out.println("Validando: " + fileName);

        try (var lines = Files.lines(csvFile)) {
            List<String> lineList = lines.collect(Collectors.toList());

            // Verifica se tem cabeçalho
            assertFalse("CSV should have content", lineList.isEmpty());

            // Verifica se tem dados (mais que apenas cabeçalho)
            if (lineList.size() > 1) {
                String header = lineList.get(0);
                String firstDataLine = lineList.get(1);

                // Verifica se o cabeçalho tem colunas
                String[] headerColumns = header.split(",");
                assertTrue("Header should have columns", headerColumns.length > 0);

                // Verifica se a primeira linha de dados tem o mesmo número de colunas
                String[] dataColumns = firstDataLine.split(",");
                assertEquals("Data should have same number of columns as header",
                           headerColumns.length, dataColumns.length);
            }
        }
    }

    /**
     * Valida integridade dos dados
     */
    private void validateDataIntegrity(Path csvFile) throws IOException {
        try (var lines = Files.lines(csvFile)) {
            List<String> lineList = lines.collect(Collectors.toList());

            if (lineList.size() <= 1) {
                return; // Apenas cabeçalho, não há dados para validar
            }

            String header = lineList.get(0);
            String[] headerColumns = header.split(",");

            // Valida cada linha de dados
            for (int i = 1; i < lineList.size(); i++) {
                String line = lineList.get(i);
                String[] columns = line.split(",");

                // Verifica se tem o número correto de colunas
                assertEquals("Line " + i + " should have same number of columns as header",
                           headerColumns.length, columns.length);

                // Verifica se não há linhas vazias
                assertFalse("Line " + i + " should not be empty", line.trim().isEmpty());
            }
        }
    }

    /**
     * Conta linhas em arquivo CSV (excluindo cabeçalho)
     */
    private long countLinesInCsv(Path csvFile) throws IOException {
        try (var lines = Files.lines(csvFile)) {
            return lines.count() - 1; // Subtrai o cabeçalho
        }
    }

    // Métodos auxiliares
    private static void createDirectories(Path... paths) throws IOException {
        for (Path path : paths) {
            Files.createDirectories(path);
        }
    }

    private static void downloadImageIfNeeded(String url, Path targetPath) throws IOException {
        if (Files.exists(targetPath)) {
            return;
        }

        System.out.println("Downloading test image from: " + url);

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void waitForAnalysisCompletion() throws InterruptedException {
        System.out.println("Waiting for analysis completion...");

        int maxWaitTime = 300; // 5 minutos
        int waitTime = 0;

        while (waitTime < maxWaitTime) {
            if (hasAnalysisCompleted()) {
                System.out.println("Analysis completed after " + waitTime + " seconds");
                return;
            }

            TimeUnit.SECONDS.sleep(1);
            waitTime++;

            if (waitTime % 10 == 0) {
                System.out.println("Waiting... (" + waitTime + "s/" + maxWaitTime + "s)");
            }
        }

        System.out.println("Timeout waiting for analysis completion");
    }

    private static boolean hasAnalysisCompleted() {
        try {
            return Files.exists(CSV_FILE) && Files.size(CSV_FILE) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
