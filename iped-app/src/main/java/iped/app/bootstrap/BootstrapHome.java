package iped.app.bootstrap;

import iped.app.home.MainFrame;
import iped.app.processing.Main;

/**
 * Bootstrap class to start the Home Application process with a custom classpath
 * with plugin jars.
 *
 * @author Luís Nassif
 * @author Thiago S. Figueiredo
 *
 */
public class BootstrapHome extends Bootstrap {

    public static void main(String args[]) {
        new BootstrapHome().run(args);
    }

    @Override
    protected boolean isToDecodeArgs() {
        return false;
    }

    @Override
    protected String getDefaultClassPath(Main iped) {
        return iped.getRootPath() + "/iped_home.jar";
    }

    @Override
    protected String getMainClassName() {
        return MainFrame.class.getCanonicalName();
    }

}
