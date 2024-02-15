package iped.parsers.vlc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VLCIniParser {

    private static final String RECENT_APPS_SECTION = "[RecentMedia]";

    public static List<String> parseRecentFiles(String iniFilePath) throws IOException {
        List<String> recentFiles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(iniFilePath))) {
            String line;
            boolean insideRecentAppsSection = false;

            while ((line = reader.readLine()) != null) {
                // Procurando pela seção [RecentApps]
                if (isRecentAppsSection(line)) {
                    insideRecentAppsSection = true;
                } else if (insideRecentAppsSection && !line.trim().isEmpty()) {
                    // Dentro da seção [RecentApps], pegar as linhas com caminhos de arquivos
                    recentFiles.add(line.trim());
                }
            }
        }

        return recentFiles;
    }

    private static boolean isRecentAppsSection(String line) {
        return line.trim().equalsIgnoreCase(RECENT_APPS_SECTION);
    }
}
