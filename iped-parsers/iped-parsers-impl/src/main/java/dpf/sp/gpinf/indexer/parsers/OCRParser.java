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
package dpf.sp.gpinf.indexer.parsers;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;
import dpf.sp.gpinf.indexer.util.IOUtil;

/**
 * Parser OCR para imagens e PDFs via Tesseract. No caso de PDFs, é gerada uma imagem
 * para cada página. Outra opção seria extrair as imagens do PDF, mas alguns softwares
 * de digitalização geram várias imagens por página e algumas linhas de texto são
 * cortadas pelas bordas das imagens.
 * 
 * @author Nassif
 *
 */
public class OCRParser extends AbstractParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static Logger LOGGER;

	private static final String OUTPUT_REGEX = "Tesseract Open Source OCR Engine v.* with Leptonica"; //$NON-NLS-1$
	
	public static final String OCR_CHAR_COUNT = "ocrCharCount"; //$NON-NLS-1$
	
	private static final String TOOL_NAME = "tesseract"; //$NON-NLS-1$
    
    public static final String ENABLE_PROP = TOOL_NAME + ".enabled"; //$NON-NLS-1$
    public static final String TOOL_PATH_PROP = TOOL_NAME + ".path"; //$NON-NLS-1$
    public static final String LANGUAGE_PROP = "ocr.language"; //$NON-NLS-1$
    public static final String PAGE_SEGMODE_PROP = "ocr.pageSegMode"; //$NON-NLS-1$
    public static final String MIN_SIZE_PROP = "ocr.minFileSize"; //$NON-NLS-1$
    public static final String MAX_SIZE_PROP = "ocr.maxFileSize"; //$NON-NLS-1$
    
    private boolean ENABLED = Boolean.valueOf(System.getProperty(ENABLE_PROP, "false")); //$NON-NLS-1$
    private String TOOL_PATH = System.getProperty(TOOL_PATH_PROP, ""); //$NON-NLS-1$
    private String LANGUAGE = System.getProperty(LANGUAGE_PROP, "por"); //$NON-NLS-1$
    private String PAGESEGMODE = System.getProperty(PAGE_SEGMODE_PROP, "1"); //$NON-NLS-1$
    private int MIN_SIZE = Integer.valueOf(System.getProperty(MIN_SIZE_PROP, "10000")); //$NON-NLS-1$
    private long MAX_SIZE = Integer.valueOf(System.getProperty(MAX_SIZE_PROP, "100000000")); //$NON-NLS-1$
    
	private static AtomicBoolean checked = new AtomicBoolean();
	private static String tessVersion = "";

	// Caso configurado, armazena texto extraído para reaproveitamento
	private File OUTPUT_BASE;
	public static String TEXT_DIR = "text"; //$NON-NLS-1$

	public static List<String> bookmarksToOCR;

	private static final Set<MediaType> SUPPORTED_TYPES = getTypes();

	private static Set<MediaType> getTypes() {
		HashSet<MediaType> supportedTypes = new HashSet<MediaType>();

		supportedTypes.add(MediaType.image("png")); //$NON-NLS-1$
		supportedTypes.add(MediaType.image("jpeg")); //$NON-NLS-1$
		supportedTypes.add(MediaType.image("tiff")); //$NON-NLS-1$
		supportedTypes.add(MediaType.application("pdf")); //$NON-NLS-1$
		supportedTypes.add(MediaType.image("bmp")); //$NON-NLS-1$
		supportedTypes.add(MediaType.image("gif")); //$NON-NLS-1$
		supportedTypes.add(MediaType.image("jp2")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("jpx")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("x-portable-pixmap")); //$NON-NLS-1$

		return supportedTypes;
	}

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}
	
	public boolean isEnabled() {
	    return this.ENABLED;
	}

	public OCRParser() {
		String tesseractPath = TOOL_NAME;
		if(!TOOL_PATH.isEmpty())
			tesseractPath = TOOL_PATH + "/" + TOOL_NAME; //$NON-NLS-1$ //$NON-NLS-2$
		
		String[] cmd = { tesseractPath, INPUT_FILE_TOKEN, OUTPUT_FILE_TOKEN, "-l", LANGUAGE, "-psm", PAGESEGMODE }; //$NON-NLS-1$ //$NON-NLS-2$
		this.command = cmd;
		
		try { 
		    synchronized(checked) {
                if (ENABLED && !checked.getAndSet(true)) {
                    tessVersion = checkVersion(cmd[0], "-v"); //$NON-NLS-1$
                    LOGGER = LoggerFactory.getLogger(OCRParser.class);
                    LOGGER.info("Detected Tesseract " + tessVersion); //$NON-NLS-1$
                }
            }
            if(ENABLED && tessVersion.startsWith("4")) { //$NON-NLS-1$
                for(int i = 0; i < command.length; i++)
                    if(command[i].equals("-psm")) //$NON-NLS-1$
                        command[i] = "--psm"; //$NON-NLS-1$
            }
			
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("Error running " + cmd[0], e); //$NON-NLS-1$
		}
	}

	/**
	 * The token, which if present in the Command string, will be replaced with
	 * the input filename. Alternately, the input data can be streamed over
	 * STDIN.
	 */
	private static final String INPUT_FILE_TOKEN = "${INPUT}"; //$NON-NLS-1$
	/**
	 * The token, which if present in the Command string, will be replaced with
	 * the output filename. Alternately, the output data can be collected on
	 * STDOUT.
	 */
	private static final String OUTPUT_FILE_TOKEN = "${OUTPUT}"; //$NON-NLS-1$

	/**
	 * The external command to invoke.
	 * 
	 * @see Runtime#exec(String[])
	 */
	private String[] command;
	
	private Random random = new Random();
	
	private String filePath = "";

	private boolean isFromBookmarkToOCR(ItemInfo ocrContext) {
		if (bookmarksToOCR.size() == 0)
			return true;

		for (String bookmarkToOCR : bookmarksToOCR)
			for (String bookmark : ocrContext.getBookmarks())
				if (bookmarkToOCR.equalsIgnoreCase(bookmark))
					return true;
		return false;
	}

	/**
	 * Executes the configured external command and passes the given document
	 * stream as a simple XHTML document to the given SAX content handler.
	 */
	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		if (!ENABLED)
			return;

		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();

		TemporaryResources tmp = new TemporaryResources();
		File output = null, tmpOutput = null;
		try {
			TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
			File input = tikaStream.getFile();
			long size = tikaStream.getLength();
			if(metadata.get(Metadata.CONTENT_LENGTH) != null)
				size = Long.parseLong(metadata.get(Metadata.CONTENT_LENGTH));
			ItemInfo itemInfo = context.get(ItemInfo.class);
			filePath = itemInfo.getPath();
			
			OCROutputFolder outDir = context.get(OCROutputFolder.class);
			if(outDir != null) OUTPUT_BASE = outDir.getPath();

			if (size >= MIN_SIZE && size <= MAX_SIZE && (bookmarksToOCR == null || isFromBookmarkToOCR(itemInfo))) {
				if (OUTPUT_BASE != null && itemInfo != null && itemInfo.getHash() != null) {
					String hash = itemInfo.getHash();
					String outPath = hash.charAt(0) + "/" + hash.charAt(1) + "/" + hash; //$NON-NLS-1$ //$NON-NLS-2$
					if (itemInfo.getChild() > -1)
						outPath += "-child-" + itemInfo.getChild(); //$NON-NLS-1$
					output = new File(OUTPUT_BASE, TEXT_DIR + "/" + outPath + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
					
					if (!output.getParentFile().exists())
						output.getParentFile().mkdirs();
				} else
                    output = new File(tmp.createTemporaryFile().getAbsolutePath() + ".txt"); //$NON-NLS-1$

                if (!output.exists()) {
				        
                        tmpOutput = new File(OUTPUT_BASE, TEXT_DIR + "/ocr-" + random.nextLong() + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
						
						String mediaType = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE); 
						if (mediaType.equals("application/pdf")) //$NON-NLS-1$
                            parsePDF(xhtml, tmp, input, tmpOutput);
                        
                        else if (mediaType.equals("image/tiff")) //$NON-NLS-1$
                            parseTiff(xhtml, tmp, input, tmpOutput);
                        
                        else
                            parse(xhtml, input, tmpOutput);
						
						if(tmpOutput.exists())
                            tmpOutput.renameTo(output);
                        else
                            output.createNewFile();

				} else
					extractOutput(new FileInputStream(output), xhtml);

			}

		} finally {
			if(tmpOutput != null)
				tmpOutput.delete();
			if (output != null && OUTPUT_BASE == null)
				output.delete();
			tmp.dispose();
		}
		xhtml.endDocument();
	}
	
	private BufferedImage getCompatibleImage(BufferedImage image) {
        // obtain the current system graphical settings
        GraphicsConfiguration gfx_config = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

        /*
         * if image is already compatible and optimized for current system
         * settings, simply return it
         */
        if (image.getColorModel().equals(gfx_config.getColorModel()))
            return image;

        // image is not optimized, so create a new image that is
        BufferedImage new_image = gfx_config.createCompatibleImage(image.getWidth(), image.getHeight(), image.getTransparency());

        // get the graphics context of the new image to draw the old image on
        Graphics2D g2d = (Graphics2D) new_image.getGraphics();

        // actually draw the image and dispose of context no longer needed
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        // return the new optimized image
        return new_image;
    }
	
  private void parseTiff(XHTMLContentHandler xhtml, TemporaryResources tmp, File input, File output) throws IOException, SAXException, TikaException {
	    
	ImageReader reader = null;
    try (ImageInputStream iis = ImageIO.createImageInputStream(input)){
      reader = ImageIO.getImageReaders(iis).next();
      reader.setInput(iis, false, true);
      int numPages = reader.getNumImages(true);
      if(numPages > 3){
    	  for (int page = 0; page < numPages; page++){
        	  	File imageFile = null;
		        try{
		            ImageReadParam params = reader.getDefaultReadParam();
		            int w0 = reader.getWidth(page);
		            int h0 = reader.getHeight(page);
		            BufferedImage image = reader.getImageTypes(page).next().createBufferedImage(w0, h0);
		            params.setDestination(image);
		            try{
		            	reader.read(page, params);
		            }catch(IOException e){}
		            
		            image = getCompatibleImage(image);
		            imageFile = File.createTempFile("iped-ocr", "." + PDFToImage.EXT); //$NON-NLS-1$ //$NON-NLS-2$
		            ImageIO.write(image, PDFToImage.EXT, imageFile);
		            File imageText = new File(imageFile.getAbsolutePath() + ".txt"); //$NON-NLS-1$
		            parse(xhtml, imageFile, imageText);
		            if (imageText.exists()) {
		                if (OUTPUT_BASE != null)
		                    IOUtil.copiaArquivo(imageText, output, true);
		                imageText.delete();
		            }
		        }catch(IOException e){
		            //ignore and try next page
		        }finally {
		        	if(imageFile != null)
		        		imageFile.delete();
		        }
		  }
      }else
          parse(xhtml, input, output);
      
    }finally{
		if(reader != null)
			reader.dispose();
	}
  }

	private void parsePDF(XHTMLContentHandler xhtml, TemporaryResources tmp, File input, File output) throws IOException, SAXException, TikaException {

		PDFToImage pdfConverter = new PDFToImage();
		try{
			pdfConverter.load(input);
			for (int page = 0; page < pdfConverter.getNumPages(); page++) {
				File imageFile = null;
				try {
					imageFile = File.createTempFile("iped-ocr", "." + PDFToImage.EXT); //$NON-NLS-1$ //$NON-NLS-2$
					boolean success = pdfConverter.convert(page, imageFile);
					if (!success || !imageFile.exists())
						continue;
					File imageText = new File(imageFile.getAbsolutePath() + ".txt"); //$NON-NLS-1$
					parse(xhtml, imageFile, imageText);
					if (imageText.exists()) {
						if (OUTPUT_BASE != null)
							IOUtil.copiaArquivo(imageText, output, true);
						imageText.delete();
					}
				}finally {
					if(imageFile != null)
						imageFile.delete();
				}
			}
		}finally{
			pdfConverter.close();
		}
	}
	
	/*
	private ITesseract tesseract;
	
	private void parse(XHTMLContentHandler xhtml, File input, File output) throws IOException, SAXException, TikaException {
	  
	  if(tesseract == null){ 
	      synchronized(OCRParser.class) {
	          if(tesseract == null)
	              try{
	                  ITesseract tess = new Tesseract1(); // JNA
	                  //tesseract = new Tesseract(); // JNA direct
	                  if(!TOOL_PATH.isEmpty())
	                      tess.setDatapath(new File(TOOL_PATH, "tessdata").getAbsolutePath());
	                  tess.setLanguage(LANGUAGE);
	                  tess.setPageSegMode(Integer.valueOf(PAGESEGMODE));
	                  tesseract = tess;
	              
	              }catch(Exception e){
	                  e.printStackTrace();
	              }
	      }
	      
	  }
	  
	  //synchronized(OCRParser.class) {
	  
	  try {
	      String result = tesseract.doOCR(ImageIO.read(input));
	  
    	  if(result == null) result = "";
    	  byte[] bytes = result.getBytes("UTF-8");
    	  
    	  if(!result.isEmpty())
    	      extractOutput(new ByteArrayInputStream(bytes), xhtml);
    	  
    	  if(OUTPUT_BASE != null){
              Files.write(output.toPath(), bytes, StandardOpenOption.CREATE);
          }
	  
	   } catch (TesseractException e) {
	      e.printStackTrace();
	      throw new TikaException(e.toString());
	   }
	  
	  //}
	}
	*/
	

	private void parse(XHTMLContentHandler xhtml, File input, File output) throws IOException, SAXException, TikaException {

		// Build our command
		String[] cmd = new String[command.length];
		System.arraycopy(command, 0, cmd, 0, command.length);
		for (int i = 0; i < cmd.length; i++) {
			if (cmd[i].indexOf(INPUT_FILE_TOKEN) != -1) {
				cmd[i] = cmd[i].replace(INPUT_FILE_TOKEN, input.getPath());
			}
			if (cmd[i].indexOf(OUTPUT_FILE_TOKEN) != -1) {
				String outputPrefix = output.getPath().substring(0, output.getPath().length() - 4);
				cmd[i] = cmd[i].replace(OUTPUT_FILE_TOKEN, outputPrefix);
			}
		}

		ProcessBuilder pb = new ProcessBuilder(cmd);
		Map<String, String> env = pb.environment();
		//try to disable OpenMP
        env.put("OMP_THREAD_LIMIT", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        
		Process process = pb.start();

		process.getOutputStream().close();

		InputStream out = process.getInputStream();
		InputStream err = process.getErrorStream();

		logStream("OCR MSG", out); //$NON-NLS-1$
		logStream("OCR ERROR", err); //$NON-NLS-1$

		try {
			process.waitFor();
		} catch (InterruptedException e) {
			// System.out.println(new Date() + "\t[AVISO]\t" +
			// "Interrompendo OCRParsing of " + input.getPath());
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

		}
		if (output.exists())
			extractOutput(new FileInputStream(output), xhtml);

	}

	/**
	 * Starts a thread that extracts the contents of the standard output stream
	 * of the given process to the given XHTML content handler. The standard
	 * output stream is closed once fully processed.
	 * 
	 * @param process
	 *            process
	 * @param xhtml
	 *            XHTML content handler
	 * @throws SAXException
	 *             if the XHTML SAX events could not be handled
	 * @throws IOException
	 *             if an input error occurred
	 */
	private void extractOutput(InputStream stream, XHTMLContentHandler xhtml) throws SAXException, IOException {
		Reader reader = new InputStreamReader(stream, "UTF-8"); //$NON-NLS-1$
		try {
			//xhtml.startElement("p");
			char[] buffer = new char[1024];
			for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
				xhtml.characters(buffer, 0, n);
			}
			//xhtml.endElement("p");
		} finally {
			reader.close();
		}
	}

	// Object msgLock = new Object();

	/**
	 * 
	 * @param process
	 *            process
	 * @param stream
	 *            input stream
	 */
	private void logStream(final String logType, final InputStream stream) {
		new Thread() {
			@Override
			public void run() {
				Reader reader = new InputStreamReader(stream);
				StringBuilder out = new StringBuilder();
				char[] buffer = new char[1024];
				try {
					for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
						out.append(buffer, 0, n);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					IOUtils.closeQuietly(stream);
				}
				
				String msg = out.toString().replaceAll(OUTPUT_REGEX, "").replaceAll("\r?\n", " ").trim();
                if (!msg.isEmpty())
                    LOGGER.debug("OCR msg from " + filePath + "\t" + msg);

				return;
			}
		}.start();
	}

	public static String checkVersion(String... checkCmd) throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec(checkCmd);
		int result = process.waitFor();

		if(result != 0) {
		    throw new IOException("Returned error code " + result); //$NON-NLS-1$
		}
		try {
            return extractVersion(process.getInputStream());
        }catch(Exception e) {
            return extractVersion(process.getErrorStream());
        }
    }
    
    private static String extractVersion(InputStream is) throws IOException {
        return IOUtils.readLines(is).get(0).replace("tesseract ", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

}
