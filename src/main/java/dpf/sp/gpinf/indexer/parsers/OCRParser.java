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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IndexerContext;
import dpf.sp.gpinf.indexer.util.PDFToImage;

public class OCRParser extends AbstractParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static String OUTPUTMSG = "Tesseract Open Source OCR Engine v3.02 with Leptonica";

	public static String TESSERACTPATH = "";
	public static String LANGUAGE = "por";
	public static String PAGESEGMODE = "1";
	public static long MIN_SIZE = 10000;
	public static long MAX_SIZE = 100000000;
	public static boolean ENABLED = false;

	// Caso configurado, armazena texto extraído para reaproveitamento
	public static File OUTPUT_BASE;
	private static String TEXT_DIR = "text";

	public static List<String> bookmarksToOCR;

	public static boolean EXECTESS = true;

	// Tesseract1 tesseract;

	private static final Set<MediaType> SUPPORTED_TYPES = getTypes();

	private static Set<MediaType> getTypes() {
		HashSet<MediaType> supportedTypes = new HashSet<MediaType>();

		supportedTypes.add(MediaType.image("png"));
		supportedTypes.add(MediaType.image("jpeg"));
		supportedTypes.add(MediaType.image("tiff"));
		supportedTypes.add(MediaType.application("pdf"));
		supportedTypes.add(MediaType.image("x-ms-bmp"));
		supportedTypes.add(MediaType.image("gif"));

		return supportedTypes;
	}

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	public OCRParser() {
		String[] cmd = { TESSERACTPATH + "tesseract", INPUT_FILE_TOKEN, OUTPUT_FILE_TOKEN, "-l", LANGUAGE, "-psm", PAGESEGMODE };
		this.command = cmd;

	}

	/**
	 * The token, which if present in the Command string, will be replaced with
	 * the input filename. Alternately, the input data can be streamed over
	 * STDIN.
	 */
	private static final String INPUT_FILE_TOKEN = "${INPUT}";
	/**
	 * The token, which if present in the Command string, will be replaced with
	 * the output filename. Alternately, the output data can be collected on
	 * STDOUT.
	 */
	private static final String OUTPUT_FILE_TOKEN = "${OUTPUT}";

	/**
	 * The external command to invoke.
	 * 
	 * @see Runtime#exec(String[])
	 */
	private String[] command = new String[] { "cat" };

	private boolean isFromBookmarkToOCR(IndexerContext ocrContext) {
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
		File output = null;
		try {
			TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
			File input = tikaStream.getFile();
			long size = tikaStream.getLength();
			IndexerContext ocrContext = context.get(IndexerContext.class);

			if (size >= MIN_SIZE && size <= MAX_SIZE && (bookmarksToOCR == null || isFromBookmarkToOCR(ocrContext))) {
				if (OUTPUT_BASE != null && ocrContext != null) {
					int id = ocrContext.getId();
					String outPath = (id % 100) / 10 + "/" + id % 10 + "/" + id;
					if (ocrContext.getChild() > -1)
						outPath += "-child-" + ocrContext.getChild();
					output = new File(OUTPUT_BASE, TEXT_DIR + "/" + outPath + ".txt");
					if (!output.getParentFile().exists())
						output.getParentFile().mkdirs();
				} else
					output = new File(tmp.createTemporaryFile().getAbsolutePath() + ".txt");

				if (!output.exists()) {
					if (EXECTESS) {
						if (!metadata.get(HttpHeaders.CONTENT_TYPE).equalsIgnoreCase("application/pdf")) {
							parse(xhtml, input, output);
						} else {
							parsePDF(xhtml, tmp, input, output);
						}

						if (!output.exists())
							output.createNewFile();
					}

				} else
					extractOutput(new FileInputStream(output), xhtml);

			}

		} finally {
			if (output != null && OUTPUT_BASE == null)
				output.delete();

			tmp.dispose();
		}
		xhtml.endDocument();
	}

	private void parsePDF(XHTMLContentHandler xhtml, TemporaryResources tmp, File input, File output) throws IOException, SAXException, TikaException {

		PDFToImage pdfConverter = new PDFToImage();
		pdfConverter.load(input);

		for (int page = 0; page < pdfConverter.numPages; page++) {
			File imageFile = tmp.createTemporaryFile();// new
														// File(output.getAbsolutePath()
														// + page + "." +
														// PDFToImage.EXT);
			pdfConverter.convert(page, imageFile);
			if (!imageFile.exists())
				continue;
			File imageText = new File(imageFile.getAbsolutePath() + ".txt");
			parse(xhtml, imageFile, imageText);
			if (imageText.exists()) {
				if (OUTPUT_BASE != null)
					IOUtil.copiaArquivo(imageText, output, true);
				imageText.delete();
			}
		}
		pdfConverter.close();

	}

	/*
	 * private void Tess4Jparse( TikaInputStream stream, XHTMLContentHandler
	 * xhtml, Metadata metadata, TemporaryResources tmp, File input, File
	 * output) throws IOException, SAXException, TikaException {
	 * 
	 * if(tesseract == null){ try{ tesseract = new Tesseract1(); // JNA
	 * Interface Mapping tesseract.setDatapath(TESSERACTPATH);
	 * tesseract.setLanguage(LANGUAGE);
	 * tesseract.setPageSegMode(Integer.valueOf(PAGESEGMODE));
	 * 
	 * }catch(Exception e){ e.printStackTrace(); } }
	 * 
	 * try { String result = tesseract.doOCR(input);
	 * 
	 * if(result == null) result = "";
	 * 
	 * if(OUTPUT_BASE != null){ if(!output.getParentFile().exists())
	 * output.getParentFile().mkdirs(); }else output =
	 * tmp.createTemporaryFile();
	 * 
	 * OutputStream outStream = new BufferedOutputStream(new
	 * FileOutputStream(output)); outStream.write(result.getBytes("UTF-8"));
	 * outStream.close();
	 * 
	 * if(output.exists()) extractOutput(new FileInputStream(output), xhtml);
	 * 
	 * } catch (TesseractException e) { //e.printStackTrace(); //throw new
	 * TikaException(e.toString()); System.out.println(new Date() +
	 * "\t[AVISO]\t" + "OCR ERROR from " + input.getPath() + " (" +
	 * input.length() + "bytes)"+ "\t" + e.toString()); }
	 * 
	 * }
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
		if (!TESSERACTPATH.isEmpty()) {
			Map<String, String> env = pb.environment();
			env.put("TESSDATA_PREFIX", TESSERACTPATH);
		}
		Process process = pb.start();

		process.getOutputStream().close();

		InputStream out = process.getInputStream();
		InputStream err = process.getErrorStream();

		logStream("OCR MSG", out, input);
		logStream("OCR ERROR", err, input);

		try {
			process.waitFor();
		} catch (InterruptedException e) {
			// System.out.println(new Date() + "\t[AVISO]\t" +
			// "Interrompendo OCRParsing of " + input.getPath());
			process.destroy();
			Thread.currentThread().interrupt();
			throw new TikaException("OCR interrompido", e);

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
		Reader reader = new InputStreamReader(stream, "UTF-8");
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
	private void logStream(final String logType, final InputStream stream, final File file) {
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

				if (!IndexFiles.getInstance().verbose)
					return;

				String msg = out.toString().replace(OUTPUTMSG, "").replace("\n", " ").trim();
				if (!msg.isEmpty())
					// synchronized(msgLock){
					// if(!errorLogged)
					System.out.println(new Date() + "\t[INFO]\t" + "OCR MSG" + " from " + file.getPath() + " (" + file.length() + "bytes)" + "\t" + msg);
				// errorLogged = true;
				// }

			}
		}.start();
	}

	/**
	 * Checks to see if the command can be run. Typically used with something
	 * like "myapp --version" to check to see if "myapp" is installed and on the
	 * path.
	 * 
	 * @param checkCmd
	 *            The check command to run
	 * @param errorValue
	 *            What is considered an error value?
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static boolean check(String checkCmd, int... errorValue) throws IOException, InterruptedException {
		return check(new String[] { checkCmd }, errorValue);
	}

	public static boolean check(String[] checkCmd, int... errorValue) throws IOException, InterruptedException {
		if (errorValue.length == 0) {
			errorValue = new int[] { 127 };
		}

		Process process;
		if (checkCmd.length == 1) {
			process = Runtime.getRuntime().exec(checkCmd[0]);
		} else {
			process = Runtime.getRuntime().exec(checkCmd);
		}
		int result = process.waitFor();

		for (int err : errorValue) {
			if (result == err)
				return false;
		}
		return true;
	}

}
