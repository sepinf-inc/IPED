package dpf.sp.gpinf.indexer;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface CmdLineArgs {

    List<File> getDatasources();

    List<String> getDname();

    File getOutputDir();

    File getReportDir();

    File getPalavrasChave();

    List<String> getOcr();

    File getLogFile();

    File getAsap();

    List<String> getNocontent();

    File getImportkff();

    String getTimezone();

    int getBlocksize();

    String getPassword();

    String getProfile();

    boolean isAddowner();

    boolean isAppendIndex();
    
    boolean isContinue();
    
    boolean isRestart();

    boolean isNogui();

    boolean isNologfile();

    boolean isNopstattachs();

    boolean isNoLinkedItems();

    boolean isPortable();

    boolean isHelp();

    Map<String, String> getExtraParams();

    String getDataSourceName(File datasource);

}