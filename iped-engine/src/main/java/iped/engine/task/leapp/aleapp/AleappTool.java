package iped.engine.task.leapp.aleapp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;

import iped.engine.task.leapp.LeappTool;

/**
 * ALEAPP (Android Logs Events And Protobuf Parser): what is specific to Android extractions.
 */
public final class AleappTool extends LeappTool {

    public static final AleappTool INSTANCE = new AleappTool();

    // Android exposes the same storage under several symbolic links / bind mounts, so the file path an app
    // records often differs from where the file physically lives in the extraction tree. Each entry maps an
    // app-visible prefix (key) to the underlying extraction location (value). More specific keys come first.
    private static final Map<String, String> SYMLINK_PREFIXES;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("/storage/emulated", "/data/media");
        map.put("/storage/self/primary", "/data/media/0");
        map.put("/mnt/sdcard", "/data/media/0");
        map.put("/mnt/user/0/primary", "/data/media/0");
        map.put("/sdcard", "/data/media/0");
        map.put("/data/user/0", "/data/data");
        SYMLINK_PREFIXES = Collections.unmodifiableMap(map);
    }

    // Per-plugin exceptions: columns whose name matches the file-path rules but do NOT hold a filesystem path in
    // that specific plugin, so the file lookup must be skipped. Kept plugin-scoped (keyed by the plugin module name)
    // because the same column name can be a real file path elsewhere: "Path" is a cookie/URL path in the cookie
    // plugins but a MediaStore file path in emulatedSmeta and a transfer path in Zapya. Module names and column
    // names are written exactly as declared in the plugins.
    private static final Map<String, Set<String>> NON_FILE_PATH_COLUMNS = Map.of( //
            "chromeCookies", Set.of("Path"), //
            "firefoxCookies", Set.of("Path"), //
            "OrnetBrowser", Set.of("Path"), //
            "FairEmail", Set.of("Return Path"), //
            "DuckDuckGo", Set.of("Folder Path"), //
            "libretorrentFR", Set.of("Length - Path"));

    private AleappTool() {
        super("aleapp", "ALEAPP");
    }

    @Override
    public int[] getMinPythonVersion() {
        return new int[] { 3, 11 };
    }

    @Override
    public MediaType resolveRowMediaType(String moduleName, String pluginName, String artifactName,
            boolean conversationRows) {
        return AleappMediaTypeResolver.resolveMediaType(moduleName, pluginName, artifactName, conversationRows);
    }

    @Override
    public Map<String, String> getPathAliases() {
        return SYMLINK_PREFIXES;
    }

    @Override
    public boolean isNonFilePathColumn(String moduleName, String headerName) {
        Set<String> columns = NON_FILE_PATH_COLUMNS.get(moduleName);
        return columns != null && columns.contains(headerName);
    }
}
