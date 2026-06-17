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

    boolean isPortable();
    
    String getSplashMessage();

    boolean isHelp();

    Map<String, String> getExtraParams();

    String getDataSourceName(File datasource);

    String getDataSourcePassword(File datasource);

    public String getEvidenceToRemove();

    boolean isDownloadInternetData();

    /**
     * Quando {@code true}, o IPED roda apenas o pipeline YARA-X sobre um caso
     * já processado (sem ingerir nova evidência), atualizando os campos
     * {@code yara:rule}/{@code yara:tag}/{@code yara:matches} no índice
     * Lucene existente. Ver {@code specs/001-yara-rules-engine/research.md}
     * §R-08 e {@code contracts/cli-yara-only.contract.md}.
     *
     * <p>Default {@code false} (modo padrão de processamento). O método é
     * {@code default} para preservar compatibilidade com implementações
     * existentes de {@code CmdLineArgs}.</p>
     */
    default boolean isYaraOnly() {
        return false;
    }

}