package iped.engine.task.jumplist;

import static iped.engine.task.jumplist.JumpListTask.getAppIDFromFileName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class JumpListTaskTest {

    @Test
    public void testAppIDFromJumpListFileName() {
        assertEquals("5d696d521de238c3", getAppIDFromFileName("5d696d521de238c3.automaticDestinations-ms"));
        assertEquals("5d696d521de238c3", getAppIDFromFileName("5d696d521de238c3.customDestinations-ms"));
        // short AppIDs are kept as they are
        assertEquals("12dc1ea8e34b5a6", getAppIDFromFileName("12dc1ea8e34b5a6.automaticDestinations-ms"));
        assertEquals("6f647f9488d7a", getAppIDFromFileName("6f647f9488d7a.customDestinations-ms"));
    }

    @Test
    public void testAppIDIsNormalized() {
        // lower case
        assertEquals("5d696d521de238c3", getAppIDFromFileName("5D696D521DE238C3.automaticDestinations-ms"));
        // no leading zeros
        assertEquals("98b0ef1c84088", getAppIDFromFileName("00098B0EF1C84088.automaticDestinations-ms"));
        assertEquals("12dc1ea8e34b5a6", getAppIDFromFileName("012dc1ea8e34b5a6.customDestinations-ms"));
        assertEquals("5d696d521de238c3", getAppIDFromFileName("05d696d521de238c3.automaticDestinations-ms"));
    }

    @Test
    public void testNotJumpListFileNames() {
        assertNull(getAppIDFromFileName(null));
        assertNull(getAppIDFromFileName(""));
        assertNull(getAppIDFromFileName("5d696d521de238c3"));
        assertNull(getAppIDFromFileName("5d696d521de238c3.lnk"));
        assertNull(getAppIDFromFileName("automaticDestinations-ms"));
        // the suffix is case sensitive
        assertNull(getAppIDFromFileName("5d696d521de238c3.AUTOMATICDESTINATIONS-MS"));
    }

    @Test
    public void testInvalidAppIDsAreRejected() {
        // not hexadecimal
        assertNull(getAppIDFromFileName("copy (1).automaticDestinations-ms"));
        assertNull(getAppIDFromFileName("5d696d521de238c3 (1).automaticDestinations-ms"));
        assertNull(getAppIDFromFileName("5d696d521de238cg.customDestinations-ms"));
        // empty or zero
        assertNull(getAppIDFromFileName(".automaticDestinations-ms"));
        assertNull(getAppIDFromFileName("0000000000000000.automaticDestinations-ms"));
        // longer than a 64-bit number
        assertNull(getAppIDFromFileName("15d696d521de238c3.automaticDestinations-ms"));
    }
}
