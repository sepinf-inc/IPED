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

import java.io.File;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.NoOpLog;
import org.apache.tika.mime.CustomDetector;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.analysis.LetterDigitTokenizer;
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
import dpf.sp.gpinf.indexer.process.task.VideoThumbTask;
import dpf.sp.gpinf.indexer.util.CustomLoader.CustomURLClassLoader;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Classe principal de carregamento e acesso às configurações da aplicação.
 */
public class Configuration {

  public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$
  public static final String LOCAL_CONFIG = "LocalConfig.txt"; //$NON-NLS-1$
  public static final String EXTRA_CONFIG_FILE = "AdvancedConfig.txt"; //$NON-NLS-1$
  public static final String PARSER_CONFIG = "ParserConfig.xml"; //$NON-NLS-1$
  public static final String CUSTOM_MIMES_CONFIG = "CustomSignatures.xml"; //$NON-NLS-1$

  public static UTF8Properties properties = new UTF8Properties();
  public static File indexTemp, indexerTemp;
  public static int numThreads;
  public static int textSplitSize = 100000000;
  public static int textOverlapSize = 10000;
  public static int timeOut = 180;
  public static int timeOutPerMB = 1;
  public static boolean forceMerge = true;
  public static String configPath, appRoot;
  public static Parser errorParser = new RawStringParser(true);
  public static Parser fallBackParser = new RawStringParser(true);
  public static boolean embutirLibreOffice = true;
  public static boolean addUnallocated = false;
  public static boolean addFileSlacks = false;
  public static long unallocatedFragSize = 1024 * 1024 * 1024;
  public static long minItemSizeToFragment = 100 * 1024 * 1024;
  public static boolean indexTempOnSSD = false;
  public static boolean outputOnSSD = false;
  public static boolean entropyTest = true;
  public static boolean addFatOrphans = true;
  public static long minOrphanSizeToIgnore = -1;
  public static int searchThreads = 1;
  public static boolean robustImageReading = false;
  public static File optionalJarDir;
  public static File tskJarFile;
  public static String loaddbPathWin;
  public static Locale locale = Locale.getDefault();
  
  private static AtomicBoolean loaded = new AtomicBoolean();
  
  private static String getAppRoot(String configPath){
	  String appRoot = new File(configPath).getAbsolutePath();
	  if(appRoot.contains("profiles")) //$NON-NLS-1$
	   	appRoot = new File(appRoot).getParentFile().getParentFile().getParent();
	  return appRoot;
  }

  /**
   * Configurações a partir do caminho informado.
   */
  public static void getConfiguration(String configPathStr) throws Exception {
	  
	  if(loaded.getAndSet(true))
		  return;

    // DataSource.testConnection(configPathStr);
    LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", NoOpLog.class.getName()); //$NON-NLS-1$
    
    Logger LOGGER = null;
    if(Configuration.class.getClassLoader().getClass().getName()
            .equals(CustomURLClassLoader.class.getName()))
        LOGGER = LoggerFactory.getLogger(Configuration.class);
    
    if(LOGGER != null) LOGGER.info("Loading configuration from " + configPathStr); //$NON-NLS-1$

    configPath = configPathStr;
    appRoot = getAppRoot(configPath);

    System.setProperty("tika.config", configPath + "/conf/" + PARSER_CONFIG); //$NON-NLS-1$ //$NON-NLS-2$
    System.setProperty(CustomDetector.CUSTOM_MIMES_SYS_PROP, appRoot + "/conf/" + Configuration.CUSTOM_MIMES_CONFIG); //$NON-NLS-1$

    properties.load(new File(appRoot + "/" + LOCAL_CONFIG)); //$NON-NLS-1$
    properties.load(new File(configPath + "/" + CONFIG_FILE)); //$NON-NLS-1$
    properties.load(new File(configPath + "/conf/" + EXTRA_CONFIG_FILE)); //$NON-NLS-1$

    String value;

    if (System.getProperty("java.io.basetmpdir") == null) { //$NON-NLS-1$
        System.setProperty("java.io.basetmpdir", System.getProperty("java.io.tmpdir")); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    File newTmp = null, tmp = new File(System.getProperty("java.io.basetmpdir")); //$NON-NLS-1$

    value = properties.getProperty("indexTemp"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (indexerTemp == null) {
      if (value != null && !value.equalsIgnoreCase("default")) { //$NON-NLS-1$
        newTmp = new File(value);
        if (!newTmp.exists() && !newTmp.mkdirs()) {
            if(LOGGER != null) LOGGER.info("Fail to create temp directory" + newTmp.getAbsolutePath()); //$NON-NLS-1$
        } else {
          tmp = newTmp;
        }
      }
      indexerTemp = new File(tmp, "indexador-temp" + new Date().getTime()); //$NON-NLS-1$
      if (!indexerTemp.mkdirs()) {
        tmp = new File(System.getProperty("java.io.basetmpdir")); //$NON-NLS-1$
        indexerTemp = new File(tmp, "indexador-temp" + new Date().getTime()); //$NON-NLS-1$
        indexerTemp.mkdirs();
      }
      if (indexerTemp.exists()) {
        System.setProperty("java.io.tmpdir", indexerTemp.getAbsolutePath()); //$NON-NLS-1$
      }
      if (tmp == newTmp) {
        indexTemp = new File(indexerTemp, "index"); //$NON-NLS-1$
      }
    }
    if (indexerTemp != null) {
      indexerTemp.mkdirs();
    }
    ConstantsViewer.indexerTemp = indexerTemp;

    value = properties.getProperty("robustImageReading"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      robustImageReading = Boolean.valueOf(value);
    }

    value = properties.getProperty("numThreads"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.equalsIgnoreCase("default")) { //$NON-NLS-1$
      numThreads = Integer.valueOf(value);
    } else {
      numThreads = Runtime.getRuntime().availableProcessors();
    }
    
    value = properties.getProperty("locale"); //$NON-NLS-1$
    if (value != null && !value.trim().isEmpty())
      locale = Locale.forLanguageTag(value.trim());
    
    System.setProperty("iped-locale", locale.toLanguageTag()); //$NON-NLS-1$

    value = properties.getProperty("forceMerge"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && value.equalsIgnoreCase("false")) { //$NON-NLS-1$
      forceMerge = false;
    }

    value = properties.getProperty("timeOut"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      timeOut = Integer.valueOf(value);
    }
    
    value = properties.getProperty("timeOutPerMB"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
    	timeOutPerMB = Integer.valueOf(value);
    }    

    value = properties.getProperty("textSplitSize"); //$NON-NLS-1$
    if (value != null && !value.trim().isEmpty()) {
    	textSplitSize = Integer.valueOf(value.trim());
    }
    
    ParsingReader.setTextSplitSize(textSplitSize);
    ParsingReader.setTextOverlapSize(textOverlapSize);
    FastPipedReader.setTimeout(timeOut);

    value = properties.getProperty("entropyTest"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      entropyTest = Boolean.valueOf(value);
    }

    value = properties.getProperty("indexUnknownFiles"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !Boolean.valueOf(value)) {
      fallBackParser = new EmptyParser();
    } else {
      fallBackParser = new RawStringParser(entropyTest);
    }

    errorParser = new RawStringParser(entropyTest);

    value = properties.getProperty("minRawStringSize"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      RawStringParser.MIN_SIZE = Integer.valueOf(value);
    }

    value = properties.getProperty("enableOCR"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.ENABLED = Boolean.valueOf(value);
    }

    value = properties.getProperty("OCRLanguage"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.LANGUAGE = value;
    }

    value = properties.getProperty("minFileSize2OCR"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.MIN_SIZE = Long.valueOf(value);
    }

    value = properties.getProperty("maxFileSize2OCR"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.MAX_SIZE = Long.valueOf(value);
    }

    value = properties.getProperty("pageSegMode"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      OCRParser.PAGESEGMODE = value;
    }

    value = properties.getProperty("maxPDFTextSize2OCR"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFOCRTextParser.MAXCHARS2OCR = Integer.valueOf(value);
    }

    value = properties.getProperty("pdfToImgResolution"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFToImage.RESOLUTION = Integer.valueOf(value);
    }

    value = properties.getProperty("pdfToImgLib"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFToImage.PDFLIB = value;
    }
    
    value = properties.getProperty("externalPdfToImgConv"); //$NON-NLS-1$
    if (value != null && !value.trim().isEmpty()) {
      PDFToImage.externalConversion = Boolean.valueOf(value.trim());
    }
    
    value = properties.getProperty("externalConvMaxMem"); //$NON-NLS-1$
    if (value != null && !value.trim().isEmpty()) {
      PDFToImage.externalConvMaxMem = value.trim();
    }
    
    value = properties.getProperty("processImagesInPDFs"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFOCRTextParser.processEmbeddedImages = Boolean.valueOf(value);
    }

    value = properties.getProperty("embutirLibreOffice"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      embutirLibreOffice = Boolean.valueOf(value);
    }

    value = properties.getProperty("sortPDFChars"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      PDFOCRTextParser.sortPDFChars = Boolean.valueOf(value);
    }

    value = properties.getProperty("extraCharsToIndex"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      LetterDigitTokenizer.load(value);
    }

    value = properties.getProperty("convertCharsToLowerCase"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      LetterDigitTokenizer.convertCharsToLowerCase = Boolean.valueOf(value);
    }

    value = properties.getProperty("addUnallocated"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      addUnallocated = Boolean.valueOf(value);
    }
    
    value = properties.getProperty("addFileSlacks"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      addFileSlacks = Boolean.valueOf(value);
    }

    value = properties.getProperty("unallocatedFragSize"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      unallocatedFragSize = Long.valueOf(value);
    }
    
    value = properties.getProperty("minItemSizeToFragment"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
    	minItemSizeToFragment = Long.valueOf(value);
    }

    value = properties.getProperty("indexTempOnSSD"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      indexTempOnSSD = Boolean.valueOf(value);
    }
    
    value = properties.getProperty("outputOnSSD"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      outputOnSSD = Boolean.valueOf(value);
    }
    if(outputOnSSD)
    	indexTemp = null;

    value = properties.getProperty("addFatOrphans"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      addFatOrphans = Boolean.valueOf(value);
    }

    value = properties.getProperty("minOrphanSizeToIgnore"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      minOrphanSizeToIgnore = Long.valueOf(value);
    }

    value = properties.getProperty("searchThreads"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      searchThreads = Integer.valueOf(value);
    }

    if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$

      String arch = "x86"; //$NON-NLS-1$
      if(System.getProperty("os.arch").contains("64")) //$NON-NLS-1$ //$NON-NLS-2$
    	  arch = "x64"; //$NON-NLS-1$
      
      loaddbPathWin = appRoot + "/tools/tsk/" + arch + "/tsk_loaddb"; //$NON-NLS-1$ //$NON-NLS-2$

      File nativelibs = new File(loaddbPathWin).getParentFile().getParentFile();
      nativelibs = new File(nativelibs, arch);
      
      IOUtil.copiaDiretorio(nativelibs, new File(indexerTemp, "nativelibs"), true); //$NON-NLS-1$
      Util.loadNatLibs(new File(indexerTemp, "nativelibs")); //$NON-NLS-1$

      OCRParser.TESSERACTFOLDER = appRoot + "/tools/tesseract"; //$NON-NLS-1$
      EDBParser.TOOL_PATH = appRoot + "/tools/esedbexport/"; //$NON-NLS-1$
      LibpffPSTParser.TOOL_PATH = appRoot + "/tools/pffexport/"; //$NON-NLS-1$
      IndexDatParser.TOOL_PATH = appRoot + "/tools/msiecfexport/"; //$NON-NLS-1$
      
      String mplayerPath = properties.getProperty("mplayerPath"); //$NON-NLS-1$
      if(mplayerPath != null)
          VideoThumbTask.mplayerWin = mplayerPath.trim();
    
    }else{
    	String tskJarPath = properties.getProperty("tskJarPath"); //$NON-NLS-1$
    	if (tskJarPath != null && !tskJarPath.isEmpty())
        	tskJarPath = tskJarPath.trim();
    	else
    		throw new IPEDException("You must set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$
    	
    	tskJarFile = new File(tskJarPath);
    	if(!tskJarFile.exists())
    		throw new IPEDException("File not found " + tskJarPath + ". Set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    String optional_jars = properties.getProperty("optional_jars"); //$NON-NLS-1$
    if(optional_jars != null)
        optionalJarDir = new File(appRoot + "/" + optional_jars.trim()); //$NON-NLS-1$
    
    File[] jars = optionalJarDir.listFiles();
    if(jars != null)
    	for(File jar : jars)
    		if(jar.getName().contains("jbig2")) //$NON-NLS-1$
    			PDFToImage.jbig2LibPath = jar.getAbsolutePath();

    String regripperFolder = properties.getProperty("regripperFolder"); //$NON-NLS-1$
    if(regripperFolder != null)
        RegistryParser.TOOL_PATH = appRoot + "/" + regripperFolder.trim(); //$NON-NLS-1$

  }

}
