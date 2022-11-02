package iped.app.bootstrap;

import iped.app.processing.Main;
import iped.app.ui.AppMain;
import iped.app.ui.splash.SplashScreenManager;

public class BootstrapUI extends Bootstrap {

    public static void main(String args[]) {
        new SplashScreenManager().run();
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

}
