/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
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

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Date;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.NoOpLog;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.analysis.LetterDigitTokenizer;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.io.FastPipedReader;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.EDBParser;
import dpf.sp.gpinf.indexer.parsers.IndexDatParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.LibpffPSTParser;
import dpf.sp.gpinf.indexer.parsers.PDFOCRTextParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.parsers.RegistryParser;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Classe principal de carregamento e acesso às configurações da aplicação.
 */
public class Configuration {

  public static final String CONFIG_FILE = "IPEDConfig.txt";
  public static final String EXTRA_CONFIG_FILE = "AdvancedConfig.txt";
  public static final String PARSER_CONFIG = "ParserConfig.xml";
  private static final String FASTMODE_CONFIG = "conf/FastModeConfig.txt";

  public static UTF8Properties properties = new UTF8Properties();
  public static File indexTemp, indexerTemp;
  public static int numThreads;
  public static int textSplitSize = 10000000;
  public static int textOverlapSize = 10000;
  public static int timeOut = 180;
  public static boolean forceMerge = true;
  public static String configPath;
  public static Parser errorParser = new RawStringParser(true);
  public static Parser fallBackParser = new RawStringParser(true);
  public static boolean embutirLibreOffice = true;
  public static boolean sortPDFChars = false;
  public static boolean addUnallocated = true;
  public static long unallocatedFragSize = 1024 * 1024 * 1024;
  public static long indexedFragSize = 10 * 1024 * 1024;
  public static String javaTmpDir = System.getProperty("java.io.tmpdir");
  public static boolean indexTempOnSSD = false;
  public static boolean entropyTest = true;
  public static boolean addFatOrphans = true;
  public static long minOrphanSizeToIgnore = -1;
  public static int searchThreads = 1;

  public static void getConfiguration(String configPath) throws Exception {
    getConfiguration(configPath, false);
  }

  /**
   * Configurações a partir do caminho informado.
   */
  public static void getConfiguration(String configPathStr, boolean fastmode) throws Exception {

    // DataSource.testConnection(configPathStr);
    LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", NoOpLog.class.getName());

    Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    configPath = configPathStr;

    System.setProperty("tika.config", configPath + "/conf/" + PARSER_CONFIG);

    if (fastmode) {
      properties.load(new File(configPath + "/" + FASTMODE_CONFIG));
    } else {
      properties.load(new File(configPath + "/" + CONFIG_FILE));
    }

    properties.load(new File(configPath + "/conf/" + EXTRA_CONFIG_FILE));

    String value;

    File newTmp = null, tmp = new File(System.getProperty("java.io.tmpdir"));

    value = properties.getProperty("indexTemp");
    if (value != null) {
      value = value.trim();
    }
    if (indexerTemp == null) {
      if (value != null && !value.equalsIgnoreCase("default")) {
        newTmp = new File(value);
        if (!newTmp.exists() && !newTmp.mkdirs()) {
          LOGGER.info("Não foi possível criar diretório temporário {}", newTmp.getAbsolutePath());
        } else {
          tmp = newTmp;
        }
      }
      indexerTemp = new File(tmp, "indexador-temp" + new Date().getTime());
      if (!indexerTemp.mkdirs()) {
        tmp = new File(System.getProperty("java.io.tmpdir"));
        indexerTemp = new File(tmp, "indexador-temp" + new Date().getTime());
        indexerTemp.mkdirs();
      }
      if (indexerTemp.exists()) {
        System.setProperty("java.io.tmpdir", indexerTemp.getAbsolutePath());
      }
      if (tmp == newTmp) {
        indexTemp = new File(indexerTemp, "index");
      }
    }
    if (indexerTemp != null) {
      indexerTemp.mkdirs();
    }
    ConstantsViewer.indexerTemp = indexerTemp;

    value = properties.getProperty("robustImageReading");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      EvidenceFile.robustImageReading = Boolean.valueOf(value);
    }

    value = properties.getProperty("numThreads");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.equalsIgnoreCase("default")) {
      numThreads = Integer.valueOf(value);
    } else {
      numThreads = Runtime.getRuntime().availableProcessors();
    }

    value = properties.getProperty("forceMerge");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && value.equalsIgnoreCase("false")) {
      forceMerge = false;
    }

    value = properties.getProperty("timeOut");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      timeOut = Integer.valueOf(value);
    }

    ParsingReader.setTextSplitSize(textSplitSize);
    ParsingReader.setTextOverlapSize(textOverlapSize);
    FastPipedReader.setTimeout(timeOut);

    value = properties.getProperty("entropyTest");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      entropyTest = Boolean.valueOf(value);
    }

    value = properties.getProperty("indexUnknownFiles");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !Boolean.valueOf(value)) {
      fallBackParser = new EmptyParser();
    } else {
      fallBackParser = new RawStringParser(entropyTest);
    }

    errorParser = new RawStringParser(entropyTest);

    value = properties.getProperty("minRawStringSize");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      RawStringParser.MIN_SIZE = Integer.valueOf(value);
    }

    value = properties.getProperty("enableOCR");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.ENABLED = Boolean.valueOf(value);
    }

    value = properties.getProperty("OCRLanguage");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.LANGUAGE = value;
    }

    value = properties.getProperty("minFileSize2OCR");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.MIN_SIZE = Long.valueOf(value);
    }

    value = properties.getProperty("maxFileSize2OCR");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.MAX_SIZE = Long.valueOf(value);
    }

    value = properties.getProperty("pageSegMode");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.PAGESEGMODE = value;
    }

    value = properties.getProperty("maxPDFTextSize2OCR");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFOCRTextParser.MAXCHARS2OCR = Integer.valueOf(value);
    }

    value = properties.getProperty("pdfToImgResolution");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFToImage.RESOLUTION = Integer.valueOf(value);
    }

    value = properties.getProperty("pdfToImgLib");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFToImage.PDFLIB = value;
    }
    
    value = properties.getProperty("processImagesInPDFs");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFOCRTextParser.processEmbeddedImages = Boolean.valueOf(value);
    }

    value = properties.getProperty("embutirLibreOffice");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      embutirLibreOffice = Boolean.valueOf(value);
    }

    value = properties.getProperty("sortPDFChars");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      sortPDFChars = Boolean.valueOf(value);
    }

    value = properties.getProperty("extraCharsToIndex");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      LetterDigitTokenizer.load(value);
    }

    value = properties.getProperty("convertCharsToLowerCase");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      LetterDigitTokenizer.convertCharsToLowerCase = Boolean.valueOf(value);
    }

    value = properties.getProperty("addUnallocated");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      addUnallocated = Boolean.valueOf(value);
    }

    value = properties.getProperty("unallocatedFragSize");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      unallocatedFragSize = Long.valueOf(value);
    }
    
    value = properties.getProperty("indexedFragSize");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
    	indexedFragSize = Long.valueOf(value);
    }

    value = properties.getProperty("indexTempOnSSD");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      indexTempOnSSD = Boolean.valueOf(value);
    }

    value = properties.getProperty("addFatOrphans");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      addFatOrphans = Boolean.valueOf(value);
    }

    value = properties.getProperty("minOrphanSizeToIgnore");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      minOrphanSizeToIgnore = Long.valueOf(value);
    }

    value = properties.getProperty("searchThreads");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      searchThreads = Integer.valueOf(value);
    }

    if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {

      value = properties.getProperty("TskLoaddbPath");
      if (value != null) {
        value = value.trim();
      }
      if (value != null && !value.isEmpty()) {
        SleuthkitReader.setTskPath(configPath + "/" + value);
      }

      value = properties.getProperty("TesseractPath");
      if (value != null) {
        value = value.trim();
      }
      if (value != null && !value.isEmpty()) {
        OCRParser.TESSERACTFOLDER = configPath + "/" + value;
      }

      IOUtil.copiaDiretorio(new File(configPath, "lib/libewf"), new File(indexerTemp, "libewf"), true);
      Util.loadNatLibs(new File(indexerTemp, "libewf").getAbsolutePath());

      EDBParser.TOOL_PATH = configPath + "/tools/esedbexport/";
      LibpffPSTParser.TOOL_PATH = configPath + "/tools/pffexport/";
      IndexDatParser.TOOL_PATH = configPath + "/tools/msiecfexport/";
    }

    RegistryParser.TOOL_PATH = configPath + "/tools/regripper/";

  }

}
