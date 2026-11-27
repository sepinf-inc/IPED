package iped.utils;

import java.io.File;

/**
 * Locates the java launcher of the running JVM, so every JVM started by IPED
 * (processing/analysis child process, sleuthkit server, external parsers...)
 * uses the same Java runtime instead of whatever "java" is found in the PATH,
 * which could be an older and incompatible version.
 */
public final class JavaExecutable {

    private static final String PATH = findPath();

    private JavaExecutable() {
    }

    /**
     * @return the absolute path of the java launcher of the running JVM, or just
     *         "java" (resolved by the OS using the PATH) if it can not be found.
     */
    public static String getPath() {
        return PATH;
    }

    private static String findPath() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
            File exe = new File(javaHome, isWindows ? "bin/java.exe" : "bin/java");
            if (exe.isFile()) {
                return exe.getAbsolutePath();
            }
        }
        return "java";
    }

}
