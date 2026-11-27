package iped.parsers.util;

import static org.junit.Assume.assumeTrue;

import java.io.File;

/**
 * External tools the parsers run, for the tests. Maven unpacks them into target/tools before the tests run
 * (phase process-test-resources), from the same definitions iped-app releases: see the pluginManagement of the
 * parent pom. The ones that are Windows binaries are only unpacked on Windows.
 */
public class TestTools {

    /** The folder IPED would be installed in: it has the "tools" folder. */
    public static final String ROOT = System.getProperty("user.dir") + "/target";

    /** Path of the tools folder relative to the module folder, starting and ending with a slash. */
    public static final String RELATIVE_DIR = "/target/tools/";

    /**
     * Returns the folder of a tool, ending with a slash. The test is skipped if it is not there, which happens when
     * the tests run without Maven having unpacked the tools (e.g. in an IDE, before any "mvn process-test-resources").
     */
    public static String dir(String tool) {
        File dir = new File(ROOT, "tools/" + tool);
        assumeTrue(dir + " not found: run \"mvn process-test-resources\" to unpack the tools of the tests", dir.isDirectory());
        return dir.getPath() + "/";
    }
}
