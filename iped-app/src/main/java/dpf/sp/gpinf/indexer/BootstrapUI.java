package dpf.sp.gpinf.indexer;

import dpf.sp.gpinf.indexer.desktop.AppMain;

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
        return iped.rootPath + "/lib/iped-search-app.jar";
    }

    @Override
    protected String getMainClassName() {
        return AppMain.class.getCanonicalName();
    }

}
