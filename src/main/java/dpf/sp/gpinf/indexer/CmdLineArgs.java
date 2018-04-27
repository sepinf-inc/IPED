package dpf.sp.gpinf.indexer;

import gpinf.dev.data.CaseData;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Classe para leitura dos parâmetros informados via linha de comando. Parâmetros iniciados com 01
 * hífen aceitam 01 valor e com 02 hífens não aceitam valor. Os parâmetros são armazenados no caso,
 * podendo ser obtidos por outros módulos:
 *
 * CmdLineArgs args = (CmdLineArgs)caseData.getCaseObject(CmdLineArgs.class.getName())
 *
 * @author Nassif
 *
 */
public class CmdLineArgs {

  @Parameter(names="-d", description="input data (can be used multiple times): "
      + "folder, DD, 001, E01 images (+AFF on Linux), ISO, physical drive, "
      + "or *.iped file (with tagged files to export and reindex)", validateWith=DatasourceExistsValidator.class, order = 0)
  public List<File> datasources;

  @Parameter(names="-dname", description="display name (optional) of data added with -d", order = 1)
  public List<String> dname;

  @Parameter(names="-o", description="output folder", order = 2)
  public File outputDir;

  @Parameter(names="-r", description="FTK3+ report folder", validateWith=ReportExistsValidator.class)
  public File reportDir;

  @Parameter(names="-l", description="line file with keywords to be imported into case. "
      + "Keywords with no hits are filtered out.", validateWith=FileExistsValidator.class)
  public File palavrasChave;

  @Parameter(names="-ocr", description="only run OCR on a specific category or bookmark (can be used multiple times)")
  public List<String> ocr;

  @Parameter(names="-log", description="Redirect log to another file")
  public File logFile;

  @Parameter(names="-asap", validateWith=FileExistsValidator.class, description=".asap file (Brazilian Federal Police) with case info to be included in html report")
  public File asap;

  @Parameter(names="-nocontent", description="do not export to report file contents of a specific category/bookmark, only thumbs and properties")
  public List<String> nocontent;

  @Parameter(names="-importkff", validateWith=FileExistsValidator.class, description="import and index hash database in NIST NSRL format")
  public File importkff;

  @Parameter(names="-tz", description="original timezone of FAT devices: GMT-3, GMT-4... "
      + "If unspecified, local system timezone is used.")
  public String tz;

  @Parameter(names="-b", description="sector block size (bytes), must set to 4k sector devices")
  public int b;

  @Parameter(names="-profile", description="use a processing profile: forensic, pedo, "
      + "fastmode, blind. More details in manual.")
  public String profile;

  @Parameter(names="--addowner", description="index file owner info when processing local folders (slow over network)")
  public boolean addowner;

  @Parameter(names="--append", description="add data to be processed to an existent case")
  public boolean appendIndex;

  @Parameter(names="--nogui", description="do not open progress windows, text mode processing")
  public boolean nogui;

  @Parameter(names="--nologfile", description="log messages to standard output")
  public boolean nologfile;

  @Parameter(names="--nopstattachs", description="do not export automatically to report PST/OST email attachments")
  public boolean nopstattachs;

  @Parameter(names="--portable", description="use relative references to forensic images, so case can be moved to other machines if the images are on the same volume")
  public boolean portable;

  @Parameter(names = {"--help", "-h", "/?"}, help = true, description="display this help")
  private boolean help;
  
  @DynamicParameter(names = "-X", description = "used to specify extra module options")
  private Map<String, String> extraParams = new HashMap<>();

  public static class FileExistsValidator implements IParameterValidator{
    @Override
    public void validate(String name, String value) throws ParameterException {
      File f = new File(value);
      if (!f.exists()) {
        throw new ParameterException("File not found: " + value);
      }
    }
  }
  
  public static class DatasourceExistsValidator implements IParameterValidator{
      @Override
      public void validate(String name, String value) throws ParameterException {
        File f = new File(value);
        if (!f.exists() && !Util.isPhysicalDrive(f)) {
          throw new ParameterException("File not found: " + value);
        }
      }
    }
  
  public static class ReportExistsValidator implements IParameterValidator{
      @Override
      public void validate(String name, String value) throws ParameterException {
        File reportDir = new File(value);
        if (!(new File(reportDir, "files")).exists() &&
            !(new File(reportDir, "Report_files/files")).exists() &&
            !(new File(reportDir, "Export")).exists()) {
            throw new ParameterException("Invalid FTK report folder!");
        }
      }
    }

  /**
   * Salva os parâmetros no objeto do caso, para serem consultados pelos módulos.
   *
   * @param caseData caso atual
   */
  public void saveIntoCaseData(CaseData caseData) {
    caseData.putCaseObject(CmdLineArgs.class.getName(), this);
  }

  /**
   * Interpreta parâmetros informados via linha de comando.
   */
  void takeArgs(String[] args){
    JCommander jc = new JCommander(this);
    jc.setProgramName("java -jar iped.jar [--no_arg_option] -option");
    try {
      jc.parse(args);
      if(help)
          printUsageAndExit(jc, null);
      
      handleSpecificArgs();
      
    } catch (Exception e) {
        printUsageAndExit(jc, e);
    }
  }
  
  private void printUsageAndExit(JCommander jc, Exception e) {
      System.out.println(Versao.APP_NAME);
      if(e != null)
          System.out.println("Error: "+ e.getMessage() + "\n");
      jc.usage();
      System.exit(1);
  }

  /**
   * Trata parâmetros específicos informados. TODO: mover o tratamento de cada parâmetro para a
   * classe que o utiliza e remover esta função.
   *
   * @param args parâmetros
   */
  private void handleSpecificArgs() {

    IndexFiles.getInstance().dataSource = new ArrayList<File>();
    
    if (this.importkff != null) {
        IndexFiles.getInstance().importKFF(this.importkff);
        System.exit(0);
    }
    
    if (reportDir == null && (datasources == null || datasources.isEmpty())) {
        throw new ParameterException("parameter '-d' or '-r' required.");
    }
    
    if (this.reportDir != null) {
      IndexFiles.getInstance().dataSource.add(this.reportDir);
    }
    if (this.datasources != null) {
      for (File dataSource : this.datasources) {
        IndexFiles.getInstance().dataSource.add(dataSource);
      }
    }
    
    if (this.dname != null) {
      if (this.dname.size() != this.datasources.size()) {
        throw new ParameterException("There must be one '-dname' parameter for each '-d', or none at all.");
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
    IndexFiles.getInstance().appendIndex = this.appendIndex;

    if (outputDir != null && reportDir != null) {
      throw new ParameterException("Option -o can not be used with FTK reports!");
    }

    if (new File(reportDir, "Report_files/files").exists()) {
      IndexFiles.getInstance().dataSource.remove(reportDir);
      IndexFiles.getInstance().dataSource.add(new File(reportDir, "Report_files"));
      IndexFiles.getInstance().output = new File(reportDir, "indexador");
    }

    if (outputDir != null) {
      IndexFiles.getInstance().output = new File(outputDir, "indexador");
    } else if (reportDir != null) {
      IndexFiles.getInstance().output = new File(reportDir, "indexador");
    } else {
      IndexFiles.getInstance().output = new File(datasources.get(0).getParentFile(), "indexador");
    }

    File file = outputDir;
    while (file != null) {
      for (File source : IndexFiles.getInstance().dataSource) {
    	  if (file.getAbsoluteFile().equals(source.getAbsoluteFile())) {
              throw new ParameterException("Output folder can not be equal or subdir of input!");
            }
      }
      file = file.getParentFile();
    }

  }

}
