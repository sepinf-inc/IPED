package dpf.sp.gpinf.indexer;

import gpinf.dev.data.CaseData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dpf.sp.gpinf.indexer.datasource.IPEDReader;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.parsers.OCRParser;

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
  private static String usage = Versao.APP_NAME + "\n" + "Uso: java -jar iped.jar -opcao  argumento [--opcao_sem_argumento]";

  public static String ALL_ARGS = "ALL_ARGS";

  /**
   * Parâmetros aceitos via linha de comando e respectiva descrição (ajuda). Aqui devem ser
   * cadastrados novos parâmetros de novos módulos.
   */
  private static String[][] params = {
    {"-d", "dados diversos (pode ser usado varias vezes):\n\t"
      + "pasta, imagem DD, 001, E01, AFF (apenas linux), ISO, disco físico,\n\t"
      + "ou arquivo *.iped (contendo seleção de itens a exportar e reindexar)"},
    {"-dname", "nome (opcional) para dados adicionados via -d"},
    {"-o", "pasta de saida da indexacao"},
    {"-r", "pasta do relatorio do AsAP3 ou FTK3"},
    {"-l", "arquivo com lista de expressoes a serem exibidas na busca.\n\t"
      + "Expressoes sem ocorrencias sao filtradas"},
    {"-ocr", "aplica OCR apenas na categoria informada. Pode ser usado varias vezes."},
    {"-log", "Especifica um arquivo de log diferente do padrao"},
    {"-asap", "arquivo .asap (Criminalistica) com informacoes para relatorio HTML"},
    {"-Xxxx", "parâmetros extras de módulos iniciados com -X"},
    {"-nocontent", "não exporta conteúdo de itens do marcador/categoria informado"},
    {"-importkff", "importa diretorio com base de hashes no formato NSRL"},
    {"--append", "adiciona indexação a um indice ja existente"},
    {"--nogui", "nao exibe a janela de progresso da indexacao"},
    {"--nologfile", "imprime as mensagem de log na saida padrao"},
    {"--nopstattachs", "não inclui automaticamente no relatorio anexos de emails de PST/OST"},
    {"--fastmode", "habilita modo de processamento minimo, para rapida execucao em locais de busca"},};

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
      System.out.println(param[0] + ":\t" + param[1]);
    }

    System.exit(1);
  }

  /**
   * Interpreta parâmetros informados via linha de comando.
   */
  void takeArgs(String[] args) {

    if (args.length == 0 || args[0].contains("--help") || args[0].contains("/?") || args[0].contains("-h")) {
      printUsageExit();
    }

    paramMap.put(ALL_ARGS, Arrays.asList(args));

    for (int i = 0; i < args.length; i++) {

      if (!args[i].startsWith("--") && (i + 1 == args.length || args[i + 1].startsWith("-"))) {
        printUsageExit();
      }

      if (!args[i].startsWith("-X")) {
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

      if (args[i].startsWith("--")) {
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

      if (args[i].compareTo("-r") == 0) {
        reportDir = new File(args[i + 1]);
        IndexFiles.getInstance().dataSource.add(reportDir);

      } else if (args[i].compareTo("-d") == 0) {
        dataSource = new File(args[i + 1]);
        IndexFiles.getInstance().dataSource.add(dataSource);

      } else if (args[i].compareTo("-ocr") == 0) {
        OCRParser.bookmarksToOCR.add(args[i + 1]);

      } else if (args[i].compareTo("-l") == 0) {
        IndexFiles.getInstance().palavrasChave = new File(args[i + 1]);

      } else if (args[i].compareTo("-o") == 0) {
        outputDir = new File(args[i + 1]);

      } else if (args[i].compareTo("-log") == 0) {
        IndexFiles.getInstance().logFile = new File(args[i + 1]);

      } else if (args[i].compareTo("-importkff") == 0) {
        IndexFiles.getInstance().importKFF(args[++i]);
        System.exit(0);

      } else if (args[i].compareTo("--nogui") == 0) {
        IndexFiles.getInstance().nogui = true;

      } else if (args[i].compareTo("--nologfile") == 0) {
        IndexFiles.getInstance().nologfile = true;

      } else if (args[i].compareTo("--append") == 0) {
        IndexFiles.getInstance().appendIndex = true;

      }

    }

    if (reportDir == null || !(new File(reportDir, "files")).exists()) {
      if (reportDir == null || !(new File(reportDir, "Report_files/files")).exists()) {
        if (reportDir == null || !(new File(reportDir, "Export")).exists()) {
          if (dataSource == null || (!dataSource.exists() && !SleuthkitReader.isPhysicalDrive(dataSource))) {
            printUsageExit();
          }
        }
      }
    }

    if (outputDir != null && reportDir != null) {
      throw new RuntimeException("Opção -o não deve ser utilizada com relatorios do FTK!");
    }

    if (IndexFiles.getInstance().dataSource.size() > 1) {
      for (File file : IndexFiles.getInstance().dataSource) {
        if (new IPEDReader(null, null, false).isSupported(file)) {
          throw new RuntimeException("Relatórios do IPED não podem ser gerados junto com outras fontes de dados!");
        }
      }
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
      IndexFiles.getInstance().output = new File(dataSource.getParentFile(), "indexador");
    }

    File file = outputDir;
    while (file != null) {
      for (File source : IndexFiles.getInstance().dataSource) {
        try {
          if (file.getCanonicalPath().equals(source.getCanonicalPath())) {
            throw new RuntimeException("Diretório de saída não pode ser igual ou estar dentro da entrada!");
          }

        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      file = file.getParentFile();
    }

  }

}
