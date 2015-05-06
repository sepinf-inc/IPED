package dpf.sp.gpinf.indexer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import dpf.sp.gpinf.indexer.datasource.SleuthkitProcessor;
import dpf.sp.gpinf.indexer.parsers.OCRParser;

public class CmdLineArgs {
	
	/** Título da ajuda */
	private static String usage = Versao.APP_NAME + "\n" + "Uso: java -jar indexer.jar -opcao  argumento [--opcao_sem_argumento]";
	
	/**
	 * Parâmetros aceitos via linha de comando e respectiva descrição (ajuda).
	 * Parâmetros iniciados com 01 hífen aceitam 01 valor.
	 * Parâmetros iniciados com 02 hífens não aceitam valor.
	 */
	private static String[][] params = {
		{"-d"			, "dados diversos (pode ser usado varias vezes):\n\t"
						+ "pasta, imagem DD, 001, E01, AFF (apenas linux), ISO, disco físico,\n\t"
						+ "ou arquivo *.iped (contendo seleção de itens a exportar e reindexar)"},
		{"-o"			, "pasta de saida da indexacao"},
		{"-r"			, "pasta do relatorio do AsAP3 ou FTK3"},
		{"-c"			, "nome do caso, necessário apenas para relatorio do FTK3"},
		{"-l"			, "arquivo com lista de expressoes a serem exibidas na busca.\n\t"
						+ "Expressoes sem ocorrencias sao filtradas"},
		{"-ocr"			, "aplica OCR apenas na categoria informada. Pode ser usado varias vezes."},
		{"-log"			, "Especifica um arquivo de log diferente do padrao"},
		{"-importkff"	, "importa diretorio com base de hashes no formato NSRL"},
		{"-Xxxx"		, "parâmetros extras de módulos iniciados com -X"},
		{"--append"		, "adiciona indexação a um indice ja existente"},
		{"--nogui"		, "nao exibe a janela de progresso da indexacao"},
		{"--nologfile"	, "imprime as mensagem de log na saida padrao"},
		{"--verbose"	, "gera mensagens de log detalhadas, porem diminui desempenho"},
	};
	
	private Map<String, String> paramMap = new HashMap<String, String>();
	
	/**
	 * @return Mapa com argumentos da linha de comando e seus valores.
	 */
	public Map<String, String> getCmdArgs() {
		return paramMap;
	}

	/**
	 * Imprime ajuda e aborta execução.
	 */
	private static void printUsageExit() {
		   
		System.out.println(usage);
		for(String[] param : params)
			System.out.println(param[0] + ":\t" + param[1]);
		
		System.exit(1);
	}
	
	/**
	 * Interpreta parâmetros informados via linha de comando.
	 */
	void takeArgs(String[] args) {
		
		if (args.length == 0 || args[0].contains("--help") || args[0].contains("/?") || args[0].contains("-h"))
			printUsageExit();

		File reportDir = null, dataSource = null, outputDir = null;
		IndexFiles.getInstance().dataSource = new ArrayList<File>();
		IndexFiles.getInstance().caseNames = new ArrayList<String>();
		OCRParser.bookmarksToOCR = new ArrayList<String>();

		for (int i = 0; i < args.length; i++) {
			
			if(!args[i].startsWith("--") && i + 1 == args.length){
				printUsageExit();
				
			} else if (args[i].compareTo("-r") == 0) {
				reportDir = new File(args[i + 1]);
				IndexFiles.getInstance().dataSource.add(reportDir);
				
			} else if (args[i].compareTo("-d") == 0) {
				dataSource = new File(args[i + 1]);
				IndexFiles.getInstance().dataSource.add(dataSource);
				
			} else if (args[i].compareTo("-c") == 0) {
				IndexFiles.getInstance().caseNames.add(args[i + 1]);
				
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
				
			} else if (args[i].compareTo("--verbose") == 0) {
				IndexFiles.getInstance().verbose = true;
				
			} else if (args[i].compareTo("--append") == 0) {
				IndexFiles.getInstance().appendIndex = true;
				    
            } else if (!args[i].startsWith("-X"))
            	printUsageExit();
			
			if(args[i].startsWith("--"))
				paramMap.put(args[i], null);
			else
				paramMap.put(args[i], args[++i]);
		}

		if (reportDir == null || !(new File(reportDir, "files")).exists()) {
			if (reportDir == null || !(new File(reportDir, "Export")).exists())
				if (dataSource == null || (!dataSource.exists() && !SleuthkitProcessor.isPhysicalDrive(dataSource)))
					printUsageExit();
			
		} else if (IndexFiles.getInstance().caseNames.size() == 0)
			printUsageExit();

		if (outputDir != null)
			IndexFiles.getInstance().output = new File(outputDir, "indexador");
		else if (reportDir != null)
			IndexFiles.getInstance().output = new File(reportDir, "indexador");
		else
			IndexFiles.getInstance().output = new File(dataSource.getParentFile(), "indexador");

	}
	
}
