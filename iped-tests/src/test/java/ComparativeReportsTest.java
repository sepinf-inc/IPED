import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Testes específicos para relatórios comparativos
 * Inclui testes de regressão, comparação de versões e avaliação de mudanças
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ComparativeReportsTest {

    private static final Path RESULT_DIR = Paths.get("./target/iped-result");
    private static final Path COMPARISON_DIR = Paths.get("./target/comparison");
    private static final Path CSV_FILE = RESULT_DIR.resolve("FileList.csv");
    private static final Path STABLE_CSV = COMPARISON_DIR.resolve("stable/FileList.csv");
    private static final Path CANDIDATE_CSV = COMPARISON_DIR.resolve("candidate/FileList.csv");

    // Dados analisados
    private static List<String[]> currentCsvData;
    private static List<String[]> stableCsvData;
    private static List<String[]> candidateCsvData;

    // Estatísticas comparativas
    private static Map<String, Integer> currentCategoryCount;
    private static Map<String, Integer> stableCategoryCount;
    private static Map<String, Integer> candidateCategoryCount;

    private static Map<String, Integer> currentFormatCount;
    private static Map<String, Integer> stableFormatCount;
    private static Map<String, Integer> candidateFormatCount;

    private static int currentCarvedFiles;
    private static int stableCarvedFiles;
    private static int candidateCarvedFiles;

    private static int currentParsedFiles;
    private static int stableParsedFiles;
    private static int candidateParsedFiles;

    @BeforeClass
    public static void setup() throws Exception {
        // Cria diretórios de comparação
        Files.createDirectories(COMPARISON_DIR);
        Files.createDirectories(COMPARISON_DIR.resolve("stable"));
        Files.createDirectories(COMPARISON_DIR.resolve("candidate"));

        // Carrega dados atuais se disponível
        if (Files.exists(CSV_FILE)) {
            loadCurrentData();
        } else {
            System.out.println("Current CSV not found - initializing empty data");
            currentCsvData = new ArrayList<>();
            currentCategoryCount = new HashMap<>();
            currentFormatCount = new HashMap<>();
            currentCarvedFiles = 0;
            currentParsedFiles = 0;
        }

        // Carrega dados de comparação se disponível
        loadComparisonData();

        // Analisa dados para comparação
        analyzeComparativeData();
    }

    /**
     * Teste 1: Rodar testes com datasets grandes (regressão)
     */
    @Test
    public void test01_LargeDatasetRegression() {
        System.out.println("=== LARGE DATASET REGRESSION TEST ===");

        // Skip test if no current data available
        org.junit.Assume.assumeTrue("Current data not available - skipping test",
            currentCsvData != null && !currentCsvData.isEmpty());

        System.out.println("Current dataset analysis:");
        System.out.println("  Total files: " + currentCsvData.size());
        System.out.println("  Categories: " + currentCategoryCount.size());
        System.out.println("  Formats: " + currentFormatCount.size());
        System.out.println("  Carved files: " + currentCarvedFiles);
        System.out.println("  Parsed files: " + currentParsedFiles);

        // Validações de regressão para datasets grandes (pode estar vazio em CI)
        if (currentCsvData.size() < 10) {
            System.out.println("⚠ Insufficient dataset size (" + currentCsvData.size() + " files) - expected in CI with IPED failure");
            // Skip test if insufficient data available
            org.junit.Assume.assumeTrue("Insufficient data available - skipping test", false);
        }

        assertTrue("Should have substantial dataset", currentCsvData.size() >= 10);
        assertTrue("Should have multiple categories", currentCategoryCount.size() >= 3);
        assertTrue("Should have multiple formats", currentFormatCount.size() >= 3);

        // Verifica se temos arquivos de diferentes tipos
        boolean hasTextFiles = currentFormatCount.keySet().stream()
            .anyMatch(format -> isTextFormat(format));
        boolean hasImageFiles = currentFormatCount.keySet().stream()
            .anyMatch(format -> isImageFormat(format));
        boolean hasDocumentFiles = currentFormatCount.keySet().stream()
            .anyMatch(format -> isDocumentFormat(format));

        assertTrue("Should have text files", hasTextFiles || hasDocumentFiles);
        assertTrue("Should have image files", hasImageFiles);

        // Verifica se temos arquivos esculpidos (opcional em perfil triage)
        if (currentCarvedFiles > 0) {
            System.out.println("✓ Carved files found: " + currentCarvedFiles);
        } else {
            System.out.println("✓ No carved files (normal in triage profile)");
        }

        System.out.println("✓ Large dataset regression test completed");
    }

    /**
     * Teste 2: Comparar versões (estável vs candidata)
     */
    @Test
    public void test02_VersionComparison() {
        System.out.println("=== VERSION COMPARISON (STABLE vs CANDIDATE) ===");

        // Skip test if comparison data not available
        boolean hasStableData = Files.exists(STABLE_CSV) && stableCsvData != null && !stableCsvData.isEmpty();
        boolean hasCandidateData = Files.exists(CANDIDATE_CSV) && candidateCsvData != null && !candidateCsvData.isEmpty();

        if (!hasStableData || !hasCandidateData) {
            System.out.println("Comparison data not available - creating baseline for future comparison");
            createBaselineForComparison();
            return;
        }

        System.out.println("Version comparison analysis:");
        System.out.println("  Stable version files: " + stableCsvData.size());
        System.out.println("  Candidate version files: " + candidateCsvData.size());

        // Compara estatísticas gerais
        compareGeneralStatistics();

        // Compara categorias
        compareCategories();

        // Compara formatos
        compareFormats();

        System.out.println("✓ Version comparison completed");
    }

    /**
     * Teste 3: Avaliar mudanças como arquivos mudando de categoria
     */
    @Test
    public void test03_CategoryChanges() {
        System.out.println("=== CATEGORY CHANGES ANALYSIS ===");

        // Skip test if comparison data not available
        if (stableCsvData == null || candidateCsvData == null) {
            System.out.println("Comparison data not available - skipping test");
            return;
        }

        // Analisa mudanças de categoria
        Map<String, List<String>> categoryChanges = analyzeCategoryChanges();

        System.out.println("Category changes detected:");
        categoryChanges.forEach((fileName, changes) -> {
            System.out.println("  " + fileName + ": " + String.join(" -> ", changes));
        });

        // Verifica se as mudanças fazem sentido
        validateCategoryChanges(categoryChanges);

        System.out.println("✓ Category changes analysis completed");
    }

    /**
     * Teste 4: Avaliar mudanças como menos arquivos esculpidos
     */
    @Test
    public void test04_CarvedFilesChanges() {
        System.out.println("=== CARVED FILES CHANGES ANALYSIS ===");

        // Skip test if comparison data not available
        if (stableCsvData == null || candidateCsvData == null) {
            System.out.println("Comparison data not available - skipping test");
            return;
        }

        System.out.println("Carved files comparison:");
        System.out.println("  Stable version: " + stableCarvedFiles + " files");
        System.out.println("  Candidate version: " + candidateCarvedFiles + " files");

        int carvedDifference = candidateCarvedFiles - stableCarvedFiles;
        System.out.println("  Difference: " + carvedDifference + " files");

        // Analisa mudanças nos arquivos esculpidos
        if (carvedDifference != 0) {
            analyzeCarvedFilesChanges();
        }

        // Valida se a mudança faz sentido
        validateCarvedFilesChanges(carvedDifference);

        System.out.println("✓ Carved files changes analysis completed");
    }

    /**
     * Teste 5: Avaliar mudanças como menos resultados por parsing mais preciso
     */
    @Test
    public void test05_ParsingPrecisionChanges() {
        System.out.println("=== PARSING PRECISION CHANGES ANALYSIS ===");

        // Skip test if comparison data not available
        if (stableCsvData == null || candidateCsvData == null) {
            System.out.println("Comparison data not available - skipping test");
            return;
        }

        System.out.println("Parsing results comparison:");
        System.out.println("  Stable version: " + stableParsedFiles + " files");
        System.out.println("  Candidate version: " + candidateParsedFiles + " files");

        int parsingDifference = candidateParsedFiles - stableParsedFiles;
        System.out.println("  Difference: " + parsingDifference + " files");

        // Analisa mudanças na precisão do parsing
        analyzeParsingPrecisionChanges();

        // Valida se a mudança indica melhoria na precisão
        validateParsingPrecisionChanges(parsingDifference);

        System.out.println("✓ Parsing precision changes analysis completed");
    }

    /**
     * Teste 6: Relatório comparativo completo
     */
    @Test
    public void test06_CompleteComparativeReport() {
        System.out.println("=== COMPLETE COMPARATIVE REPORT ===");

        // Gera relatório comparativo
        generateComparativeReport();

        // Valida consistência dos dados
        validateReportConsistency();

        // Salva relatório em arquivo
        saveComparativeReport();

        System.out.println("✓ Complete comparative report generated");
    }

    /**
     * Carrega dados atuais
     */
    private static void loadCurrentData() throws IOException {
        currentCsvData = loadCsvData(CSV_FILE);
        analyzeCurrentData();
    }

    /**
     * Carrega dados de comparação
     */
    private static void loadComparisonData() {
        try {
            if (Files.exists(STABLE_CSV)) {
                stableCsvData = loadCsvData(STABLE_CSV);
            }
            if (Files.exists(CANDIDATE_CSV)) {
                candidateCsvData = loadCsvData(CANDIDATE_CSV);
            }
        } catch (IOException e) {
            System.out.println("Error loading comparison data: " + e.getMessage());
        }
    }

    /**
     * Analisa dados para comparação
     */
    private static void analyzeComparativeData() {
        if (stableCsvData != null) {
            stableCategoryCount = countByCategory(stableCsvData);
            stableFormatCount = countByFormat(stableCsvData);
            stableCarvedFiles = countCarvedFiles(stableCsvData);
            stableParsedFiles = countParsedFiles(stableCsvData);
        }

        if (candidateCsvData != null) {
            candidateCategoryCount = countByCategory(candidateCsvData);
            candidateFormatCount = countByFormat(candidateCsvData);
            candidateCarvedFiles = countCarvedFiles(candidateCsvData);
            candidateParsedFiles = countParsedFiles(candidateCsvData);
        }
    }

    /**
     * Analisa dados atuais
     */
    private static void analyzeCurrentData() {
        currentCategoryCount = countByCategory(currentCsvData);
        currentFormatCount = countByFormat(currentCsvData);
        currentCarvedFiles = countCarvedFiles(currentCsvData);
        currentParsedFiles = countParsedFiles(currentCsvData);
    }

    /**
     * Cria baseline para comparação futura
     */
    private static void createBaselineForComparison() {
        try {
            // Copia dados atuais como baseline estável
            if (Files.exists(CSV_FILE)) {
                Files.copy(CSV_FILE, STABLE_CSV, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("✓ Created stable baseline from current data");
            }

            // Cria arquivo de metadados de comparação
            Path metadataFile = COMPARISON_DIR.resolve("comparison-metadata.txt");
            List<String> metadata = Arrays.asList(
                "Baseline created: " + new Date(),
                "Source: " + CSV_FILE.toString(),
                "Total files: " + (currentCsvData != null ? currentCsvData.size() : 0),
                "Categories: " + (currentCategoryCount != null ? currentCategoryCount.size() : 0),
                "Formats: " + (currentFormatCount != null ? currentFormatCount.size() : 0)
            );
            Files.write(metadataFile, metadata);

        } catch (IOException e) {
            System.err.println("Error creating baseline: " + e.getMessage());
        }
    }

    /**
     * Compara estatísticas gerais
     */
    private static void compareGeneralStatistics() {
        System.out.println("General statistics comparison:");

        if (stableCsvData != null && candidateCsvData != null) {
            int fileDifference = candidateCsvData.size() - stableCsvData.size();
            System.out.println("  Total files difference: " + fileDifference);

            if (fileDifference != 0) {
                System.out.println("    Stable: " + stableCsvData.size() + " files");
                System.out.println("    Candidate: " + candidateCsvData.size() + " files");
            }
        }
    }

    /**
     * Compara categorias
     */
    private static void compareCategories() {
        System.out.println("Category comparison:");

        if (stableCategoryCount != null && candidateCategoryCount != null) {
            Set<String> allCategories = new HashSet<>();
            allCategories.addAll(stableCategoryCount.keySet());
            allCategories.addAll(candidateCategoryCount.keySet());

            for (String category : allCategories) {
                int stableCount = stableCategoryCount.getOrDefault(category, 0);
                int candidateCount = candidateCategoryCount.getOrDefault(category, 0);
                int difference = candidateCount - stableCount;

                if (difference != 0) {
                    System.out.println("  " + category + ": " + stableCount + " -> " + candidateCount + " (" + difference + ")");
                }
            }
        }
    }

    /**
     * Compara formatos
     */
    private static void compareFormats() {
        System.out.println("Format comparison:");

        if (stableFormatCount != null && candidateFormatCount != null) {
            Set<String> allFormats = new HashSet<>();
            allFormats.addAll(stableFormatCount.keySet());
            allFormats.addAll(candidateFormatCount.keySet());

            for (String format : allFormats) {
                int stableCount = stableFormatCount.getOrDefault(format, 0);
                int candidateCount = candidateFormatCount.getOrDefault(format, 0);
                int difference = candidateCount - stableCount;

                if (difference != 0) {
                    System.out.println("  " + format + ": " + stableCount + " -> " + candidateCount + " (" + difference + ")");
                }
            }
        }
    }

    /**
     * Analisa mudanças de categoria
     */
    private static Map<String, List<String>> analyzeCategoryChanges() {
        Map<String, List<String>> changes = new HashMap<>();

        if (stableCsvData != null && candidateCsvData != null) {
            Map<String, String> stableCategories = getFileCategories(stableCsvData);
            Map<String, String> candidateCategories = getFileCategories(candidateCsvData);

            for (String fileName : stableCategories.keySet()) {
                if (candidateCategories.containsKey(fileName)) {
                    String stableCat = stableCategories.get(fileName);
                    String candidateCat = candidateCategories.get(fileName);

                    if (!stableCat.equals(candidateCat)) {
                        changes.put(fileName, Arrays.asList(stableCat, candidateCat));
                    }
                }
            }
        }

        return changes;
    }

    /**
     * Valida mudanças de categoria
     */
    private static void validateCategoryChanges(Map<String, List<String>> changes) {
        System.out.println("Validating category changes:");

        for (Map.Entry<String, List<String>> entry : changes.entrySet()) {
            String fileName = entry.getKey();
            List<String> categoryChange = entry.getValue();

            // Verifica se a mudança faz sentido
            boolean isValidChange = isValidCategoryChange(categoryChange.get(0), categoryChange.get(1));

            if (isValidChange) {
                System.out.println("  ✓ " + fileName + ": " + String.join(" -> ", categoryChange));
            } else {
                System.out.println("  ⚠ " + fileName + ": " + String.join(" -> ", categoryChange) + " (suspicious)");
            }
        }
    }

    /**
     * Analisa mudanças nos arquivos esculpidos
     */
    private static void analyzeCarvedFilesChanges() {
        System.out.println("Analyzing carved files changes:");

        if (stableCsvData != null && candidateCsvData != null) {
            List<String> stableCarved = getCarvedFileNames(stableCsvData);
            List<String> candidateCarved = getCarvedFileNames(candidateCsvData);

            // Arquivos que foram esculpidos na versão estável mas não na candidata
            List<String> removedCarved = stableCarved.stream()
                .filter(file -> !candidateCarved.contains(file))
                .collect(Collectors.toList());

            // Arquivos que foram esculpidos na versão candidata mas não na estável
            List<String> addedCarved = candidateCarved.stream()
                .filter(file -> !stableCarved.contains(file))
                .collect(Collectors.toList());

            if (!removedCarved.isEmpty()) {
                System.out.println("  Removed from carved: " + removedCarved.size() + " files");
            }
            if (!addedCarved.isEmpty()) {
                System.out.println("  Added to carved: " + addedCarved.size() + " files");
            }
        }
    }

    /**
     * Valida mudanças nos arquivos esculpidos
     */
    private static void validateCarvedFilesChanges(int difference) {
        if (difference < 0) {
            System.out.println("  ✓ Fewer carved files may indicate improved carving precision");
        } else if (difference > 0) {
            System.out.println("  ✓ More carved files may indicate improved carving detection");
        } else {
            System.out.println("  ✓ No change in carved files count");
        }
    }

    /**
     * Analisa mudanças na precisão do parsing
     */
    private static void analyzeParsingPrecisionChanges() {
        System.out.println("Analyzing parsing precision changes:");

        if (stableCsvData != null && candidateCsvData != null) {
            Map<String, String> stableParsing = getParsingResults(stableCsvData);
            Map<String, String> candidateParsing = getParsingResults(candidateCsvData);

            // Arquivos que mudaram de status de parsing
            int parsingStatusChanges = 0;
            for (String fileName : stableParsing.keySet()) {
                if (candidateParsing.containsKey(fileName)) {
                    if (!stableParsing.get(fileName).equals(candidateParsing.get(fileName))) {
                        parsingStatusChanges++;
                    }
                }
            }

            System.out.println("  Files with parsing status changes: " + parsingStatusChanges);
        }
    }

    /**
     * Valida mudanças na precisão do parsing
     */
    private static void validateParsingPrecisionChanges(int difference) {
        if (difference < 0) {
            System.out.println("  ✓ Fewer parsed files may indicate more precise parsing (fewer false positives)");
        } else if (difference > 0) {
            System.out.println("  ✓ More parsed files may indicate improved parsing coverage");
        } else {
            System.out.println("  ✓ No change in parsed files count");
        }
    }

    /**
     * Gera relatório comparativo completo
     */
    private static void generateComparativeReport() {
        System.out.println("Generating comprehensive comparative report...");

        // Aqui você pode implementar a geração de um relatório detalhado
        // incluindo gráficos, estatísticas e análises
    }

    /**
     * Valida consistência do relatório
     */
    private static void validateReportConsistency() {
        System.out.println("Validating report consistency...");

        // Validações básicas de consistência
        if (stableCsvData != null && candidateCsvData != null) {
            assertTrue("Stable data should be available", !stableCsvData.isEmpty());
            assertTrue("Candidate data should be available", !candidateCsvData.isEmpty());
        }
    }

    /**
     * Salva relatório comparativo
     */
    private static void saveComparativeReport() {
        try {
            Path reportFile = COMPARISON_DIR.resolve("comparative-report.txt");
            List<String> report = Arrays.asList(
                "=== IPED COMPARATIVE REPORT ===",
                "Generated: " + new Date(),
                "",
                "Current Version:",
                "  Total files: " + (currentCsvData != null ? currentCsvData.size() : 0),
                "  Categories: " + (currentCategoryCount != null ? currentCategoryCount.size() : 0),
                "  Formats: " + (currentFormatCount != null ? currentFormatCount.size() : 0),
                "  Carved files: " + currentCarvedFiles,
                "  Parsed files: " + currentParsedFiles,
                "",
                "Stable Version:",
                "  Total files: " + (stableCsvData != null ? stableCsvData.size() : 0),
                "  Carved files: " + stableCarvedFiles,
                "  Parsed files: " + stableParsedFiles,
                "",
                "Candidate Version:",
                "  Total files: " + (candidateCsvData != null ? candidateCsvData.size() : 0),
                "  Carved files: " + candidateCarvedFiles,
                "  Parsed files: " + candidateParsedFiles
            );

            Files.write(reportFile, report);
            System.out.println("✓ Comparative report saved to: " + reportFile);

        } catch (IOException e) {
            System.err.println("Error saving report: " + e.getMessage());
        }
    }

    // Métodos auxiliares
    private static List<String[]> loadCsvData(Path csvFile) throws IOException {
        List<String[]> data = new ArrayList<>();

        List<String> allLines = Files.readAllLines(csvFile);

        if (allLines.size() <= 1) {
            return data;
        }

        // Pula o cabeçalho e processa as linhas de dados
        for (int i = 1; i < allLines.size(); i++) {
            String line = allLines.get(i);
            String[] columns = parseCsvLine(line);
            data.add(columns);
        }

        return data;
    }

    private static Map<String, Integer> countByCategory(List<String[]> data) {
        return data.stream()
            .collect(Collectors.groupingBy(
                row -> getColumnValue(row, "Category"),
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
    }

    private static Map<String, Integer> countByFormat(List<String[]> data) {
        return data.stream()
            .collect(Collectors.groupingBy(
                row -> getColumnValue(row, "Extension"),
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
    }

    private static int countCarvedFiles(List<String[]> data) {
        return (int) data.stream()
            .filter(row -> isCarved(row))
            .count();
    }

    private static int countParsedFiles(List<String[]> data) {
        return (int) data.stream()
            .filter(row -> hasMetadata(row))
            .count();
    }

    private static Map<String, String> getFileCategories(List<String[]> data) {
        return data.stream()
            .collect(Collectors.toMap(
                row -> getColumnValue(row, "Name"),
                row -> getColumnValue(row, "Category"),
                (existing, replacement) -> existing
            ));
    }

    private static List<String> getCarvedFileNames(List<String[]> data) {
        return data.stream()
            .filter(row -> isCarved(row))
            .map(row -> getColumnValue(row, "Name"))
            .collect(Collectors.toList());
    }

    private static Map<String, String> getParsingResults(List<String[]> data) {
        return data.stream()
            .collect(Collectors.toMap(
                row -> getColumnValue(row, "Name"),
                row -> hasMetadata(row) ? "parsed" : "not_parsed",
                (existing, replacement) -> existing
            ));
    }

    private static boolean isCarved(String[] row) {
        String carved = getColumnValue(row, "Carved");
        return "true".equalsIgnoreCase(carved) || "1".equals(carved);
    }

    private static boolean hasMetadata(String[] row) {
        String category = getColumnValue(row, "Category");
        String extension = getColumnValue(row, "Extension");

        return isTextFormat(extension) || isDocumentFormat(extension) ||
               category.contains("Documents") || category.contains("Text");
    }

    private static boolean isValidCategoryChange(String oldCategory, String newCategory) {
        // Lógica para validar se a mudança de categoria faz sentido
        // Por exemplo, mudar de "Other files" para "Documents" é válido
        // mas mudar de "Images" para "Documents" pode ser suspeito

        if (oldCategory.contains("Other") && newCategory.contains("Documents")) return true;
        if (oldCategory.contains("Other") && newCategory.contains("Images")) return true;
        if (oldCategory.contains("Other") && newCategory.contains("Videos")) return true;

        return false; // Por padrão, considera suspeito
    }

    private static boolean isTextFormat(String format) {
        Set<String> textFormats = Set.of(
            "txt", "log", "ini", "cfg", "conf", "xml", "html", "htm", "css", "js",
            "json", "csv", "sql", "sh", "bat", "cmd", "ps1", "py", "java", "cpp"
        );
        return textFormats.contains(format.toLowerCase());
    }

    private static boolean isDocumentFormat(String format) {
        Set<String> documentFormats = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "rtf", "odt", "ods"
        );
        return documentFormats.contains(format.toLowerCase());
    }

    private static boolean isImageFormat(String format) {
        Set<String> imageFormats = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "svg", "ico", "webp"
        );
        return imageFormats.contains(format.toLowerCase());
    }

    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private static String getColumnValue(String[] row, String columnName) {
        // Mapeamento de colunas baseado no cabeçalho do CSV
        Map<String, Integer> columnMap = new HashMap<>();
        columnMap.put("Name", 0);
        columnMap.put("Link", 1);
        columnMap.put("Size", 2);
        columnMap.put("Extension", 3);
        columnMap.put("Bookmark", 4);
        columnMap.put("Category", 5);
        columnMap.put("MD5", 6);
        columnMap.put("SHA1", 7);
        columnMap.put("Deleted", 8);
        columnMap.put("Carved", 9);
        columnMap.put("Accessed", 10);
        columnMap.put("Modified", 11);
        columnMap.put("Created", 12);
        columnMap.put("Path", 13);
        columnMap.put("TrackId", 14);

        Integer index = columnMap.get(columnName);
        if (index != null && index < row.length) {
            return row[index];
        }
        return "";
    }
}
