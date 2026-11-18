package iped.app.processing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.IParameterSplitter;

import iped.data.ICaseData;
import iped.engine.CmdLineArgs;
import iped.engine.Version;
import iped.engine.config.LocalConfig;
import iped.engine.task.SkipCommitedTask;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.parsers.ocr.OCRParser;
import iped.parsers.whatsapp.WhatsAppParser;

/**
 * Classe para leitura dos parâmetros informados via linha de comando.
 * Parâmetros iniciados com 01 hífen aceitam 01 valor e com 02 hífens não
 * aceitam valor. Os parâmetros são armazenados no caso, podendo ser obtidos por
 * outros módulos:
 *
 * CmdLineArgs args =
 * (CmdLineArgs)caseData.getCaseObject(CmdLineArgs.class.getName())
 *
 * @author Nassif
 *
 */
public class CmdLineArgsImpl implements CmdLineArgs {

    private static class NoSplitter implements IParameterSplitter {
        @Override
        public List<String> split(String value) {
            return Arrays.asList(value);
        }
    }

    @Parameter(names = { "-d", "-data" }, description = "input data (can be used multiple times): "
            + "DD, 001, E01, Ex01, VHD, VHDX, VMDK, Physical Drive, ISO, AFF (on Linux), AD1, UFDR, folder "
            + "or *.iped file (with tagged files to export and reindex)", validateWith = DatasourceExistsValidator.class, order = 0, splitter = NoSplitter.class)
    private List<File> datasources;

    @Parameter(names = "-dname", description = "display name (optional) of data added with -d", order = 1, splitter = NoSplitter.class)
    private List<String> dname;

    @Parameter(names = { "-o", "-output" }, description = "output folder", order = 2)
    private File outputDir;

    @Parameter(names = { "-remove" }, description = "removes the evidence with specified name")
    private String evidenceToRemove;

    @Parameter(names = { "-l", "-keywordlist" }, description = "line file with keywords to be imported into case. "
            + "Keywords with no hits are filtered out.", validateWith = FileExistsValidator.class)
    private File keywords;

    @Parameter(names = "-ocr", description = "only run OCR on a specific category or bookmark (can be used multiple times)", splitter = NoSplitter.class)
    private List<String> ocr;

    @Parameter(names = "-log", description = "Redirect log to another file")
    private File logFile;

    @Parameter(names = "-asap", validateWith = FileExistsValidator.class, description = ".asap file (Brazilian Federal Police) with case info to be included in html report")
    private File asap;

    @Parameter(names = "-nocontent", description = "do not export to report file contents of a specific category/bookmark, only thumbs and properties", splitter = NoSplitter.class)
    private List<String> nocontent;

    @Parameter(names = { "-tz", "-timezone" }, description = "original timezone of FAT devices: GMT-3, GMT-4... "
            + "If unspecified, local system timezone is used.")
    private String timezone;

    @Parameter(names = { "-b", "-blocksize" }, description = "sector block size (bytes), must set to 4k sector devices")
    private int blocksize;

    @Parameter(names = { "-p", "-password" }, description = "password for encrypted images/volumes", splitter = NoSplitter.class)
    private List<String> passwords;

    @Parameter(names = "-profile", description = "use a processing profile: forensic, pedo, "
            + "fastmode, blind, triage. More details in manual.")
    private String profile;

    @Parameter(names = "--addowner", description = "index file owner info when processing local folders (slow over network)")
    private boolean addowner;

    @Parameter(names = "--append", description = "add data to be processed to an existent case")
    private boolean appendIndex;

    @Parameter(names = "--continue", description = "continue a stopped or aborted processing")
    private boolean isContinue;

    @Parameter(names = "--restart", description = "discard last aborted processing and start from beginning")
    private boolean restart;

    @Parameter(names = "--nogui", description = "do not open progress windows, text mode processing")
    private boolean nogui;

    @Parameter(names = "--nologfile", description = "log messages to standard output")
    private boolean nologfile;

    @Parameter(names = "--nopstattachs", description = "do not export automatically to report PST/OST email attachments")
    private boolean nopstattachs;

    public static final String noLinkedItemsOption = "--nolinkeditems";
    @Parameter(names = noLinkedItemsOption, description = "do not export automatically to report items linked to chats")
    private boolean noLinkedItems = false;

    @Parameter(names = "--portable", description = "use relative references to forensic images, so case can be moved to other machines if the images are on the same volume")
    private boolean portable;

    @Parameter(names = "--downloadInternetData", description = "download Internet data to enrich evidence data processing. E.g. media files still available in WhatsApp servers and not found in the evidence")
    private boolean downloadInternetData;
    
    @Parameter(names = { "-splash" }, description = "custom message to be shown in the splash screen")
    private String splashMessage;    

    @Parameter(names = { "--help", "-h", "/?" }, help = true, description = "display this help")
    private boolean help;

    @DynamicParameter(names = "-X", description = "used to specify extra module options")
    private Map<String, String> extraParams = new HashMap<>();

    private List<String> allArgs;

    private HashSet<String> evidenceNames = new HashSet<>();

    @Override
    public boolean isDownloadInternetData() {
        return downloadInternetData;
    }

    @Override
    public List<File> getDatasources() {
        return datasources;
    }

    @Override
    public List<String> getDname() {
        return dname;
    }

    @Override
    public File getOutputDir() {
        return outputDir;
    }

    @Override
    public File getKeywords() {
        return keywords;
    }

    @Override
    public List<String> getOcr() {
        return ocr;
    }

    @Override
    public File getLogFile() {
        return logFile;
    }

    @Override
    public File getAsap() {
        return asap;
    }

    @Override
    public List<String> getNocontent() {
        return nocontent;
    }

    @Override
    public String getTimezone() {
        return timezone;
    }

    @Override
    public int getBlocksize() {
        return blocksize;
    }

    @Override
    public List<String> getPasswords() {
        return passwords;
    }

    @Override
    public String getProfile() {
        return profile;
    }

    @Override
    public boolean isAddowner() {
        return addowner;
    }

    @Override
    public boolean isAppendIndex() {
        return appendIndex;
    }

    @Override
    public boolean isContinue() {
        return isContinue;
    }

    @Override
    public boolean isRestart() {
        return restart;
    }

    @Override
    public boolean isNogui() {
        return nogui;
    }

    @Override
    public boolean isNologfile() {
        return nologfile;
    }

    @Override
    public boolean isNopstattachs() {
        return nopstattachs;
    }

    @Override
    public boolean isNoLinkedItems() {
        return noLinkedItems;
    }

    @Override
    public boolean isPortable() {
        return portable;
    }

    @Override
    public String getSplashMessage() {
        return splashMessage;
    }
    
    @Override
    public boolean isHelp() {
        return help;
    }

    @Override
    public Map<String, String> getExtraParams() {
        return extraParams;
    }

    public String getEvidenceToRemove() {
        return evidenceToRemove;
    }

    @Override
    public String getDataSourceName(File datasource) {
        boolean isEvidenceParam = false;
        for (int i = 0; i < allArgs.size(); i++) {
            if (datasource.equals(new File(allArgs.get(i)))) {
                isEvidenceParam = true;
            }
            if (allArgs.get(i).equals("-d") || allArgs.get(i).equals("-data")) {
                isEvidenceParam = false;
            }
            if (isEvidenceParam && allArgs.get(i).equals("-dname")) {
                return allArgs.get(i + 1);
            }
        }
        return datasource.getName();
    }

    @Override
    public String getDataSourcePassword(File datasource) {
        boolean isEvidenceParam = false;
        for (int i = 0; i < allArgs.size(); i++) {
            if (datasource.equals(new File(allArgs.get(i)))) {
                isEvidenceParam = true;
            }
            if (allArgs.get(i).equals("-d") || allArgs.get(i).equals("-data")) {
                isEvidenceParam = false;
            }
            if (isEvidenceParam && (allArgs.get(i).equals("-p") || allArgs.get(i).equals("-password"))) {
                return allArgs.get(i + 1);
            }
        }
        return null;
    }

    public static class FileExistsValidator implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            File f = new File(value);
            if (!f.exists()) {
                throw new ParameterException("File not found: " + value); //$NON-NLS-1$
            }
        }
    }

    public static class DatasourceExistsValidator implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            File f = new File(value);
            if (!f.exists() && !Util.isPhysicalDrive(f)) {
                throw new ParameterException("File not found: " + value); //$NON-NLS-1$
            }
        }
    }

    /**
     * Salva os parâmetros no objeto do caso, para serem consultados pelos módulos.
     *
     * @param caseData
     *            caso atual
     */
    public void saveIntoCaseData(ICaseData caseData) {
        caseData.putCaseObject(CmdLineArgs.class.getName(), this);
        caseData.putCaseObject(SkipCommitedTask.DATASOURCE_NAMES, evidenceNames);
    }

    /**
     * Interpreta parâmetros informados via linha de comando.
     */
    public void takeArgs(String[] args) {
        JCommander jc = new JCommander(this);
        jc.setProgramName("java -jar iped.jar [--no_arg_option] -option"); //$NON-NLS-1$
        try {
            jc.parse(args);
            if (help)
                printUsageAndExit(jc);

            allArgs = Arrays.asList(args);
            handleSpecificArgs();
            checkIfAppendingToCompatibleCase();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
            System.exit(1);
        }
    }

    private void checkIfAppendingToCompatibleCase() {
        if (this.isAppendIndex()) {
            String classpath = outputDir.getAbsolutePath() + "/iped/lib/iped-search-app.jar"; //$NON-NLS-1$
            List<String> cmd = new ArrayList<>();
            cmd.addAll(Arrays.asList("java", "-cp", classpath, Main.class.getCanonicalName(), "-h"));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            String line;
            try {
                Process process = pb.start();
                line = IOUtils.readLines(process.getInputStream(), Charset.defaultCharset()).get(0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String thisVersion = Version.APP_VERSION.substring(0, Version.APP_VERSION.lastIndexOf('.'));
            String fullVersion = line.replace(Version.APP_NAME_PREFIX, "").trim();
            String version = fullVersion.substring(0, fullVersion.lastIndexOf('.'));
            if (!version.equals(thisVersion)) {
                throw new IPEDException("Appending to case with old version " + fullVersion + " not supported.");
            }
        }
    }

    private void checkDuplicateDataSources() {
        for (File source : datasources) {
            String name = getDataSourceName(source);
            if (!evidenceNames.add(name)) {
                throw new ParameterException("Duplicate evidence names not allowed: " + name);
            }
        }
    }

    private void printUsageAndExit(JCommander jc) {
        System.out.println(Version.APP_NAME);
        jc.usage();
        System.exit(0);
    }

    /**
     * Trata parâmetros específicos informados. TODO: mover o tratamento de cada
     * parâmetro para a classe que o utiliza e remover esta função.
     *
     * @param args
     *            parâmetros
     */
    private void handleSpecificArgs() {

        Main.getInstance().dataSource = new ArrayList<File>();

        if ((datasources == null || datasources.isEmpty()) && evidenceToRemove == null) {
            throw new ParameterException("parameter '-d' or '-r' required."); //$NON-NLS-1$
        }

        if (evidenceToRemove != null) {
            this.nogui = true;
        }

        if (this.datasources != null) {
            for (File dataSource : this.datasources) {
                Main.getInstance().dataSource.add(dataSource);
            }
            checkDuplicateDataSources();
        }

        if (downloadInternetData) {
            System.setProperty(WhatsAppParser.DOWNLOAD_MEDIA_FILES_PROP, "true");
        }

        if (this.ocr != null) {
            String list = "";
            for (String o : ocr)
                list += (o + OCRParser.SUBSET_SEPARATOR);
            System.setProperty(OCRParser.SUBSET_TO_OCR, list);
        }
        if (this.keywords != null) {
            Main.getInstance().keywords = this.keywords;
        }
        if (this.logFile != null) {
            Main.getInstance().logFile = this.logFile;
        }

        if (outputDir == null) {
            outputDir = datasources.get(0).getParentFile();
        }
        Main.getInstance().output = new File(outputDir, "iped");

        File file = outputDir;
        while (file != null) {
            for (File source : Main.getInstance().dataSource) {
                if (file.getAbsoluteFile().equals(source.getAbsoluteFile())) {
                    throw new ParameterException("The output folder can not be equal or a subfolder of an input!");
                }
            }
            file = file.getParentFile();
        }

        if ((appendIndex || isContinue || restart) && !(new File(outputDir, "iped").exists())) {
            throw new IPEDException(
                    "You cannot use --append, --continue or --restart with an inexistent or invalid case folder.");
        }

        System.setProperty(LocalConfig.SYS_PROP_APPEND, Boolean.toString(this.appendIndex));

    }

}
