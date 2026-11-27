package iped.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppMainTest {

    @Test
    public void testHomeJreFolder() {
        // must match the JRE path of the Windows launchers, set in iped-app/pom.xml
        assertEquals(".iped/jre-25.0.5.1+5", AppMain.getHomeJreFolder("25.0.5.1+5-LTS"));
    }

    @Test
    public void testJreRelease() {
        assertEquals("25.0.5.1+5", AppMain.getJreRelease("25.0.5.1+5-LTS"));
        assertEquals("25.0.5+11", AppMain.getJreRelease("25.0.5+11-LTS"));
        assertEquals("26.0.1+8", AppMain.getJreRelease("26.0.1+8"));
        assertEquals("26-ea+10", AppMain.getJreRelease("26-ea+10-LTS"));
        assertEquals("25.0.5", AppMain.getJreRelease("25.0.5"));
    }

}
