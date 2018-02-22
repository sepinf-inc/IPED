package dpf.sp.gpinf.indexer;

import gpinf.dev.data.CaseData;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.util.IPEDException;
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

  /**
   * Título da ajuda
   */
  private static final String usage = Versao.APP_NAME + "\n" + "Use: java -jar iped.jar -option arg [--no_arg_option]"; //$NON-NLS-1$ //$NON-NLS-2$

  public static final String ALL_ARGS = "ALL_ARGS"; //$NON-NLS-1$
  public static final String ADD_OWNER = "--addowner"; //$NON-NLS-1$

  /**
   * Parâmetros aceitos via linha de comando e respectiva descrição (ajuda). Aqui devem ser
   * cadastrados novos parâmetros de novos módulos.
   */
  private static String[][] params = {
    {"-d", "input data (can be used multiple times):\n\t" //$NON-NLS-1$ //$NON-NLS-2$
      + "folder, DD, 001, E01 images (+AFF on Linux), ISO, physical drive,\n\t" //$NON-NLS-1$
      + "or *.iped file (with tagged files to export and reindex)"}, //$NON-NLS-1$
    {"-dname", "display name (optional) of data added with -d"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-o", "output folder"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-r", "FTK3+ report folder"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-l", "line file with keywords to be imported into case.\n\t" //$NON-NLS-1$ //$NON-NLS-2$
      + "Keywords with no hits are filtered out."}, //$NON-NLS-1$
    {"-ocr", "only run OCR on a specific category or bookmark (can be used multiple times)"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-log", "Redirect log to another file"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-asap", ".asap file (Brazilian Federal Police) with case info to be included in html report"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-Xxxx", "extra module options prefixed with -X"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-nocontent", "do not export to report file contents of a specific category/bookmark, only thumbs and properties"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-importkff", "import and index hash database in NIST NSRL format"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-tz", "original timezone of FAT devices: GMT-3, GMT-4...\n" //$NON-NLS-1$ //$NON-NLS-2$
    		+ "\tIf unspecified, local system timezone is used."}, //$NON-NLS-1$
    {"-b", "sector block size (bytes), must set to 4k sector devices"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"-profile", "use a processing profile: forensic, pedo,\n" //$NON-NLS-1$ //$NON-NLS-2$
    		+ "\t\t fastmode, blind. More details in manual."}, //$NON-NLS-1$
    {ADD_OWNER, "index file owner info when processing local folders (slow over network)"}, //$NON-NLS-1$
    {"--append", "add data to be processed to an existent case"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"--nogui", "do not open progress windows, text mode processing"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"--nologfile", "log messages to standard output"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"--nopstattachs", "do not export automatically to report PST/OST email attachments"}, //$NON-NLS-1$ //$NON-NLS-2$
    {"--portable", "use relative references to forensic images, so case can be moved to other machines if the images are on the same volume"}}; //$NON-NLS-1$ //$NON-NLS-2$

  private Map<String, List<String>> paramMap = new HashMap<String, List<String>>();

  /**
   * @return Mapa com argumentos da linha de comando e seus valores.
   */
  public Map<String, List<String>> getCmdArgs() {
    return paramMap;
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
   * Imprime ajuda e aborta execução.
   */
  private static void printUsageExit() {

    System.out.println(usage);
    for (String[] param : params) {
      System.out.println(param[0] + ":\t" + param[1]); //$NON-NLS-1$
    }

    System.exit(1);
  }

  /**
   * Interpreta parâmetros informados via linha de comando.
   */
  void takeArgs(String[] args) {

    if (args.length == 0 || args[0].contains("--help") || args[0].contains("/?") || args[0].contains("-h")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      printUsageExit();
    }

    paramMap.put(ALL_ARGS, Arrays.asList(args));

    for (int i = 0; i < args.length; i++) {

      if (!args[i].startsWith("--") && (i + 1 == args.length || args[i + 1].startsWith("-"))) { //$NON-NLS-1$ //$NON-NLS-2$
        printUsageExit();
      }

      if (!args[i].startsWith("-X")) { //$NON-NLS-1$
        boolean knownArg = false;
        for (String[] param : params) {
          if (args[i].equals(param[0])) {
            knownArg = true;
            break;
          }
        }
        if (!knownArg) {
          printUsageExit();
        }
      }

      if (args[i].startsWith("--")) { //$NON-NLS-1$
        paramMap.put(args[i], null);
      } else {
        List<String> values = paramMap.get(args[i]);
        if (values == null) {
          values = new ArrayList<String>();
          paramMap.put(args[i], values);
        }
        values.add(args[++i]);
      }
    }

    handleSpecificArgs(args);
  }

  /**
   * Trata parâmetros específicos informados. TODO: mover o tratamento de cada parâmetro para a
   * classe que o utiliza e remover esta função.
   *
   * @param args parâmetros
   */
  private void handleSpecificArgs(String[] args) {

    File reportDir = null, dataSource = null, outputDir = null;
    IndexFiles.getInstance().dataSource = new ArrayList<File>();
    OCRParser.bookmarksToOCR = new ArrayList<String>();

    for (int i = 0; i < args.length; i++) {

      if (args[i].compareTo("-r") == 0) { //$NON-NLS-1$
        reportDir = new File(args[i + 1]);
        IndexFiles.getInstance().dataSource.add(reportDir);

      } else if (args[i].compareTo("-d") == 0) { //$NON-NLS-1$
        dataSource = new File(args[i + 1]);
        IndexFiles.getInstance().dataSource.add(dataSource);

      } else if (args[i].compareTo("-ocr") == 0) { //$NON-NLS-1$
        OCRParser.bookmarksToOCR.add(args[i + 1]);

      } else if (args[i].compareTo("-l") == 0) { //$NON-NLS-1$
        IndexFiles.getInstance().palavrasChave = new File(args[i + 1]);

      } else if (args[i].compareTo("-o") == 0) { //$NON-NLS-1$
        outputDir = new File(args[i + 1]);

      } else if (args[i].compareTo("-log") == 0) { //$NON-NLS-1$
        IndexFiles.getInstance().logFile = new File(args[i + 1]);

      } else if (args[i].compareTo("-importkff") == 0) { //$NON-NLS-1$
        IndexFiles.getInstance().importKFF(args[++i]);
        System.exit(0);

      } else if (args[i].compareTo("--nogui") == 0) { //$NON-NLS-1$
        IndexFiles.getInstance().nogui = true;

      } else if (args[i].compareTo("--nologfile") == 0) { //$NON-NLS-1$
        IndexFiles.getInstance().nologfile = true;

      } else if (args[i].compareTo("--append") == 0) { //$NON-NLS-1$
        IndexFiles.getInstance().appendIndex = true;

      }

    }

    if (reportDir == null || !(new File(reportDir, "files")).exists()) { //$NON-NLS-1$
      if (reportDir == null || !(new File(reportDir, "Report_files/files")).exists()) { //$NON-NLS-1$
        if (reportDir == null || !(new File(reportDir, "Export")).exists()) { //$NON-NLS-1$
          if (dataSource == null || (!dataSource.exists() && !Util.isPhysicalDrive(dataSource))) {
            printUsageExit();
          }
        }
      }
    }

    if (outputDir != null && reportDir != null) {
      throw new RuntimeException("Option -o can not be used with FTK reports!"); //$NON-NLS-1$
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
      IndexFiles.getInstance().output = new File(dataSource.getParentFile(), "indexador"); //$NON-NLS-1$
    }

    File file = outputDir;
    while (file != null) {
      for (File source : IndexFiles.getInstance().dataSource) {
    	  if (file.getAbsoluteFile().equals(source.getAbsoluteFile())) {
              throw new RuntimeException("Output folder can not be equal or subdir of input!"); //$NON-NLS-1$
            }
      }
      file = file.getParentFile();
    }

  }

}
