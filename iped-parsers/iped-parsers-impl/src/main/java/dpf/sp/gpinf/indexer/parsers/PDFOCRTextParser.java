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
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
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

import dpf.sp.gpinf.indexer.parsers.util.CharCountContentHandler;

/**
 * Parser para arquivos PDF. Chama o parser OCR caso habilitado se o PDF tiver
 * pouco texto, provavelmente por conter imagens digitalizadas. Também permite
 * processar o PDF com e sem ordenação dos caracteres ao mesmo tempo.
 * 
 * @author Nassif
 *
 */
public class PDFOCRTextParser extends PDFParser {

    private static Logger LOGGER = LoggerFactory.getLogger(PDFOCRTextParser.class);

    private static AtomicBoolean checked = new AtomicBoolean();

    public static final String MAX_CHARS_TO_OCR = "pdfparser.maxCharsToOcr"; //$NON-NLS-1$
    public static final String SORT_PDF_CHARS = "pdfparser.sortPdfChars"; //$NON-NLS-1$
    public static final String PROCESS_INLINE_IMAGES = "pdfparser.processInlineImages"; //$NON-NLS-1$

    private int maxCharsToOcr = Integer.valueOf(System.getProperty(MAX_CHARS_TO_OCR, "100")); //$NON-NLS-1$
    private boolean sortPDFChars = Boolean.valueOf(System.getProperty(SORT_PDF_CHARS, "false")); //$NON-NLS-1$
    private boolean processEmbeddedImages = Boolean.valueOf(System.getProperty(PROCESS_INLINE_IMAGES, "false")); //$NON-NLS-1$

    private boolean useIcePDFParsing = false;
    private int maxMainMemoryBytes = 100000000;

    private static final long serialVersionUID = 1L;
    private static MediaType PDF_TYPE = MediaType.application("pdf"); //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PDF_TYPE);
    private PDFParser SortedPDFParser, PDFParser;
    private OCRParser ocrParser = new OCRParser();
    private PDFParserConfig defaultConfig = new PDFParserConfig();

    public PDFOCRTextParser() {
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
                        + " and put it in optional_jars folder. Warn: that plugin is worse to decode JPX outside of PDFs!"); //$NON-NLS-1$
        }
    }

    @Field
    public void setUseIcePDF(boolean value) {
        this.useIcePDFParsing = value;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }
    

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(HttpHeaders.CONTENT_TYPE, "application/pdf"); //$NON-NLS-1$

        handler.startDocument();
        CharCountContentHandler countHandler = new CharCountContentHandler(handler);

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            File file = null;
            if (ocrParser.isEnabled())
                file = tis.getFile();

            int numPages = 0;

            if (useIcePDFParsing) {
                numPages = icePDFParse(tis, countHandler, metadata);

            } else {
                if (sortPDFChars) {
                    file = tis.getFile();
                    try {
                        SortedPDFParser.parse(tis, countHandler, new Metadata(), context);

                    } finally {
                        tis = TikaInputStream.get(file);
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

                } finally {
                    if (sortPDFChars)
                        tis.close();
                }

                if (metadata.get("xmpTPg:NPages") != null) //$NON-NLS-1$
                    numPages = Integer.parseInt(metadata.get("xmpTPg:NPages")); //$NON-NLS-1$
            }

            if (numPages == 0)
                if (metadata.get(Metadata.RESOURCE_NAME_KEY).startsWith("Carved")) //$NON-NLS-1$
                    throw new TikaException("PDF document contains zero pages"); //$NON-NLS-1$
                else
                    numPages = 1;

            int charCount = countHandler.getCharCount();
            metadata.set(Metadata.CHARACTER_COUNT, charCount);

            if (ocrParser.isEnabled() && !processEmbeddedImages && charCount / numPages <= maxCharsToOcr) {
                tis = TikaInputStream.get(file);
                try {
                    ocrParser.parse(tis, countHandler, metadata, context);

                } catch (Exception e) {
                    LOGGER.warn("OCRParser error on '{}' ({} bytes)\t{}", file.getPath(), file.length(), e.toString()); //$NON-NLS-1$

                } finally {
                    tis.close();
                }
                metadata.set(OCRParser.OCR_CHAR_COUNT, (countHandler.getCharCount() - charCount) + ""); //$NON-NLS-1$
            }

        } finally {
            tmp.close();
            handler.endDocument();
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
                    metadata.add(Metadata.SUBJECT, value);
                value = info.getTitle();
                if (value != null)
                    metadata.add(Metadata.TITLE, value);
                value = info.getAuthor();
                if (value != null)
                    metadata.add(Metadata.AUTHOR, value);
                value = info.getCreator();
                if (value != null)
                    metadata.add(Metadata.CREATOR, value);
                value = info.getProducer();
                if (value != null)
                    metadata.add(Metadata.APPLICATION_NAME, value);
                value = info.getKeywords();
                if (value != null)
                    metadata.add(Metadata.KEYWORDS, value);
                PDate date = info.getCreationDate();
                if (date != null)
                    metadata.add(Metadata.CREATION_DATE, date.toString());
                date = info.getModDate();
                if (date != null)
                    metadata.add(Metadata.MODIFIED, date.toString());

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
