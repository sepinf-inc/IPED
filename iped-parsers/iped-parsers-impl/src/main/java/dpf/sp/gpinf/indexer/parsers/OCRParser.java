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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.CharCountContentHandler;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;
import dpf.sp.gpinf.indexer.util.ExternalImageConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;

/**
 * Parser OCR para imagens e PDFs via Tesseract. No caso de PDFs, é gerada uma
 * imagem para cada página. Outra opção seria extrair as imagens do PDF, mas
 * alguns softwares de digitalização geram várias imagens por página e algumas
 * linhas de texto são cortadas pelas bordas das imagens.
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

    private static final String CHILD_PREFIX = "-child-"; //$NON-NLS-1$

    private static final String OCR_STORAGE = "ocr-results.db"; //$NON-NLS-1$

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS ocr(id TEXT PRIMARY KEY, text TEXT);"; //$NON-NLS-1$

    private static final String INSERT_DATA = "INSERT INTO ocr(id, text) VALUES(?,?) ON CONFLICT(id) DO NOTHING"; //$NON-NLS-1$

    private static final String SELECT_EXACT = "SELECT text FROM ocr WHERE id=?;"; //$NON-NLS-1$

    private static final String SELECT_ALL = "SELECT id, text FROM ocr WHERE id LIKE ?;"; //$NON-NLS-1$

    public static final String ENABLE_PROP = TOOL_NAME + ".enabled"; //$NON-NLS-1$
    public static final String TOOL_PATH_PROP = TOOL_NAME + ".path"; //$NON-NLS-1$
    public static final String LANGUAGE_PROP = "ocr.language"; //$NON-NLS-1$
    public static final String PAGE_SEGMODE_PROP = "ocr.pageSegMode"; //$NON-NLS-1$
    public static final String MIN_SIZE_PROP = "ocr.minFileSize"; //$NON-NLS-1$
    public static final String MAX_SIZE_PROP = "ocr.maxFileSize"; //$NON-NLS-1$
    public static final String SUBSET_TO_OCR = "subsetToOcr"; //$NON-NLS-1$
    public static final String SUBSET_SEPARATOR = "_#_"; //$NON-NLS-1$
    public static final String TEXT_DIR = "text"; //$NON-NLS-1$
    public static final String PROCESS_NON_STANDARD_FORMATS_PROP = "ocr.processNonStandard"; //$NON-NLS-1$
    public static final String MAX_CONV_IMAGE_SIZE_PROP = "ocr.maxConvImageSize"; //$NON-NLS-1$

    private boolean ENABLED = Boolean.valueOf(System.getProperty(ENABLE_PROP, "false")); //$NON-NLS-1$
    private String TOOL_PATH = System.getProperty(TOOL_PATH_PROP, ""); //$NON-NLS-1$
    private String LANGUAGE = System.getProperty(LANGUAGE_PROP, "por"); //$NON-NLS-1$
    private String PAGESEGMODE = System.getProperty(PAGE_SEGMODE_PROP, "1"); //$NON-NLS-1$
    private int MIN_SIZE = Integer.valueOf(System.getProperty(MIN_SIZE_PROP, "10000")); //$NON-NLS-1$
    private long MAX_SIZE = Integer.valueOf(System.getProperty(MAX_SIZE_PROP, "100000000")); //$NON-NLS-1$
    private List<String> bookmarksToOCR = Arrays
            .asList(System.getProperty(SUBSET_TO_OCR, SUBSET_SEPARATOR).split(SUBSET_SEPARATOR)); // $NON-NLS-1$;
    private boolean PROCESS_NON_STANDARD_FORMATS = Boolean.valueOf(System.getProperty(PROCESS_NON_STANDARD_FORMATS_PROP, "true")); //$NON-NLS-1$
    private int MAX_CONV_IMAGE_SIZE = Integer.valueOf(System.getProperty(MAX_CONV_IMAGE_SIZE_PROP, "3000")); //$NON-NLS-1$

    private static AtomicBoolean checked = new AtomicBoolean();
    private static String tessVersion = "";

    private static HashMap<File, Connection> connMap = new HashMap<>();

    // Root folder to store ocr results
    private File outputBase;

    private static final Set<MediaType> directSupportedTypes = getDirectSupportedTypes();
    private static final Set<MediaType> nonStandardSupportedTypes = getNonStandardSupportedTypes();
    private static final Set<MediaType> nonImageSupportedTypes = getNonImageSupportedTypes();

    private static final Map<String,Set<MediaType>> librarySupportedTypes = getLibrarySupportedTypes();
    
    private static final Set<MediaType> imageSupportedTypes = new HashSet<MediaType>();
    private static final Set<MediaType> allSupportedTypes = new HashSet<MediaType>();
    
    static {
        imageSupportedTypes.addAll(directSupportedTypes);
        imageSupportedTypes.addAll(nonStandardSupportedTypes);
        
        allSupportedTypes.addAll(imageSupportedTypes);
        allSupportedTypes.addAll(nonImageSupportedTypes);
    }

    public static Set<MediaType> getImageSupportedTypes() {
        return imageSupportedTypes;
    }

    private static Set<MediaType> getDirectSupportedTypes() {
        HashSet<MediaType> types = new HashSet<MediaType>();
        
        types.add(MediaType.image("png")); //$NON-NLS-1$
        types.add(MediaType.image("jpeg")); //$NON-NLS-1$
        types.add(MediaType.image("tiff")); //$NON-NLS-1$
        types.add(MediaType.image("bmp")); //$NON-NLS-1$
        types.add(MediaType.image("x-portable-bitmap")); //$NON-NLS-1$
        types.add(MediaType.image("x-portable-graymap")); //$NON-NLS-1$
        types.add(MediaType.image("x-portable-pixmap")); //$NON-NLS-1$

        return types;
    }
    
    private static Set<MediaType> getNonImageSupportedTypes() {
        HashSet<MediaType> types = new HashSet<MediaType>();
        types.add(MediaType.application("pdf")); //$NON-NLS-1$
        return types;
    }
    
    private static Set<MediaType> getNonStandardSupportedTypes() {
        HashSet<MediaType> types = new HashSet<MediaType>();
        
        types.add(MediaType.image("gif")); //$NON-NLS-1$
        types.add(MediaType.image("jp2")); //$NON-NLS-1$
        types.add(MediaType.image("jpx")); //$NON-NLS-1$
        types.add(MediaType.image("webp")); //$NON-NLS-1$
        
        types.add(MediaType.image("aces")); //$NON-NLS-1$
        types.add(MediaType.image("emf")); //$NON-NLS-1$
        types.add(MediaType.image("heic")); //$NON-NLS-1$
        types.add(MediaType.image("svg+xml")); //$NON-NLS-1$
        types.add(MediaType.image("vnd.adobe.photoshop")); //$NON-NLS-1$
        types.add(MediaType.image("vnd.wap.wbmp")); //$NON-NLS-1$
        types.add(MediaType.image("vnd.zbrush.dcx")); //$NON-NLS-1$
        types.add(MediaType.image("vnd.zbrush.pcx")); //$NON-NLS-1$
        types.add(MediaType.image("wmf")); //$NON-NLS-1$
        types.add(MediaType.image("x-cmu-raster")); //$NON-NLS-1$
        types.add(MediaType.image("x-jp2-codestream")); //$NON-NLS-1$
        types.add(MediaType.image("x-rgb")); //$NON-NLS-1$
        types.add(MediaType.image("x-xbitmap")); //$NON-NLS-1$
        
        return types;
    }

    private static Map<String, Set<MediaType>> getLibrarySupportedTypes() {
        Map<String, Set<MediaType>> libraryToTypes = new HashMap<String, Set<MediaType>>();
        
        HashSet<MediaType> types = new HashSet<MediaType>();
        types.add(MediaType.image("gif")); //$NON-NLS-1$
        libraryToTypes.put("libgif", types); //$NON-NLS-1$

        types = new HashSet<MediaType>();
        types.add(MediaType.image("jp2")); //$NON-NLS-1$
        types.add(MediaType.image("jpx")); //$NON-NLS-1$
        libraryToTypes.put("libopenjp2", types); //$NON-NLS-1$

        types = new HashSet<MediaType>();
        types.add(MediaType.image("webp")); //$NON-NLS-1$
        libraryToTypes.put("libwebp", types); //$NON-NLS-1$
        
        return libraryToTypes;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return PROCESS_NON_STANDARD_FORMATS ? allSupportedTypes : directSupportedTypes;
    }

    public boolean isEnabled() {
        return this.ENABLED;
    }

    public OCRParser() {
        String tesseractPath = TOOL_NAME;
        if (!TOOL_PATH.isEmpty())
            tesseractPath = TOOL_PATH + "/" + TOOL_NAME; //$NON-NLS-1$ //$NON-NLS-2$

        String[] cmd = { tesseractPath, INPUT_FILE_TOKEN, OUTPUT_FILE_TOKEN, "-l", LANGUAGE, "-psm", PAGESEGMODE }; //$NON-NLS-1$ //$NON-NLS-2$
        this.command = cmd;

        try {
            synchronized (checked) {
                if (ENABLED && !checked.getAndSet(true)) {
                    List<String> info = checkVersionInfo(cmd[0], "-v"); //$NON-NLS-1$
                    if (!info.isEmpty()) 
                        tessVersion = info.get(0);
                    LOGGER = LoggerFactory.getLogger(OCRParser.class);
                    LOGGER.info("Detected Tesseract " + tessVersion); //$NON-NLS-1$
                    if (info.size() <= 1) {
                        LOGGER.info("No Tesseract optional image libraries detected."); //$NON-NLS-1$
                    } else {
                        LOGGER.info("Tesseract optional image libraries: " + info.subList(1, info.size())); //$NON-NLS-1$
                        for (int i = 1; i < info.size(); i++) {
                            Set<MediaType> types = librarySupportedTypes.get(info.get(i));
                            directSupportedTypes.addAll(types);
                            nonStandardSupportedTypes.removeAll(types);
                        }
                    }
                    LOGGER.info("Process non-standard image formats {}.", //$NON-NLS-1$
                            PROCESS_NON_STANDARD_FORMATS ? "enabled" : "disabled");
                }
            }
            if (ENABLED && Integer.valueOf(tessVersion.charAt(0)) >= 4) { // $NON-NLS-1$
                for (int i = 0; i < command.length; i++)
                    if (command[i].equals("-psm")) //$NON-NLS-1$
                        command[i] = "--psm"; //$NON-NLS-1$
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error running " + cmd[0], e); //$NON-NLS-1$
        }
    }

    /**
     * The token, which if present in the Command string, will be replaced with the
     * input filename. Alternately, the input data can be streamed over STDIN.
     */
    private static final String INPUT_FILE_TOKEN = "${INPUT}"; //$NON-NLS-1$
    /**
     * The token, which if present in the Command string, will be replaced with the
     * output filename. Alternately, the output data can be collected on STDOUT.
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

        for (String group : bookmarksToOCR) {
            for (String bookmark : ocrContext.getBookmarks())
                if (group.equalsIgnoreCase(bookmark))
                    return true;
            for (String category : ocrContext.getCategories())
                if (group.equalsIgnoreCase(category))
                    return true;
        }

        return false;
    }

    private static synchronized Connection getConnection(File outputBase) {
        File db = new File(outputBase, OCR_STORAGE);
        Connection conn = connMap.get(db);
        if (conn != null) {
            return conn;
        }
        db.getParentFile().mkdirs();
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setSynchronous(SynchronousMode.NORMAL);
            config.setBusyTimeout(3600000);
            conn = config.createConnection("jdbc:sqlite:" + db.getAbsolutePath());
            connMap.put(db, conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_TABLE);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return conn;
    }

    /**
     * Executes the configured external command and passes the given document stream
     * as a simple XHTML document to the given SAX content handler.
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        if (!ENABLED)
            return;

        CharCountContentHandler countHandler = new CharCountContentHandler(handler);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(countHandler, metadata);
        xhtml.startDocument();

        TemporaryResources tmp = new TemporaryResources();
        File output = null, tmpOutput = null;
        String outFileName = null;
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
            File input = tikaStream.getFile();
            long size = tikaStream.getLength();
            if (metadata.get(Metadata.CONTENT_LENGTH) != null)
                size = Long.parseLong(metadata.get(Metadata.CONTENT_LENGTH));
            ItemInfo itemInfo = context.get(ItemInfo.class);
            filePath = itemInfo.getPath();

            OCROutputFolder outDir = context.get(OCROutputFolder.class);
            if (outDir != null)
                outputBase = new File(outDir.getPath(), TEXT_DIR);

            if (size >= MIN_SIZE && size <= MAX_SIZE && (bookmarksToOCR == null || isFromBookmarkToOCR(itemInfo))) {
                if (outputBase != null && itemInfo != null && itemInfo.getHash() != null) {
                    String hash = itemInfo.getHash();
                    outFileName = hash;
                    if (itemInfo.getChild() > -1) {
                        outFileName += CHILD_PREFIX + itemInfo.getChild(); // $NON-NLS-1$
                    }

                    String ocrText = getOcrTextFromDb(outFileName, outputBase);
                    if (ocrText != null) {
                        extractOutput(ocrText, xhtml); //$NON-NLS-1$
                        return;
                    }

                    String outPath = hash.charAt(0) + "/" + hash.charAt(1); //$NON-NLS-1$
                    output = new File(outputBase, outPath + "/" + outFileName + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
                }

                if (output == null || !output.exists()) {

                    tmpOutput = new File(outputBase, "ocr-" + random.nextLong() + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$

                    String mediaType = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
                    if (mediaType.equals("application/pdf")) //$NON-NLS-1$
                        parsePDF(xhtml, tmp, input, tmpOutput);

                    else if (mediaType.equals("image/tiff")) //$NON-NLS-1$
                        parseTiff(xhtml, tmp, input, tmpOutput);

                    else if (nonStandardSupportedTypes.contains(MediaType.parse(mediaType))
                            || (mediaType.equals("image/bmp") && ImageUtil.isCompressedBMP(input)))
                        parseNonStandard(xhtml, input, tmpOutput);
                    
                    else
                        parse(xhtml, input, tmpOutput);

                    byte[] bytes;
                    if (tmpOutput.exists()) {
                        bytes = Files.readAllBytes(tmpOutput.toPath());
                    } else {
                        bytes = new byte[0];
                    }

                    String ocrText = new String(bytes, "UTF-8").trim(); //$NON-NLS-1$
                    storeOcrTextInDb(outFileName, ocrText, outputBase);

                } else {
                    extractOutput(output, xhtml);
                }
            }

        } finally {
            xhtml.endDocument();
            metadata.set(OCRParser.OCR_CHAR_COUNT, Integer.toString(countHandler.getCharCount()));
            if (tmpOutput != null) {
                tmpOutput.delete();
            }
            tmp.dispose();
        }
    }

    private static String getOcrTextFromDb(String id, File outputBase) throws IOException {
        try (PreparedStatement ps = getConnection(outputBase).prepareStatement(SELECT_EXACT)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    private static void storeOcrTextInDb(String id, String ocrText, File outputBase) throws IOException {
        try (PreparedStatement ps = getConnection(outputBase).prepareStatement(INSERT_DATA)) {
            ps.setString(1, id);
            ps.setString(2, ocrText);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    public static void copyOcrResults(String hash, File inputBase, File outputBase) throws IOException {
        File sourceDb = new File(inputBase, OCRParser.TEXT_DIR + File.separator + OCRParser.OCR_STORAGE);
        File targetDb = new File(outputBase, OCRParser.TEXT_DIR + File.separator + OCRParser.OCR_STORAGE);
        if (!sourceDb.exists())
            return;
        try (PreparedStatement ps = getConnection(sourceDb.getParentFile()).prepareStatement(SELECT_ALL)) {
            ps.setString(1, hash + "%"); //$NON-NLS-1$
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString(1);
                String ocrText = rs.getString(2);
                storeOcrTextInDb(id, ocrText, targetDb.getParentFile());
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

    }

    private BufferedImage getCompatibleImage(BufferedImage image) {
        // obtain the current system graphical settings
        GraphicsConfiguration gfx_config = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();

        /*
         * if image is already compatible and optimized for current system settings,
         * simply return it
         */
        if (image.getColorModel().equals(gfx_config.getColorModel()))
            return image;

        // image is not optimized, so create a new image that is
        BufferedImage new_image = gfx_config.createCompatibleImage(image.getWidth(), image.getHeight(),
                image.getTransparency());

        // get the graphics context of the new image to draw the old image on
        Graphics2D g2d = (Graphics2D) new_image.getGraphics();

        // actually draw the image and dispose of context no longer needed
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        // return the new optimized image
        return new_image;
    }

    private void parseTiff(XHTMLContentHandler xhtml, TemporaryResources tmp, File input, File output)
            throws IOException, SAXException, TikaException {

        ImageReader reader = null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
            reader = ImageIO.getImageReaders(iis).next();
            reader.setInput(iis, false, true);
            int numPages = reader.getNumImages(true);
            if (numPages > 3) {
                for (int page = 0; page < numPages; page++) {
                    File imageFile = null;
                    try {
                        ImageReadParam params = reader.getDefaultReadParam();
                        int w0 = reader.getWidth(page);
                        int h0 = reader.getHeight(page);
                        BufferedImage image = reader.getImageTypes(page).next().createBufferedImage(w0, h0);
                        params.setDestination(image);
                        try {
                            reader.read(page, params);
                        } catch (IOException e) {
                        }

                        image = getCompatibleImage(image);
                        imageFile = File.createTempFile("iped-ocr", "." + PDFToImage.EXT); //$NON-NLS-1$ //$NON-NLS-2$
                        ImageIO.write(image, PDFToImage.EXT, imageFile);
                        File imageText = new File(imageFile.getAbsolutePath() + ".txt"); //$NON-NLS-1$
                        parse(xhtml, imageFile, imageText);
                        if (imageText.exists()) {
                            if (outputBase != null)
                                IOUtil.copiaArquivo(imageText, output, true);
                            imageText.delete();
                        }
                    } catch (IOException e) {
                        // ignore and try next page
                    } finally {
                        if (imageFile != null)
                            imageFile.delete();
                    }
                }
            } else
                parse(xhtml, input, output);

        } finally {
            if (reader != null)
                reader.dispose();
        }
    }

    private void parseNonStandard(XHTMLContentHandler xhtml, File input, File output)
            throws IOException, SAXException, TikaException {
        File imageFile = null;
        try {
            BufferedImage img = ImageUtil.getSubSampledImage(input, MAX_CONV_IMAGE_SIZE * 2, MAX_CONV_IMAGE_SIZE * 2);
            if (img == null) {
                try (ExternalImageConverter converter = new ExternalImageConverter()) {
                    img = converter.getImage(input, MAX_CONV_IMAGE_SIZE, true, input.length());
                }
            }
            if (img != null) {
                if (img.getWidth() > MAX_CONV_IMAGE_SIZE || img.getHeight() > MAX_CONV_IMAGE_SIZE)
                    img = ImageUtil.resizeImage(img, MAX_CONV_IMAGE_SIZE, MAX_CONV_IMAGE_SIZE, BufferedImage.TYPE_3BYTE_BGR);
                
                img = getCompatibleImage(img);
                imageFile = File.createTempFile("iped-ocr", "." + PDFToImage.EXT); //$NON-NLS-1$ //$NON-NLS-2$
                ImageIO.write(img, PDFToImage.EXT, imageFile);

                if (imageFile.exists()) 
                    parse(xhtml, imageFile, output);
            }
        } finally {
            if (imageFile != null)
                imageFile.delete();
        }
    }
    
    private void parsePDF(XHTMLContentHandler xhtml, TemporaryResources tmp, File input, File output)
            throws IOException, SAXException, TikaException {

        PDFToImage pdfConverter = new PDFToImage();
        try {
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
                        if (outputBase != null)
                            IOUtil.copiaArquivo(imageText, output, true);
                        imageText.delete();
                    }
                } finally {
                    if (imageFile != null)
                        imageFile.delete();
                }
            }
        } finally {
            pdfConverter.close();
        }
    }

    private void parse(XHTMLContentHandler xhtml, File input, File output)
            throws IOException, SAXException, TikaException {

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
        // try to disable OpenMP
        env.put("OMP_THREAD_LIMIT", "1"); //$NON-NLS-1$ //$NON-NLS-2$

        Process process = pb.start();

        process.getOutputStream().close();

        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();

        logStream("OCR MSG", out); //$NON-NLS-1$
        logStream("OCR ERROR", err); //$NON-NLS-1$

        try {
            int status = process.waitFor();
            if (status != 0) {
                throw new TikaException("tesseract returned error code " + status);
            }
        } catch (InterruptedException e) {
            // System.out.println(new Date() + "\t[AVISO]\t" +
            // "Interrompendo OCRParsing of " + input.getPath());
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

        }
        if (output.exists())
            extractOutput(output, xhtml);

    }

    private void extractOutput(File output, XHTMLContentHandler xhtml) throws SAXException, IOException {
        byte[] bytes = Files.readAllBytes(output.toPath());
        String ocrText = new String(bytes, "UTF-8").trim(); //$NON-NLS-1$
        extractOutput(ocrText, xhtml);
    }

    private void extractOutput(String ocrText, XHTMLContentHandler xhtml) throws SAXException, IOException {
        xhtml.characters(ocrText);
    }

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

    public static List<String> checkVersionInfo(String... checkCmd) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(checkCmd);
        int result = process.waitFor();

        if (result != 0) {
            throw new IOException("Returned error code " + result); //$NON-NLS-1$
        }
        try {
            return extractVersion(process.getInputStream());
        } catch (Exception e) {
            return extractVersion(process.getErrorStream());
        }
    }

    private static List<String> extractVersion(InputStream is) throws IOException {
        List<String> lines = IOUtils.readLines(is);
        String version = lines.get(0).replace("tesseract", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
        if (version.startsWith("v")) {
            version = version.substring(1);
        }
        List<String> info = new ArrayList<String>();
        info.add(version);
        for (String libName : librarySupportedTypes.keySet()) {
            for (String l : lines) {
                if (l.contains(libName)) {
                    info.add(libName);
                    break;
                }
            }
        }
        return info;
    }

}
