package iped.engine.task.jumplist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class AppIDsConfigTest {

    private static final Path APP_IDS_FILE = Paths.get("../iped-app/resources/config/conf/" + AppIDsConfig.CONFIG_FILE);

    private static AppIDsConfig load(String content) throws IOException {
        Path file = Files.createTempFile("AppIDs", ".txt");
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            AppIDsConfig config = new AppIDsConfig();
            config.processTaskConfig(file);
            return config;
        } finally {
            Files.delete(file);
        }
    }

    @Test
    public void testNormalize() {
        assertEquals("98b0ef1c84088", AppIDsConfig.normalize("00098B0EF1C84088"));
        assertEquals("98b0ef1c84088", AppIDsConfig.normalize("98b0ef1c84088"));
        assertEquals("5d696d521de238c3", AppIDsConfig.normalize("5D696D521DE238C3"));
        assertEquals("", AppIDsConfig.normalize("0000"));
        assertNull(AppIDsConfig.normalize(null));
    }

    @Test
    public void testZeroPaddingIsIgnored() throws IOException {
        Map<String, String> appIDs = load("\"00098B0EF1C84088\"|\"fulDC 6.78\"\n\"12DC1EA8E34B5A6\"|\"Microsoft Paint 6.1\"\n").getConfiguration();
        assertEquals("fulDC 6.78", appIDs.get("98b0ef1c84088"));
        assertEquals("Microsoft Paint 6.1", appIDs.get("12dc1ea8e34b5a6"));
        assertEquals(2, appIDs.size());
    }

    @Test
    public void testFirstLineIsLoaded() throws IOException {
        Map<String, String> appIDs = load("\"560d789a6a42ad5a\"|\"DC++\"\n\"5d696d521de238c3\"|\"Chrome\"\n").getConfiguration();
        assertEquals("DC++", appIDs.get("560d789a6a42ad5a"));
        assertEquals("Chrome", appIDs.get("5d696d521de238c3"));
        assertEquals(2, appIDs.size());
    }

    @Test
    public void testCommentsAndInvalidLines() throws IOException {
        Map<String, String> appIDs = load("# format: \"AppID\"|\"Application name\"\n\n  # other \"comment\"|\"x\"\n"
                + "no quotes here\n\"12dc1ea8e34b5a6\"\n\"1b4dd67f29cb1962\"|\"Windows Explorer\"\r\n").getConfiguration();
        assertEquals("Windows Explorer", appIDs.get("1b4dd67f29cb1962"));
        assertNull(appIDs.get("12dc1ea8e34b5a6"));
        assertEquals(1, appIDs.size());
    }

    @Test
    public void testAppNameIsTrimmed() throws IOException {
        Map<String, String> appIDs = load("\"4C58CF9096EF3EFD\"|\"Kindle for PC 1.24.3 \"\n").getConfiguration();
        assertEquals("Kindle for PC 1.24.3", appIDs.get("4c58cf9096ef3efd"));
    }

    @Test
    public void testLastDuplicateWins() throws IOException {
        Map<String, String> appIDs = load("\"E4EA035065B5789A\"|\"first\"\n\"e4ea035065b5789a\"|\"last\"\n").getConfiguration();
        assertEquals("last", appIDs.get("e4ea035065b5789a"));
        assertEquals(1, appIDs.size());
    }

    /**
     * The shipped list is an unmodified copy of
     * https://github.com/EricZimmerman/JumpList/blob/master/JumpList/Resources/AppIDs.txt
     * so all of its lines must be understood by the loader.
     */
    @Test
    public void testShippedList() throws IOException {
        Pattern linePattern = Pattern.compile("\"([0-9A-Fa-f]{1,16})\"\\|\"([^\"|]+)\"");
        List<String> lines = Files.readAllLines(APP_IDS_FILE, StandardCharsets.UTF_8);
        Set<String> appIDs = new HashSet<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = linePattern.matcher(line);
            assertTrue("Invalid line: " + line, matcher.matches());
            appIDs.add(AppIDsConfig.normalize(matcher.group(1)));
        }
        assertFalse(appIDs.isEmpty());

        AppIDsConfig config = new AppIDsConfig();
        config.processTaskConfig(APP_IDS_FILE);
        assertEquals(appIDs, config.getConfiguration().keySet());

        // AppIDs in upper case, in lower case, without zero padding and zero padded
        assertTrue(appIDs.contains("560d789a6a42ad5a"));
        assertTrue(appIDs.contains("46b106fe160f9bd"));
        assertTrue(appIDs.contains("12dc1ea8e34b5a6"));
        assertTrue(appIDs.contains("98b0ef1c84088"));
    }
}
