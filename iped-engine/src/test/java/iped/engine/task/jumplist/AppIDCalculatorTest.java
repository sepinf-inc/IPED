package iped.engine.task.jumplist;

import static org.junit.Assert.*;
import static iped.engine.task.jumplist.AppIDCalculator.*;

import org.junit.Test;

public class AppIDCalculatorTest {

    @Test
    public void test() {
        assertEquals("660b9b4edee9f30f", calculateAppID("/image.E01/vol_vol3/Program Files (x86)/K-Lite Codec Pack/Media Player Classic/mpc-hc_nvo.exe"));
        assertEquals("baacb5294867b833", calculateAppID("/image.E01/vol_vol3/Program Files/Notepad++/notepad++.exe"));
        assertEquals("47592b67dd97a119", calculateAppID("/image.E01/vol_vol3/Windows/notepad.exe"));
        assertEquals("9b9cdc69c1c24e2b", calculateAppID("/image.E01/vol_vol3/Windows/SysWOW64/notepad.exe"));
    }

}
