package iped.app.bootstrap;

import iped.app.processing.Main;
import iped.app.ui.AppMain;

public class BootstrapUI extends Bootstrap {

    public static void main(String args[]) {
        new BootstrapUI().run(args);
    }

    @Override
    protected boolean isToDecodeArgs() {
        return false;
    }

    @Override
    protected String getDefaultClassPath(Main iped) {
        return iped.getRootPath() + "/lib/iped-search-app.jar";
    }

    @Override
    protected String getMainClassName() {
        return AppMain.class.getCanonicalName();
    }

    @Override
    protected float getRAMToHeapFactor() {
        return 0.5f;
    }

}
