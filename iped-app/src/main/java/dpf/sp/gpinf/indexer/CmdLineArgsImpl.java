package dpf.sp.gpinf.indexer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.CaseData;

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

    @Parameter(names = { "-d", "-data" }, description = "input data (can be used multiple times): "
            + "folder, DD, 001, E01 images (+AFF on Linux), ISO, physical drive, "
            + "or *.iped file (with tagged files to export and reindex)", validateWith = DatasourceExistsValidator.class, order = 0)
    private List<File> datasources;

    @Parameter(names = "-dname", description = "display name (optional) of data added with -d", order = 1)
    private List<String> dname;

    @Parameter(names = { "-o", "-output" }, description = "output folder", order = 2)
    private File outputDir;

    @Parameter(names = { "-r",
            "-report" }, description = "FTK3+ report folder", validateWith = FTKReportValidator.class)
    private File reportDir;

    @Parameter(names = { "-l", "-keywordlist" }, description = "line file with keywords to be imported into case. "
            + "Keywords with no hits are filtered out.", validateWith = FileExistsValidator.class)
    private File palavrasChave;

    @Parameter(names = "-ocr", description = "only run OCR on a specific category or bookmark (can be used multiple times)")
    private List<String> ocr;

    @Parameter(names = "-log", description = "Redirect log to another file")
    private File logFile;

    @Parameter(names = "-asap", validateWith = FileExistsValidator.class, description = ".asap file (Brazilian Federal Police) with case info to be included in html report")
    private File asap;

    @Parameter(names = "-nocontent", description = "do not export to report file contents of a specific category/bookmark, only thumbs and properties")
    private List<String> nocontent;

    @Parameter(names = "-importkff", validateWith = FileExistsValidator.class, description = "import and index hash database in NIST NSRL format")
    private File importkff;

    @Parameter(names = { "-tz", "-timezone" }, description = "original timezone of FAT devices: GMT-3, GMT-4... "
            + "If unspecified, local system timezone is used.")
    private String timezone;

    @Parameter(names = { "-b", "-blocksize" }, description = "sector block size (bytes), must set to 4k sector devices")
    private int blocksize;

    @Parameter(names = { "-p", "-password" }, description = "password for encrypted images/volumes")
    private String password;

    @Parameter(names = "-profile", description = "use a processing profile: forensic, pedo, "
            + "fastmode, blind, triage. More details in manual.")
    private String profile;

    @Parameter(names = "--addowner", description = "index file owner info when processing local folders (slow over network)")
    private boolean addowner;

    @Parameter(names = "--append", description = "add data to be processed to an existent case")
    private boolean appendIndex;

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

    @Parameter(names = { "--help", "-h", "/?" }, help = true, description = "display this help")
    private boolean help;

    @DynamicParameter(names = "-X", description = "used to specify extra module options")
    private Map<String, String> extraParams = new HashMap<>();

    private List<String> allArgs;

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
    public File getReportDir() {
        return reportDir;
    }

    @Override
    public File getPalavrasChave() {
        return palavrasChave;
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
    public File getImportkff() {
        return importkff;
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
    public String getPassword() {
        return password;
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
    public boolean isHelp() {
        return help;
    }

    @Override
    public Map<String, String> getExtraParams() {
        return extraParams;
    }

    @Override
    public String getDataSourceName(File datasource) {
        for (int i = 0; i < allArgs.size(); i++) {
            if ((allArgs.get(i).equals("-d") || allArgs.get(i).equals("-data")) //$NON-NLS-1$ //$NON-NLS-2$
                    && datasource.equals(new File(allArgs.get(i + 1))) && i + 2 < allArgs.size()
                    && allArgs.get(i + 2).equals("-dname")) { //$NON-NLS-1$
                return allArgs.get(i + 3);
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

    public static class FTKReportValidator implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            File reportDir = new File(value);
            if (!(new File(reportDir, "files")).exists() && //$NON-NLS-1$
                    !(new File(reportDir, "Report_files/files")).exists() && //$NON-NLS-1$
                    !(new File(reportDir, "Export")).exists()) { //$NON-NLS-1$
                throw new ParameterException("Invalid FTK report folder!"); //$NON-NLS-1$
            }
        }
    }

    /**
     * Salva os parâmetros no objeto do caso, para serem consultados pelos módulos.
     *
     * @param caseData
     *            caso atual
     */
    public void saveIntoCaseData(CaseData caseData) {
        caseData.putCaseObject(CmdLineArgs.class.getName(), this);
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
                printUsageAndExit(jc, null);

            allArgs = Arrays.asList(args);
            handleSpecificArgs();

        } catch (Exception e) {
            printUsageAndExit(jc, e);
        }
    }

    private void printUsageAndExit(JCommander jc, Exception e) {
        System.out.println(Versao.APP_NAME);
        if (e != null)
            System.out.println("Error: " + e.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        jc.usage();
        System.exit(1);
    }

    /**
     * Trata parâmetros específicos informados. TODO: mover o tratamento de cada
     * parâmetro para a classe que o utiliza e remover esta função.
     *
     * @param args
     *            parâmetros
     */
    private void handleSpecificArgs() {

        IndexFiles.getInstance().dataSource = new ArrayList<File>();

        if (this.importkff != null) {
            IndexFiles.getInstance().importKFF(this.importkff);
            System.exit(0);
        }

        if (reportDir == null && (datasources == null || datasources.isEmpty())) {
            throw new ParameterException("parameter '-d' or '-r' required."); //$NON-NLS-1$
        }

        if (this.reportDir != null) {
            IndexFiles.getInstance().dataSource.add(this.reportDir);
        }
        if (this.datasources != null) {
            for (File dataSource : this.datasources) {
                IndexFiles.getInstance().dataSource.add(dataSource);
            }
        }

        OCRParser.bookmarksToOCR = new ArrayList<String>();
        if (this.ocr != null) {
            OCRParser.bookmarksToOCR.addAll(this.ocr);
        }
        if (this.palavrasChave != null) {
            IndexFiles.getInstance().palavrasChave = this.palavrasChave;
        }
        if (this.logFile != null) {
            IndexFiles.getInstance().logFile = this.logFile;
        }

        IndexFiles.getInstance().nogui = this.nogui;
        IndexFiles.getInstance().nologfile = this.nologfile;
        System.setProperty(LocalConfig.SYS_PROP_APPEND, Boolean.toString(this.appendIndex));

        if (outputDir != null && reportDir != null) {
            throw new ParameterException("Option -o can not be used with FTK reports!"); //$NON-NLS-1$
        }

        if (new File(reportDir, "Report_files/files").exists()) { //$NON-NLS-1$
            IndexFiles.getInstance().dataSource.remove(reportDir);
            IndexFiles.getInstance().dataSource.add(new File(reportDir, "Report_files")); //$NON-NLS-1$
            IndexFiles.getInstance().output = new File(reportDir, "indexador"); //$NON-NLS-1$
        }

        if (outputDir != null) {
            IndexFiles.getInstance().output = new File(outputDir, "indexador"); //$NON-NLS-1$
        } else if (reportDir != null) {
            IndexFiles.getInstance().output = new File(reportDir, "indexador"); //$NON-NLS-1$
        } else {
            IndexFiles.getInstance().output = new File(datasources.get(0).getParentFile(), "indexador"); //$NON-NLS-1$
        }

        File file = outputDir;
        while (file != null) {
            for (File source : IndexFiles.getInstance().dataSource) {
                if (file.getAbsoluteFile().equals(source.getAbsoluteFile())) {
                    throw new ParameterException("Output folder can not be equal or subdir of input!"); //$NON-NLS-1$
                }
            }
            file = file.getParentFile();
        }

        System.setProperty("IPED_OUTPUT_DIR", IndexFiles.getInstance().output.getPath().toString()); //$NON-NLS-1$
        System.setProperty("IPED_IS_PORTABLE", "" + isPortable()); //$NON-NLS-1$ //$NON-NLS-2$

    }

}
