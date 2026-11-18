package iped.app.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Local templates and HTTPS imports. No credentials or shared links are persisted. */
public class WordReportFiles {

    static final long MAX_DOWNLOAD = 300L * 1024 * 1024;
    private static SourceSelection lastSelection;
    private static String selectionHome;
    private static final Map<Path, SourceSelection> caseSelections = new HashMap<>();

    /** Keep each case's working document separate; shared URLs remain in memory only. */
    public static SourceSelection lastSelection(String caseKey) throws IOException {
        Path file = caseSelectionFile(caseKey);
        if (caseSelections.containsKey(file)) {
            return caseSelections.get(file);
        }
        if (Files.isRegularFile(file)) {
            Properties settings = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file)) {
                settings.load(reader);
            }
            String type = settings.getProperty("type");
            if ("0".equals(type) || "2".equals(type)) {
                SourceSelection selection = new SourceSelection(Integer.parseInt(type), settings.getProperty("value", ""));
                caseSelections.put(file, selection);
                return selection;
            }
        }
        return null;
    }

    public static void rememberSelection(String caseKey, int type, String value) throws IOException {
        Path file = caseSelectionFile(caseKey);
        caseSelections.put(file, new SourceSelection(type, value));
        if (type == 1) {
            return;
        }
        Properties settings = new Properties();
        settings.setProperty("type", Integer.toString(type));
        settings.setProperty("value", value);
        Path staged = Files.createTempFile(file.getParent(), "case-document-", ".tmp");
        try {
            try (java.io.Writer writer = Files.newBufferedWriter(staged)) {
                settings.store(writer, null);
            }
            Files.move(staged, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static Path caseSelectionFile(String caseKey) throws IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(caseKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte value : digest) {
                name.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            return templateFolder().getParent().resolve("case-" + name + "-document.properties");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static class SourceSelection {
        public final int type;
        public final String value;

        SourceSelection(int type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    public static SourceSelection lastSelection() throws IOException {
        String home = System.getProperty("user.home");
        if (home.equals(selectionHome)) {
            return lastSelection;
        }
        selectionHome = home;
        lastSelection = null;
        Path file = templateFolder().getParent().resolve("last-document.properties");
        if (Files.isRegularFile(file)) {
            Properties settings = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file)) {
                settings.load(reader);
            }
            String type = settings.getProperty("type");
            if ("0".equals(type) || "2".equals(type)) {
                lastSelection = new SourceSelection(Integer.parseInt(type), settings.getProperty("value", ""));
            }
        }
        return lastSelection;
    }

    public static void rememberSelection(int type, String value) throws IOException {
        selectionHome = System.getProperty("user.home");
        lastSelection = new SourceSelection(type, value);
        if (type == 1) {
            return; // Shared links can carry access tokens; retain them only for this application session.
        }
        Properties settings = new Properties();
        settings.setProperty("type", Integer.toString(type));
        settings.setProperty("value", value);
        Path file = templateFolder().getParent().resolve("last-document.properties");
        Path staged = Files.createTempFile(file.getParent(), "last-document-", ".tmp");
        try {
            try (java.io.Writer writer = Files.newBufferedWriter(staged)) {
                settings.store(writer, null);
            }
            Files.move(staged, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static Path templateFolder() throws IOException {
        Path folder = Path.of(System.getProperty("user.home"), ".iped", "word-report", "templates");
        Files.createDirectories(folder);
        return folder;
    }

    public static List<Path> templates() throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(templateFolder())) {
            files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).forEach(result::add);
        }
        return result;
    }

    public static Path saveTemplate(Path source) throws Exception {
        new WordReportDocument(source);
        String name = source.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IOException(Messages.getString("WordReport.InvalidDocument"));
        }
        Path destination = templateFolder().resolve(name);
        if (Files.exists(destination)) {
            destination = templateFolder().resolve(UUID.randomUUID() + "-" + name);
        }
        Files.copy(source, destination);
        return destination;
    }

    public static Path defaultTemplate(Path work) throws IOException {
        Path destination = work.resolve("modelo-relatorio.docx");
        WordReportTemplate.create(destination);
        return destination;
    }

    public static URI httpsUri(String link) throws IOException {
        try {
            URI uri = URI.create(link.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IOException(Messages.getString("WordReport.HttpsOnly"));
        }
    }

    public static Path download(String link, Path work) throws Exception {
        URI uri = httpsUri(link);
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        break;
                    }
                    uri = httpsUri(uri.resolve(location).toString());
                    continue;
                }
                String type = connection.getContentType();
                if (status != 200 || type != null && type.toLowerCase(Locale.ROOT).contains("text/html")) {
                    throw new IOException(Messages.getString("WordReport.LoginDownload"));
                }
                if (connection.getContentLengthLong() > MAX_DOWNLOAD) {
                    throw new IOException(Messages.getString("WordReport.DownloadSize"));
                }
                Path destination = work.resolve("download-" + UUID.randomUUID() + ".docx");
                boolean valid = false;
                try {
                    try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(destination)) {
                        byte[] buffer = new byte[65536];
                        long total = 0;
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_DOWNLOAD) {
                                throw new IOException(Messages.getString("WordReport.DownloadSize"));
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                    new WordReportDocument(destination);
                    valid = true;
                    return destination;
                } finally {
                    if (!valid) {
                        Files.deleteIfExists(destination);
                    }
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException(Messages.getString("WordReport.LoginDownload"));
    }
}
