package iped.engine.task.yara;

import java.io.File;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;

/**
 * Static helper that locates IPED-installation-relative paths used by the
 * YARA-X integration:
 *
 * <ul>
 *   <li>{@link #getIpedRoot()} — root of the IPED release (the directory that
 *       holds {@code iped.jar}, {@code lib/}, {@code tools/}, {@code conf/}).</li>
 *   <li>{@link #getBundledLibraryDir()} — {@code tools/yara-x/<os>/} subdirectory
 *       inside the release, where the {@code libyara-x-capi} native binary
 *       lives.</li>
 * </ul>
 *
 * <p>Both methods walk up from this class's code source ({@code iped-engine}
 * jar inside {@code <iped-root>/lib/}) to locate the release root. When the
 * code is loaded from a non-release layout (IDE, Maven build, unit tests),
 * both methods return {@code null} so the caller can fall back to explicit
 * configuration ({@code engineLibraryHint}, {@code jna.library.path}, or the
 * {@code YARA_X_LIB_PATH} env var).</p>
 */
public final class YaraInstallPaths {

    private static final Logger logger = LoggerFactory.getLogger(YaraInstallPaths.class);

    private YaraInstallPaths() {
        // Static utility — no instantiation.
    }

    /**
     * Returns the root directory of the running IPED release, or {@code null}
     * when not running from a release layout.
     *
     * <p>The release layout that this method recognizes is the canonical one
     * produced by {@code mvn -pl iped-app -am install}:</p>
     * <pre>
     *   &lt;iped-root&gt;/
     *     iped.jar
     *     lib/iped-engine-&lt;version&gt;.jar    &larr; this class lives here
     *     tools/yara-x/...
     *     conf/...
     * </pre>
     */
    public static File getIpedRoot() {
        try {
            URL src = YaraInstallPaths.class.getProtectionDomain().getCodeSource().getLocation();
            if (src == null) {
                return null;
            }
            File jarOrDir = new File(src.toURI());
            if (!jarOrDir.isFile()) {
                // Running from target/classes (dev/test) — there is no IPED root.
                return null;
            }
            File libDir = jarOrDir.getParentFile();
            if (libDir == null) {
                return null;
            }
            File root = libDir.getParentFile();
            return (root != null && root.isDirectory()) ? root : null;
        } catch (Throwable t) {
            logger.debug("YaraInstallPaths: failed to detect IPED root: {}", t.toString());
            return null;
        }
    }

    /**
     * Returns the {@code tools/yara-x/<os>/} directory inside the IPED release,
     * or {@code null} when the release layout cannot be detected or the
     * platform-specific subdirectory does not exist.
     */
    public static File getBundledLibraryDir() {
        File root = getIpedRoot();
        if (root == null) {
            return null;
        }
        String subdir;
        if (Platform.isWindows()) {
            subdir = "win64";
        } else if (Platform.isLinux()) {
            subdir = "linux64";
        } else {
            return null;
        }
        File nativeDir = new File(root, "tools/yara-x/" + subdir);
        return nativeDir.isDirectory() ? nativeDir : null;
    }
}
