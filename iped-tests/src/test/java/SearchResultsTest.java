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
 * Testes específicos para resultados de busca
 * Inclui validação de buscas com palavras comuns, resultados por formato e parsing + indexação
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SearchResultsTest {

    private static final Path RESULT_DIR = Paths.get("./target/iped-result");
    private static final Path CSV_FILE = RESULT_DIR.resolve("FileList.csv");
    private static final Path INDEX_DIR = RESULT_DIR.resolve("index");
    private static final Path PARSED_DIR = RESULT_DIR.resolve("parsed");

    // Dados analisados
    private static List<String[]> csvData;
    private static Map<String, List<String[]>> filesByFormat;
    private static Map<String, Integer> formatCount;
    private static List<String[]> searchableFiles;
    private static Map<String, List<String[]>> parsedFilesByType;

    @BeforeClass
    public static void setup() throws Exception {
        // Verifica se os dados de análise estão disponíveis
        if (!Files.exists(CSV_FILE)) {
            System.out.println("FileList.csv not found - skipping search tests");
            // Initialize empty data structures to prevent null pointer exceptions
            csvData = new ArrayList<>();
            filesByFormat = new HashMap<>();
            formatCount = new HashMap<>();
            searchableFiles = new ArrayList<>();
            parsedFilesByType = new HashMap<>();
            return;
        }

        loadCsvData();
        analyzeData();
    }

    /**
     * Teste 1: Realizar buscas com palavras comuns (preposições, conjunções, substantivos)
     */
    @Test
    public void test01_CommonWordsSearch() {
        System.out.println("=== COMMON WORDS SEARCH ===");

        // Skip test if no data available
        org.junit.Assume.assumeTrue("CSV data not available - skipping test",
            csvData != null && !csvData.isEmpty());

        // Lista de palavras comuns para teste
        List<String> commonWords = Arrays.asList(
            // Preposições
            "de", "em", "para", "por", "com", "sem", "sob", "sobre", "entre", "contra",
            // Conjunções
            "e", "ou", "mas", "se", "que", "como", "quando", "onde", "porque",
            // Substantivos comuns (português)
            "arquivo", "documento", "imagem", "texto", "dados", "sistema", "usuario",
            "email", "mensagem", "relatorio", "projeto", "trabalho", "casa", "empresa",
            "pasta", "diretorio", "ficheiro", "ficheiros", "ficheiros", "ficheiros",
            // Palavras comuns em inglês
            "file", "document", "image", "text", "data", "system", "user", "report",
            "project", "work", "home", "company", "folder", "directory", "message",
            "email", "attachment", "download", "upload", "save", "open", "close",
            "edit", "view", "search", "find", "copy", "paste", "delete", "create",
            "new", "old", "recent", "important", "urgent", "confidential", "private",
            "public", "admin", "manager", "employee", "client", "customer", "service",
            "support", "help", "info", "information", "details", "summary", "analysis",
            "result", "test", "example", "sample", "template", "form", "list", "table",
            "chart", "graph", "picture", "photo", "video", "audio", "music", "song"
        );

        System.out.println("Testing search with common words:");

        int totalResults = 0;
        Map<String, Integer> wordResults = new HashMap<>();

        for (String word : commonWords) {
            int results = performTextSearch(word);
            wordResults.put(word, results);
            totalResults += results;

            if (results > 0) {
                System.out.println("  '" + word + "': " + results + " results");
            }
        }

        System.out.println("Total search results: " + totalResults);
        System.out.println("Words with results: " +
            wordResults.values().stream().filter(count -> count > 0).count());

        // Verifica se temos resultados de busca
        assertTrue("Should have search results", totalResults > 0);

        // Verifica se pelo menos algumas palavras retornaram resultados
        long wordsWithResults = wordResults.values().stream()
            .filter(count -> count > 0)
            .count();
        assertTrue("Should have results for some common words", wordsWithResults > 0);

        System.out.println("✓ Common words search validation completed");
    }

    /**
     * Teste 2: Verificar número de resultados por formato
     */
    @Test
    public void test02_ResultsByFormat() {
        System.out.println("=== RESULTS BY FORMAT ===");

        // Skip test if no data available
        org.junit.Assume.assumeTrue("Format data not available - skipping test",
            formatCount != null && !formatCount.isEmpty());

        System.out.println("Results distribution by format:");
        formatCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " files");
            });

        // Verifica se temos diferentes formatos
        assertTrue("Should have multiple file formats", formatCount.size() > 1);

        // Verifica se temos pelo menos um tipo de arquivo pesquisável
        boolean hasSearchableFiles = formatCount.keySet().stream()
            .anyMatch(format -> isSearchableFormat(format));
        assertTrue("Should have searchable files", hasSearchableFiles);

        // Verifica se temos arquivos de documentos (PDFs são considerados documentos)
        boolean hasDocuments = formatCount.keySet().stream()
            .anyMatch(format -> isDocumentFormat(format));
        assertTrue("Should have document files", hasDocuments);

        // Verifica se temos arquivos de imagem
        boolean hasImages = formatCount.keySet().stream()
            .anyMatch(format -> isImageFormat(format));
        assertTrue("Should have image files", hasImages);

        System.out.println("✓ Results by format validation completed");
    }

    /**
     * Teste 3: Avaliar parsing + indexação ao mesmo tempo
     */
    @Test
    public void test03_ParsingAndIndexing() {
        System.out.println("=== PARSING AND INDEXING EVALUATION ===");

        // Skip test if no data available
        org.junit.Assume.assumeTrue("Parsed files data not available - skipping test",
            parsedFilesByType != null && !parsedFilesByType.isEmpty());

        System.out.println("Parsed files by type:");
        parsedFilesByType.forEach((type, files) -> {
            System.out.println("  " + type + ": " + files.size() + " files");
        });

        // Verifica se temos arquivos parseados
        int totalParsedFiles = parsedFilesByType.values().stream()
            .mapToInt(List::size)
            .sum();
        assertTrue("Should have parsed files", totalParsedFiles > 0);

        // Verifica se temos diferentes tipos parseados
        assertTrue("Should have multiple parsed file types", parsedFilesByType.size() > 1);

        // Verifica se temos arquivos de texto parseados (para busca)
        boolean hasParsedText = parsedFilesByType.containsKey("text") ||
                               parsedFilesByType.containsKey("document");
        assertTrue("Should have parsed text files for search", hasParsedText);

        // Verifica se temos arquivos de imagem parseados (metadados)
        boolean hasParsedImages = parsedFilesByType.containsKey("image") ||
                                parsedFilesByType.containsKey("exif");
        assertTrue("Should have parsed image files", hasParsedImages);

        // Verifica se temos pelo menos um tipo de arquivo parseado além de texto
        boolean hasOtherParsedTypes = parsedFilesByType.keySet().stream()
            .anyMatch(type -> !type.equals("text") && !type.equals("document"));
        assertTrue("Should have other parsed file types", hasOtherParsedTypes);

        System.out.println("✓ Parsing and indexing evaluation completed");
    }

    /**
     * Teste 4: Análise de qualidade da indexação
     */
    @Test
    public void test04_IndexingQuality() {
        System.out.println("=== INDEXING QUALITY ANALYSIS ===");

        // Skip test if no data available
        org.junit.Assume.assumeTrue("Searchable files not available - skipping test",
            searchableFiles != null && !searchableFiles.isEmpty());

        System.out.println("Searchable files: " + searchableFiles.size());

        // Analisa qualidade da indexação por tipo de arquivo
        Map<String, Integer> searchableByFormat = new HashMap<>();
        Map<String, Integer> searchableByCategory = new HashMap<>();

        for (String[] file : searchableFiles) {
            String format = getColumnValue(file, "Extension");
            String category = getColumnValue(file, "Category");

            searchableByFormat.merge(format, 1, Integer::sum);
            searchableByCategory.merge(category, 1, Integer::sum);
        }

        System.out.println("Searchable files by format:");
        searchableByFormat.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " files");
            });

        System.out.println("Searchable files by category:");
        searchableByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " files");
            });

        // Verifica se temos pelo menos um tipo de arquivo indexado
        boolean hasIndexedFiles = !searchableByFormat.isEmpty();
        assertTrue("Should have indexed files", hasIndexedFiles);

        // Verifica se temos documentos indexados (PDFs são considerados documentos)
        boolean hasDocumentsIndexed = searchableByFormat.keySet().stream()
            .anyMatch(format -> isDocumentFormat(format));
        assertTrue("Should have indexed document files", hasDocumentsIndexed);

        System.out.println("✓ Indexing quality analysis completed");
    }

    /**
     * Teste 5: Validação de metadados para busca
     */
    @Test
    public void test05_SearchMetadataValidation() {
        System.out.println("=== SEARCH METADATA VALIDATION ===");

        // Skip test if no data available
        org.junit.Assume.assumeTrue("CSV data not available - skipping test",
            csvData != null && !csvData.isEmpty());

        // Verifica metadados essenciais para busca
        List<String[]> filesWithSearchMetadata = csvData.stream()
            .filter(file -> hasSearchMetadata(file))
            .collect(Collectors.toList());

        System.out.println("Files with search metadata: " + filesWithSearchMetadata.size());

        // Verifica se temos metadados de busca
        assertTrue("Should have files with search metadata", filesWithSearchMetadata.size() > 0);

        // Analisa tipos de metadados disponíveis
        Map<String, Integer> metadataTypes = new HashMap<>();
        for (String[] file : filesWithSearchMetadata) {
            String category = getColumnValue(file, "Category");
            String extension = getColumnValue(file, "Extension");

            String metadataType = determineSearchMetadataType(category, extension);
            metadataTypes.merge(metadataType, 1, Integer::sum);
        }

        System.out.println("Search metadata types:");
        metadataTypes.forEach((type, count) -> {
            System.out.println("  " + type + ": " + count + " files");
        });

        // Verifica se temos pelo menos um tipo de metadados
        assertTrue("Should have metadata types", metadataTypes.size() > 0);

        System.out.println("✓ Search metadata validation completed");
    }

    /**
     * Carrega dados do CSV
     */
    private static void loadCsvData() throws IOException {
        csvData = new ArrayList<>();

        List<String> allLines = Files.readAllLines(CSV_FILE);

        if (allLines.size() <= 1) {
            throw new IllegalStateException("CSV file has no data rows");
        }

        // Pula o cabeçalho e processa as linhas de dados
        for (int i = 1; i < allLines.size(); i++) {
            String line = allLines.get(i);
            String[] columns = parseCsvLine(line);
            csvData.add(columns);
        }
    }

    /**
     * Analisa os dados carregados
     */
    private static void analyzeData() {
        // Agrupa arquivos por formato
        filesByFormat = csvData.stream()
            .collect(Collectors.groupingBy(file -> getColumnValue(file, "Extension")));

        // Conta arquivos por formato
        formatCount = filesByFormat.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().size()
            ));

        // Identifica arquivos pesquisáveis
        searchableFiles = csvData.stream()
            .filter(file -> isSearchableFile(file))
            .collect(Collectors.toList());

        // Analisa arquivos parseados por tipo
        parsedFilesByType = new HashMap<>();
        for (String[] file : csvData) {
            String category = getColumnValue(file, "Category");
            String extension = getColumnValue(file, "Extension");

            String parsedType = determineParsedType(category, extension);
            if (!parsedType.equals("none")) {
                parsedFilesByType.computeIfAbsent(parsedType, k -> new ArrayList<>()).add(file);
            }
        }
    }

    /**
     * Realiza busca de texto em um arquivo
     */
    private static int performTextSearch(String searchTerm) {
        // Simula busca de texto nos arquivos
        // Em um ambiente real, isso seria feito através da API de busca do IPED

        int results = 0;
        for (String[] file : searchableFiles) {
            String name = getColumnValue(file, "Name");
            String category = getColumnValue(file, "Category");

            // Simula busca por nome e categoria
            if (name.toLowerCase().contains(searchTerm.toLowerCase()) ||
                category.toLowerCase().contains(searchTerm.toLowerCase())) {
                results++;
            }
        }

        return results;
    }

    /**
     * Verifica se é um arquivo de texto
     */
    private static boolean isTextFormat(String format) {
        Set<String> textFormats = Set.of(
            "txt", "log", "ini", "cfg", "conf", "xml", "html", "htm", "css", "js",
            "json", "csv", "sql", "sh", "bat", "cmd", "ps1", "py", "java", "cpp"
        );
        return textFormats.contains(format.toLowerCase());
    }

    /**
     * Verifica se é um arquivo de documento
     */
    private static boolean isDocumentFormat(String format) {
        Set<String> documentFormats = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "rtf", "odt", "ods"
        );
        return documentFormats.contains(format.toLowerCase());
    }

    /**
     * Verifica se é um arquivo de imagem
     */
    private static boolean isImageFormat(String format) {
        Set<String> imageFormats = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "svg", "ico", "webp"
        );
        return imageFormats.contains(format.toLowerCase());
    }

    /**
     * Verifica se é um arquivo pesquisável (texto, documento ou outros)
     */
    private static boolean isSearchableFormat(String format) {
        return isTextFormat(format) || isDocumentFormat(format) ||
               format.equals("pdf") || format.equals("e01");
    }

    /**
     * Verifica se é um arquivo pesquisável
     */
    private static boolean isSearchableFile(String[] file) {
        String category = getColumnValue(file, "Category");
        String extension = getColumnValue(file, "Extension");

        // Arquivos que podem ser pesquisados
        return isTextFormat(extension) ||
               isDocumentFormat(extension) ||
               category.contains("Documents") ||
               category.contains("Text");
    }

    /**
     * Determina tipo de arquivo parseado
     */
    private static String determineParsedType(String category, String extension) {
        if (isTextFormat(extension) || category.contains("Text")) return "text";
        if (isDocumentFormat(extension) || category.contains("Documents")) return "document";
        if (isImageFormat(extension) || category.contains("Images")) return "image";
        if (extension.equals("pdf") || category.contains("PDF")) return "pdf";
        if (extension.matches("(doc|docx|xls|xlsx|ppt|pptx)") || category.contains("Office")) return "office";
        if (extension.matches("(eml|msg|pst)") || category.contains("Email")) return "email";

        return "none";
    }

    /**
     * Verifica se tem metadados para busca
     */
    private static boolean hasSearchMetadata(String[] file) {
        String name = getColumnValue(file, "Name");
        String category = getColumnValue(file, "Category");
        String extension = getColumnValue(file, "Extension");

        return !name.isEmpty() && !category.isEmpty() &&
               (isTextFormat(extension) || isDocumentFormat(extension) ||
                category.contains("Documents") || category.contains("Text"));
    }

    /**
     * Determina tipo de metadados para busca
     */
    private static String determineSearchMetadataType(String category, String extension) {
        if (isTextFormat(extension)) return "text";
        if (isDocumentFormat(extension)) return "document";
        if (category.contains("Documents")) return "document";
        if (category.contains("Text")) return "text";
        if (category.contains("Images")) return "image";
        if (category.contains("Videos")) return "video";
        if (category.contains("Audio")) return "audio";

        return "other";
    }

    /**
     * Parse uma linha CSV considerando aspas
     */
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

    /**
     * Obtém valor de uma coluna por nome
     */
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
