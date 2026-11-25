package iped.engine.task.leapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import iped.engine.task.leapp.aleapp.AleappTool;
import iped.engine.task.leapp.interceptors.IlapfuncsEmbeddedMediaInterceptor;
import jep.Jep;
import jep.SharedInterpreter;

/**
 * Exercises the Java &harr; Python side of the LEAPP integration against a real ALEAPP installation, through Jep:
 * what the unit tests of this package cannot reach.
 *
 * It needs Jep and the ALEAPP python requirements, so it is skipped when they are not available. To run it:
 *
 * <pre>
 * mvn -pl iped-engine surefire:test -Dtest=LeappPythonIntegrationTest \
 *     -DargLine="-Djava.library.path=&lt;site-packages&gt;/jep" [-Dleapp.test.toolFolder=&lt;aleapp folder&gt;]
 * </pre>
 *
 * The ALEAPP folder defaults to the one of the release built by maven (target/release/&lt;iped&gt;/tools/aleapp).
 */
public class LeappPythonIntegrationTest {

    private static Jep jep;

    @BeforeClass
    public static void setUp() throws Exception {
        File toolFolder = findToolFolder();
        assumeTrue("ALEAPP folder not found", toolFolder != null);

        try {
            jep = new SharedInterpreter();
            jep.set("_tool_folder", toolFolder.getCanonicalPath());
            jep.exec("import sys");
            jep.exec("sys.path.append(_tool_folder)");
            new LeappInterceptors().install(jep);
        } catch (Throwable e) {
            // Jep or the python requirements are not available in this environment
            jep = null;
            assumeNoException(e);
        }
    }

    private static File findToolFolder() {
        String property = System.getProperty("leapp.test.toolFolder");
        if (StringUtils.isNotBlank(property)) {
            File folder = new File(property);
            return folder.isDirectory() ? folder : null;
        }
        File[] releases = new File("../target/release").listFiles((java.io.FileFilter) DirectoryFileFilter.DIRECTORY);
        if (releases != null) {
            for (File release : releases) {
                File folder = new File(release, "tools/aleapp");
                if (new File(folder, "scripts/ilapfuncs.py").isFile()) {
                    return folder;
                }
            }
        }
        return null;
    }

    @After
    public void clearContext() {
        LeappContext.clear();
    }

    /** A seeker that finds two files for every pattern, recording how it was called. */
    private static class RecordingSeeker extends FileSeeker {

        final List<String> calls = new ArrayList<>();

        RecordingSeeker(Path folder) {
            super("evidence/Dump", folder, null);
        }

        @Override
        public List<String> searchAndExport(String filepattern, boolean returnOnFirstHit) throws IOException {
            calls.add(filepattern + "|" + returnOnFirstHit);
            return returnOnFirstHit ? List.of("/staged/a.db") : List.of("/staged/a.db", "/staged/b.db");
        }
    }

    @Test
    public void testSeekerSearchCallShapes() throws Exception {
        RecordingSeeker seeker = new RecordingSeeker(Files.createTempDirectory("leapp-test"));
        jep.set("_seeker", seeker);

        // positional
        assertEquals(Arrays.asList("/staged/a.db", "/staged/b.db"), jep.getValue("list(_seeker.search('*/a'))"));
        // first hit: a str, not a list, as the ALEAPP seekers return
        assertEquals("/staged/a.db", jep.getValue("_seeker.search('*/b', True)"));
        assertEquals(Boolean.TRUE, jep.getValue("isinstance(_seeker.search('*/b', True), str)"));
        // keyword arguments
        assertEquals("/staged/a.db", jep.getValue("_seeker.search('*/c', return_on_first_hit=True)"));
        assertEquals(Arrays.asList("/staged/a.db", "/staged/b.db"), jep.getValue("list(_seeker.search('*/d', force=True))"));
        assertEquals("/staged/a.db", jep.getValue("_seeker.search(filepattern='*/e', return_on_first_hit=True, force=True)"));

        assertTrue(seeker.calls.containsAll(Arrays.asList("*/a|false", "*/b|true", "*/c|true", "*/d|false", "*/e|true")));

        // attributes plugins read directly
        assertEquals(seeker.data_folder, jep.getValue("_seeker.data_folder"));
        assertEquals(Boolean.TRUE, jep.getValue("_seeker.file_infos.get('/not/staged') is None"));

        jep.exec("del _seeker");
    }

    @Test
    public void testContextIsThreadLocal() throws Exception {
        jep.exec("import scripts.context");
        jep.exec("scripts.context.Context.set_module_name('main-thread-module')");
        jep.exec("scripts.context.Context.set_data_folder('/main/data')");

        AtomicReference<Object> seenByOther = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread other = new Thread(() -> {
            // every IPED worker has an interpreter of its own, all of them sharing the python modules
            try (Jep otherJep = new SharedInterpreter()) {
                otherJep.exec("import scripts.context");
                seenByOther.set(otherJep.getValue("scripts.context.Context.get_data_folder()"));
                otherJep.exec("scripts.context.Context.set_module_name('other-thread-module')");
                otherJep.exec("scripts.context.Context.set_data_folder('/other/data')");
            } catch (Throwable e) {
                error.set(e);
            }
        });
        other.start();
        other.join();

        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        assertEquals(null, seenByOther.get());
        assertEquals("main-thread-module", jep.getValue("scripts.context.Context.get_module_name()"));
        assertEquals("x/y.db", jep.getValue("scripts.context.Context.get_relative_path('/main/data/x/y.db')"));

        jep.exec("scripts.context.Context.clear()");
    }

    @Test
    public void testEmbeddedMediaIsKeptInTheContext() throws Exception {
        LeappContext context = LeappContext.create(AleappTool.INSTANCE, null, null, jep, null, null, List.of());

        jep.exec("import scripts.ilapfuncs");
        String reference = (String) jep.getValue("scripts.ilapfuncs.check_in_embedded_media('db.sqlite', b'\\x89PNG-data', 'avatar.png')");

        assertNotNull(reference);
        assertTrue(reference.startsWith(IlapfuncsEmbeddedMediaInterceptor.REFERENCE_PREFIX));
        LeappContext.EmbeddedMedia media = context.getEmbeddedMedia().get(reference);
        assertNotNull(media);
        assertEquals("avatar.png", media.name);
        assertArrayEquals(new byte[] { (byte) 0x89, 'P', 'N', 'G', '-', 'd', 'a', 't', 'a' }, media.data);

        // the same data gets the same reference, and no data means no media
        assertEquals(reference, jep.getValue("scripts.ilapfuncs.check_in_embedded_media('db.sqlite', b'\\x89PNG-data', name='other.png')"));
        assertEquals(1, context.getEmbeddedMedia().size());
        assertEquals(null, jep.getValue("scripts.ilapfuncs.check_in_embedded_media('db.sqlite', b'')"));
    }

    @Test
    public void testMediaCellsAreNotBlanked() throws Exception {
        jep.exec("import scripts.ilapfuncs");
        // media cells hold extraction paths here, not LAVA references: the validation must let them through
        assertEquals(Arrays.asList(Arrays.asList("row", "/staged/pic.jpg")),
                jep.getValue("[list(r) for r in scripts.ilapfuncs.lava_blank_invalid_media_refs([('row', '/staged/pic.jpg')], [1], 'label')]"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRequirementsCheck() throws Exception {
        LeappInterceptors.execPythonResource(jep, "check_requirements.py");
        jep.exec("_requirements = _iped_leapp_check_requirements(_tool_folder)");
        Map<String, Object> result = (Map<String, Object>) jep.getValue("_requirements");
        jep.exec("del _requirements, _iped_leapp_check_requirements");

        assertTrue(result.get("missing") instanceof List);
        assertTrue(result.get("mismatched") instanceof List);
        String command = (String) result.get("command");
        // the command installs the requirements files of this installation, with a python found on this machine
        assertTrue(command, command.contains("-m pip install -r requirements.txt"));
        assertTrue(command, command.contains((String) jep.getValue("_tool_folder")));
    }
}
