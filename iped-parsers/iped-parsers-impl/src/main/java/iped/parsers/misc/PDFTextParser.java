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
package iped.parsers.misc;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.exception.ZipBombException;
import iped.parsers.fork.EmbeddedDocumentParser;
import iped.parsers.misc.PdfSignatureExtractor.SignatureInfo;
import iped.parsers.ocr.OCRParser;
import iped.parsers.security.SignatureMetadata;
import iped.parsers.util.CharCountContentHandler;
import iped.parsers.util.ComputeThumb;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.PDFToThumb;
import iped.properties.ExtraProperties;

/**
 * Parser para arquivos PDF. Chama o parser OCR caso habilitado se o PDF tiver
 * pouco texto, provavelmente por conter imagens digitalizadas. Também permite
 * processar o PDF com e sem ordenação dos caracteres ao mesmo tempo.
 * 
 * @author Nassif
 *
 */
public class PDFTextParser extends PDFParser {

    private static Logger LOGGER = LoggerFactory.getLogger(PDFTextParser.class);

    private static AtomicBoolean checked = new AtomicBoolean();

    public static final String MAX_CHARS_TO_OCR = "pdfparser.maxCharsToOcr"; //$NON-NLS-1$
    public static final String SORT_PDF_CHARS = "pdfparser.sortPdfChars"; //$NON-NLS-1$
    public static final String PROCESS_INLINE_IMAGES = "pdfparser.processInlineImages"; //$NON-NLS-1$
    public static final String CREATE_THUMB = "pdfparser.createThumb"; //$NON-NLS-1$
    public static final String THUMB_SIZE = "pdfparser.thumbSize"; //$NON-NLS-1$

    private int maxCharsToOcr = Integer.valueOf(System.getProperty(MAX_CHARS_TO_OCR, "100")); //$NON-NLS-1$
    private boolean sortPDFChars = Boolean.valueOf(System.getProperty(SORT_PDF_CHARS, "false")); //$NON-NLS-1$
    private boolean processEmbeddedImages = Boolean.valueOf(System.getProperty(PROCESS_INLINE_IMAGES, "false")); //$NON-NLS-1$

    private boolean useIcePDFParsing = false;
    private boolean extractSignatures = true;
    private boolean extractSignatureFiles = true;
    private int maxMainMemoryBytes = 100000000;

    static {
        SignatureMetadata.init();
    }

    private static final long serialVersionUID = 1L;

    public static final MediaType PDF_TYPE = MediaType.application("pdf"); //$NON-NLS-1$

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PDF_TYPE);
    private PDFParser SortedPDFParser, PDFParser;
    private OCRParser ocrParser = new OCRParser();
    private PDFParserConfig defaultConfig = new PDFParserConfig();

    public PDFTextParser() {
        if (PDFParser == null) {
            PDFParser = new PDFParser();
            SortedPDFParser = new PDFParser();
            SortedPDFParser.setSortByPosition(true);
        }
        if (!checked.getAndSet(true)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("jp2"); //$NON-NLS-1$
            ImageReader reader = null;
            while (readers.hasNext()) {
                reader = readers.next();
                if (reader != null && reader.canReadRaster())
                    break;
                else
                    reader = null;
            }
            if (reader == null)
                LOGGER.warn("Plugin JPEG2000 not found, JPX images will not be decoded from PDFs." //$NON-NLS-1$
                        + " You can download it from https://mvnrepository.com/artifact/com.github.jai-imageio/jai-imageio-jpeg2000/1.3.0" //$NON-NLS-1$
                        + " and put it in plugins folder. Warn: that plugin is worse to decode JPX outside of PDFs!"); //$NON-NLS-1$
        }
    }

    @Field
    public void setUseIcePDF(boolean value) {
        this.useIcePDFParsing = value;
    }

    /**
     * Extracts digital signature metadata, see {@link PdfSignatureExtractor}.
     */
    @Field
    public void setExtractSignatures(boolean value) {
        this.extractSignatures = value;
    }

    /**
     * When extracting signatures, also extracts each signature as a .p7s subitem
     * with its own fields. Only takes effect when PDFs are expanded (PDF Documents
     * in CategoriesToExpand.txt), since otherwise IPED would parse the subitems
     * into the PDF text.
     */
    @Field
    public void setExtractSignatureFiles(boolean value) {
        this.extractSignatureFiles = value;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }
    

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(HttpHeaders.CONTENT_TYPE, "application/pdf"); //$NON-NLS-1$
        
        ItemInfo itemInfo = context.get(ItemInfo.class);

        handler.startDocument();
        CharCountContentHandler countHandler = new CharCountContentHandler(handler);

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            File file = null;
            Exception exception = null;
            boolean createThumb = Boolean.valueOf(System.getProperty(CREATE_THUMB)) && context.get(ComputeThumb.class) != null;
            // Signature verification reads the signed byte ranges from a file. When the
            // stream has no file but the IItemReader in the context is this PDF, the file is
            // only requested later, for the PDFs that turn out to be signed. Decided before
            // parsing, while the metadata is still the one the caller passed.
            IItemReader item = contextItem(context, metadata);
            boolean signatureFileLater = extractSignatures && !tis.hasFile() && item != null;
            if (ocrParser.isEnabled() || createThumb || (extractSignatures && !signatureFileLater))
                file = tis.getFile();

            int numPages = 0;

            if (useIcePDFParsing) {
                numPages = icePDFParse(tis, countHandler, metadata);

            } else {
                if (sortPDFChars) {
                    file = tis.getFile();
                    try {
                        SortedPDFParser.parse(tis, countHandler, new Metadata(), context);

                    } catch (Exception e) {
                        exception = e;
                    } finally {
                        tis = TikaInputStream.get(file.toPath());
                    }
                }

                PDFParserConfig config = context.get(PDFParserConfig.class, defaultConfig);
                config.setExtractInlineImages(processEmbeddedImages);
                config.setCatchIntermediateIOExceptions(true);
                config.setExtractActions(true);
                config.setExtractAnnotationText(true);
                config.setExtractBookmarksText(true);
                config.setMaxMainMemoryBytes(maxMainMemoryBytes);
                config.setDetectAngles(true);
                context.set(PDFParserConfig.class, config);

                try {
                    PDFParser.parse(tis, countHandler, metadata, context);

                } catch (Exception e) {
                    if (exception == null)
                        exception = e;
                } finally {
                    if (sortPDFChars)
                        tis.close();
                }

                if (metadata.get("xmpTPg:NPages") != null) //$NON-NLS-1$
                    numPages = Integer.parseInt(metadata.get("xmpTPg:NPages")); //$NON-NLS-1$
            }

            if (numPages == 0)
                if (metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY).startsWith("Carved")) //$NON-NLS-1$
                    throw new TikaException("PDF document contains zero pages"); //$NON-NLS-1$
                else
                    numPages = 1;

            if (extractSignatures) {
                // With catchIntermediateIOExceptions, an error while walking the AcroForm is
                // only recorded as a warning, so its signature fields may not have been seen.
                // Signatures live in AcroForm fields, which Tika reports separately.
                boolean acroFormWarning = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length > 0
                        && Boolean.parseBoolean(metadata.get(PDF.HAS_ACROFORM_FIELDS));
                boolean tikaMayHaveMissed = useIcePDFParsing || exception != null || acroFormWarning;
                extractSignatures(file, item, metadata, tikaMayHaveMissed, handler, context);
            }

            int charCount = countHandler.getCharCount();
            metadata.set(Office.CHARACTER_COUNT, charCount);

            if (ocrParser.isEnabled() && !processEmbeddedImages && charCount / numPages <= maxCharsToOcr) {
                tis = TikaInputStream.get(file.toPath());
                try {
                    ocrParser.parse(tis, countHandler, metadata, context);

                } catch (Exception e) {
                    LOGGER.warn("OCRParser error on '{}' ({} bytes)\t{}", itemInfo.getPath(), file.length(), e.toString()); //$NON-NLS-1$
                    LOGGER.debug("", e);
                } finally {
                    tis.close();
                }
            }
            
            if (createThumb) {
                byte[] thumb = null;
                try (PDFToThumb pdfToThumb = new PDFToThumb()) {
                    int thumbSize = Integer.valueOf(System.getProperty(THUMB_SIZE));
                    BufferedImage img = pdfToThumb.getPdfThumb(file, thumbSize);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    if (img != null) {
                        ImageIO.write(img, "jpg", baos);
                    }
                    thumb = baos.toByteArray();
                } catch (Throwable t) {
                    thumb = new byte[0];
                    LOGGER.warn("PDF thumb error on '{}' ({} bytes)\t{}", itemInfo.getPath(), file.length(), t.toString()); //$NON-NLS-1$
                    LOGGER.debug("", t);
                } finally {
                    metadata.set(ExtraProperties.THUMBNAIL_BASE64, Base64.getEncoder().encodeToString(thumb));
                }
            }

            if (exception instanceof IOException) {
                throw (IOException) exception;
            } else if (exception instanceof TikaException) {
                throw (TikaException) exception;
            } else if (exception instanceof SAXException) {
                throw (SAXException) exception;
            } else if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }

        } finally {
            tmp.close();
            handler.endDocument();
        }

    }

    /**
     * Adds signature metadata. Runs only on documents Tika flagged as signed, on
     * XFA forms (Tika does not walk their AcroForm fields, so it never flags them),
     * or, when Tika did not parse the document, on documents with a /ByteRange
     * entry, so unsigned PDFs are not loaded again. Errors are logged and never
     * stop the parsing of the PDF.
     */
    private void extractSignatures(File file, IItemReader item, Metadata metadata, boolean tikaMayHaveMissed,
            ContentHandler handler, ParseContext context) throws ZipBombException {
        ItemInfo itemInfo = context.get(ItemInfo.class);
        File pdf = file;
        try {
            boolean flagged = Boolean.parseBoolean(metadata.get(TikaCoreProperties.HAS_SIGNATURE));
            boolean xfa = Boolean.parseBoolean(metadata.get(PDF.HAS_XFA));
            if (!flagged && !xfa && !tikaMayHaveMissed) {
                metadata.set(SignatureMetadata.COUNT, "0");
                return;
            }
            // Signers write /ByteRange uncompressed, to fill it in after signing. A later
            // compressed rewrite could hide it, so no count is written when it is not found.
            if (!flagged) {
                boolean found;
                if (pdf != null) {
                    found = PdfSignatureExtractor.mayContainSignature(pdf);
                } else {
                    // no file only when the context item is this PDF (signatureFileLater):
                    // scan its stream, the temp file is only needed if /ByteRange is there
                    try (InputStream in = item.getBufferedInputStream()) {
                        found = PdfSignatureExtractor.mayContainSignature(in);
                    }
                }
                if (!found) {
                    return;
                }
            }
            if (pdf == null) {
                pdf = item.getTempFile();
                if (pdf == null) {
                    LOGGER.warn("No file to read the digital signatures of '{}'", //$NON-NLS-1$
                            itemInfo != null ? itemInfo.getPath() : null);
                    return;
                }
            }
            List<SignatureInfo> signatures;
            try (PDDocument document = PDDocument.load(pdf, MemoryUsageSetting.setupMixed(maxMainMemoryBytes))) {
                signatures = new PdfSignatureExtractor().extract(document, pdf);
            }
            PdfSignatureExtractor.addMetadata(signatures, metadata);
            // after closing the document: extracting subitems may wait on IPED's queue
            // Subitems only for a PDF that is itself an item: a PDF parsed inline, inside a
            // container that is not expanded, has its children parsed into its text.
            if (extractSignatureFiles && item != null) {
                extractSignatureFiles(signatures, handler, context);
            }
        } catch (ZipBombException e) {
            // thrown by IPED when extracting the .p7s subitems to abort the parent parse
            throw e;
        } catch (InvalidPasswordException e) {
            // encrypted with a user password: already reported by the PDF parsing
            LOGGER.debug("Encrypted PDF, signatures not extracted", e);
        } catch (Exception e) {
            String path = itemInfo != null ? itemInfo.getPath() : null;
            LOGGER.warn("Error extracting PDF signatures from '{}' ({} bytes)\t{}", path, //$NON-NLS-1$
                    pdf != null ? pdf.length() : null, e.toString());
            LOGGER.debug("", e);
        }
    }

    /**
     * The IItemReader IPED puts in the context, if it is the PDF being parsed, or
     * null. Documents parsed inline share their parent's context, so both name and
     * length must match (ParsingTask sets both in the item metadata).
     */
    private static IItemReader contextItem(ParseContext context, Metadata metadata) {
        IItemReader item = context.get(IItemReader.class);
        if (item == null || item.getLength() == null) {
            return null;
        }
        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        String length = metadata.get(Metadata.CONTENT_LENGTH);
        boolean same = name != null && name.equals(item.getName()) && length != null
                && length.equals(item.getLength().toString());
        return same ? item : null;
    }

    private void extractSignatureFiles(List<SignatureInfo> signatures, ContentHandler handler, ParseContext context)
            throws IOException, SAXException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        // When PDFs are not expanded (the default CategoriesToExpand), IPED parses
        // embedded documents into the parent text instead of creating subitems: that
        // would add raw CMS strings to the text of every signed PDF. The signature
        // fields are already on the PDF item, so the files are only extracted when they
        // become subitems.
        if (extractor instanceof EmbeddedDocumentParser) {
            return;
        }
        for (SignatureInfo info : signatures) {
            // a reserved signature that was never filled has only zero padding
            byte[] cms = info.contents == null ? null : PdfSignatureExtractor.cmsBytes(info);
            if (cms == null || cms.length == 0) {
                continue;
            }
            Metadata subitemMetadata = PdfSignatureExtractor.subitemMetadata(info);
            if (extractor.shouldParseEmbedded(subitemMetadata)) {
                try (InputStream in = new ByteArrayInputStream(cms)) {
                    extractor.parseEmbedded(in, handler, subitemMetadata, false);
                }
            }
        }
    }

    /*
     * Parsing alternativo utilizando outra biblioteca (IcePDF)
     */
    private int icePDFParse(TikaInputStream tis, ContentHandler handler, Metadata metadata) throws TikaException {

        Document iceDoc = null;
        try {
            iceDoc = new Document();
            iceDoc.setFile(tis.getFile().getAbsolutePath());

            int numPages = iceDoc.getNumberOfPages();
            for (int i = 0; i < numPages; i++) {
                try {
                    PageText pageText = iceDoc.getPageText(i);
                    if (pageText != null) {
                        char[] chars = ("\nPage " + i + "\n").toCharArray(); //$NON-NLS-1$ //$NON-NLS-2$
                        handler.characters(chars, 0, chars.length);
                        chars = pageText.toString().toCharArray();
                        handler.characters(chars, 0, chars.length);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try {
                PInfo info = iceDoc.getInfo();
                String value = info.getSubject();
                if (value != null)
                    metadata.add(TikaCoreProperties.SUBJECT, value);
                value = info.getTitle();
                if (value != null)
                    metadata.add(TikaCoreProperties.TITLE, value);
                value = info.getAuthor();
                if (value != null)
                    metadata.add(TikaCoreProperties.CREATOR, value);
                value = info.getCreator();
                if (value != null)
                    metadata.add(TikaCoreProperties.CREATOR, value);
                value = info.getProducer();
                if (value != null)
                    metadata.add(TikaCoreProperties.CREATOR_TOOL, value);
                value = info.getKeywords();
                if (value != null)
                    metadata.add(Office.KEYWORDS, value);
                PDate date = info.getCreationDate();
                if (date != null)
                    metadata.add(TikaCoreProperties.CREATED, date.toString());
                date = info.getModDate();
                if (date != null)
                    metadata.add(TikaCoreProperties.MODIFIED, date.toString());

            } catch (Exception e) {
                e.printStackTrace();
            }

            return numPages;

        } catch (Exception e) {
            e.printStackTrace();
            throw new TikaException("IcePDF Exception", e); //$NON-NLS-1$

        } finally {
            if (iceDoc != null)
                iceDoc.dispose();
        }

    }

}
