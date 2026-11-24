package iped.engine.task.transcript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class WhisperCpuProcessCountTest {

    @Test
    public void limitsModelCopiesOnVirtualSocketTopologies() {
        assertEquals(2, WhisperTranscriptTask.cpuProcessCount(32, 32, "2"));
        assertEquals(1, WhisperTranscriptTask.cpuProcessCount(32, 32, "1"));
    }

    @Test
    public void preservesAutomaticSelectionWithoutAnOverride() {
        assertEquals(2, WhisperTranscriptTask.cpuProcessCount(2, 32, null));
        assertEquals(32, WhisperTranscriptTask.cpuProcessCount(32, 32, null));
    }

    @Test
    public void rejectsInvalidOverridesBeforeLoadingModels() {
        for (String value : new String[] { "0", "-1", "33", "invalid" }) {
            assertThrows(IllegalArgumentException.class,
                    () -> WhisperTranscriptTask.cpuProcessCount(32, 32, value));
        }
    }
}
