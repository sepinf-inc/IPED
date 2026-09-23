package iped.engine;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface CmdLineArgs {

    List<File> getDatasources();

    List<String> getDname();

    File getOutputDir();

    File getKeywords();

    List<String> getOcr();

    File getLogFile();

    File getAsap();

    List<String> getNocontent();

    String getTimezone();

    int getBlocksize();

    List<String> getPasswords();

    String getProfile();

    boolean isAddowner();

    boolean isAppendIndex();

    boolean isContinue();

    boolean isRestart();

    boolean isNogui();

    boolean isNologfile();

    boolean isNopstattachs();

    boolean isNoLinkedItems();

    boolean isPdfReport();

    boolean isPortable();
    
    String getSplashMessage();

    boolean isHelp();

    Map<String, String> getExtraParams();

    String getDataSourceName(File datasource);

    String getDataSourcePassword(File datasource);

    public String getEvidenceToRemove();

    boolean isDownloadInternetData();

}