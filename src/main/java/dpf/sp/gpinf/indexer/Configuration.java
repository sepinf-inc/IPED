/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.Properties;

import org.apache.tika.parser.EmptyParser;

import dpf.sp.gpinf.indexer.analysis.LowerCaseLetterDigitTokenizer;
import dpf.sp.gpinf.indexer.io.FastPipedReader;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.PDFOCRTextParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;
import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.search.GalleryModel;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.PDFToImage;

/*
 * Classe principal de carregamento e acesso às configuraçõs da aplicação.
 */
public class Configuration {

	public static String CONFIG_FILE = "IndexerConfig.txt";
	public static String PARSER_CONFIG = "ParserConfig.xml";
	public static String CATEGORIES_BY_TYPE = "CategoriesByTypeConfig.txt";
	public static String CATEGORIES_BY_PROPS = "CategoriesByPropsConfig.txt";
	public static String EXPAND_CONFIG = "CategoriesToExpand.txt";
	public static String EXTRACT_CONFIG = "CategoriesToExport.txt";
	public static String CARVE_CONFIG = "CarvingConfig.txt";

	public static Properties properties = new Properties();
	public static File indexTemp, indexerTemp;
	public static int numThreads;
	public static int textSplitSize = 10000000;
	public static int textOverlapSize = 10000;
	public static int timeOut = 180;
	public static boolean forceMerge = true;
	public static String configPath;
	public static Class<?> errorParser = RawStringParser.class;
	public static Class<?> fallBackParser = RawStringParser.class;
	public static boolean embutirLibreOffice = true;
	public static String hashAlgorithm;
	public static String defaultCategory = "";
	public static boolean sortPDFChars = false;
	public static boolean exportFileProps = false;
	public static boolean indexFileContents = true;
	public static boolean processFileSignatures = true;
	public static boolean addUnallocated = true;
	public static boolean indexUnallocated = false;
	public static boolean ignoreDuplicates = false;
	public static long unallocatedFragSize = 100 * 1024 * 1024;
	public static String javaTmpDir = System.getProperty("java.io.tmpdir");
	public static boolean expandContainers = false;
	public static boolean enableCarving = false;

	/*
	 * Lê as configurações a partir do caminho informado.
	 */
	public static void getConfiguration(String configPathStr) throws Exception {

		// DataSource.testConnection(configPathStr);

		configPath = configPathStr;

		if (new File(configPath + "/lib/sleuth").exists())
			IOUtil.loadNatLibs(configPath + "/lib/sleuth");
		else
			IOUtil.loadNatLibs(configPath + "/../lib/sleuth");

		if (new File(configPath + "/" + PARSER_CONFIG).exists())
			System.setProperty("tika.config", configPath + "/" + PARSER_CONFIG);
		else
			System.setProperty("tika.config", configPath + "/conf/" + PARSER_CONFIG);

		if (new File(configPath + "/conf/" + CATEGORIES_BY_TYPE).exists())
			SetCategoryTask.load(new File(configPath + "/conf/" + CATEGORIES_BY_TYPE));

		if (new File(configPath + "/conf/" + CATEGORIES_BY_PROPS).exists())
			SetCategoryTask.loadScript(new File(configPath + "/conf/" + CATEGORIES_BY_PROPS));

		if (new File(configPath + "/conf/" + EXTRACT_CONFIG).exists())
			ExportFileTask.load(new File(configPath + "/conf/" + EXTRACT_CONFIG));

		if (new File(configPath + "/conf/" + CARVE_CONFIG).exists())
			CarveTask.loadConfigFile(new File(configPath + "/conf/" + CARVE_CONFIG));

		if (new File(configPath + "/conf/" + EXPAND_CONFIG).exists())
			ExpandContainerTask.load(new File(configPath + "/conf/" + EXPAND_CONFIG));
		else
			ExpandContainerTask.load(new File(configPath + "/" + EXPAND_CONFIG));

		properties.load(new FileInputStream(new File(configPath + "/" + CONFIG_FILE)));

		String value;

		File newTmp = null, tmp = new File(System.getProperty("java.io.tmpdir"));
		// File tmp = new File(configPath + "/tmp");
		value = properties.getProperty("indexTemp");
		if (value != null)
			value = value.trim();
		if (indexerTemp == null) {
			if (value != null && !value.equalsIgnoreCase("default")) {
				newTmp = new File(value);
				if (!newTmp.exists() && !newTmp.mkdirs())
					System.out.println(new Date() + "\t[INFO]\t" + "Não foi possível criar diretório temporário " + newTmp.getAbsolutePath());
				else
					tmp = newTmp;
			}
			indexerTemp = new File(tmp, "indexador-temp" + new Date().getTime());
			System.setProperty("java.io.tmpdir", indexerTemp.getAbsolutePath());
			if (tmp == newTmp)
				indexTemp = new File(indexerTemp, "index");
		}
		if (indexerTemp != null)
			indexerTemp.mkdirs();

		value = properties.getProperty("numThreads");
		if (value != null)
			value = value.trim();
		if (value != null && !value.equalsIgnoreCase("default"))
			numThreads = Integer.valueOf(value);
		else
			numThreads = Runtime.getRuntime().availableProcessors();

		value = properties.getProperty("forceMerge");
		if (value != null)
			value = value.trim();
		if (value != null && value.equalsIgnoreCase("false"))
			forceMerge = false;

		value = properties.getProperty("textSplitSize");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			textSplitSize = Integer.valueOf(value);

		value = properties.getProperty("textOverlapSize");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			textOverlapSize = Integer.valueOf(value);

		value = properties.getProperty("timeOut");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			timeOut = Integer.valueOf(value);

		ParsingReader.setTextSplitSize(textSplitSize);
		ParsingReader.setTextOverlapSize(textOverlapSize);
		FastPipedReader.setTimeout(timeOut);

		value = properties.getProperty("fallBackParser");
		if (value != null)
			value = value.trim();
		if (value != null) {
			if (value.isEmpty())
				fallBackParser = EmptyParser.class;
			else
				fallBackParser = Class.forName(value);
		}

		value = properties.getProperty("errorParser");
		if (value != null)
			value = value.trim();
		if (value != null) {
			if (value.isEmpty())
				errorParser = EmptyParser.class;
			else
				errorParser = Class.forName(value);
		}

		value = properties.getProperty("minRawStringSize");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			RawStringParser.MIN_SIZE = Integer.valueOf(value);

		value = properties.getProperty("TesseractPath");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			OCRParser.TESSERACTPATH = configPath + "/" + value + "/";

		value = properties.getProperty("enableOCR");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			OCRParser.ENABLED = Boolean.valueOf(value);

		value = properties.getProperty("OCRLanguage");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			OCRParser.LANGUAGE = value;

		value = properties.getProperty("minFileSize2OCR");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			OCRParser.MIN_SIZE = Long.valueOf(value);

		value = properties.getProperty("maxFileSize2OCR");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			OCRParser.MAX_SIZE = Long.valueOf(value);

		value = properties.getProperty("pageSegMode");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			OCRParser.PAGESEGMODE = value;

		value = properties.getProperty("maxPDFTextSize2OCR");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			PDFOCRTextParser.MAXCHARS2OCR = Integer.valueOf(value);

		value = properties.getProperty("pdfToImgResolution");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			PDFToImage.RESOLUTION = Integer.valueOf(value);

		value = properties.getProperty("embutirLibreOffice");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			embutirLibreOffice = Boolean.valueOf(value);

		value = properties.getProperty("defaultCategory");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			defaultCategory = value;

		value = properties.getProperty("sortPDFChars");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			sortPDFChars = Boolean.valueOf(value);

		value = properties.getProperty("hash");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			hashAlgorithm = value;

		value = properties.getProperty("exportFileProps");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			exportFileProps = Boolean.valueOf(value);

		value = properties.getProperty("indexFileContents");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			indexFileContents = Boolean.valueOf(value);

		value = properties.getProperty("processFileSignatures");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			processFileSignatures = Boolean.valueOf(value);

		value = properties.getProperty("extraCharsToIndex");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			LowerCaseLetterDigitTokenizer.load(value);

		value = properties.getProperty("addUnallocated");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			addUnallocated = Boolean.valueOf(value);

		value = properties.getProperty("indexUnallocated");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			indexUnallocated = Boolean.valueOf(value);

		value = properties.getProperty("unallocatedFragSize");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			unallocatedFragSize = Long.valueOf(value);

		value = properties.getProperty("ignoreDuplicates");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			ignoreDuplicates = Boolean.valueOf(value);

		value = properties.getProperty("useGM");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			GraphicsMagicConverter.USE_GM = Boolean.valueOf(value);

		value = properties.getProperty("imgConvTimeout");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			GraphicsMagicConverter.TIMEOUT = Integer.valueOf(value);

		Runtime.getRuntime().availableProcessors();
		value = properties.getProperty("galleryThreads");
		if (value != null)
			value = value.trim();
		if (value != null && !value.equalsIgnoreCase("default"))
			GalleryModel.GALLERY_THREADS = Integer.valueOf(value);
		else
			GalleryModel.GALLERY_THREADS = Runtime.getRuntime().availableProcessors();
		
		value = properties.getProperty("enableCarving");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			enableCarving = Boolean.valueOf(value);
		
		value = properties.getProperty("expandContainers");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			expandContainers = Boolean.valueOf(value);
		

	}

}
