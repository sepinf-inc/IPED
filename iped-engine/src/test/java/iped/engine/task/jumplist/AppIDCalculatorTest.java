package iped.engine.task.jumplist;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import static iped.engine.task.jumplist.AppIDCalculator.*;

import org.junit.Test;

public class AppIDCalculatorTest {

    @Test
    public void test() {
        assertThat(calculateAppIDs("/image.E01/vol_vol3/Program Files (x86)/K-Lite Codec Pack/Media Player Classic/mpc-hc_nvo.exe"), hasItem("660b9b4edee9f30f"));
        assertThat(calculateAppIDs("/image.E01/vol_vol3/Program Files/Notepad++/notepad++.exe"),  hasItem("baacb5294867b833"));
        assertThat(calculateAppIDs("/image.E01/vol_vol3/Windows/notepad.exe"),  hasItem("47592b67dd97a119"));
        assertThat(calculateAppIDs("/image.E01/vol_vol3/Windows/System32/notepad.exe"),  hasItem("9b9cdc69c1c24e2b"));
    }
}
