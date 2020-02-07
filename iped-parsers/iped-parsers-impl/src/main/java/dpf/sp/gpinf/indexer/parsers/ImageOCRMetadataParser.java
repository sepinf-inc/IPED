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
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.parser.jpeg.JpegParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.CharCountContentHandler;

/**
 * Parser para imagens. Extrair metadados via parsers padrão e extrair texto via
 * OCR, caso habilitado.
 * 
 * @author Nassif
 *
 */
public class ImageOCRMetadataParser extends AbstractParser {
    private static Logger LOGGER = LoggerFactory.getLogger(ImageOCRMetadataParser.class);

    private static final long serialVersionUID = 1L;
    private final ImageParser imageParser = new ImageParser();
    private final JpegParser jpegParser = new JpegParser();
    private final TiffParser tiffParser = new TiffParser();
    private final OCRParser ocrParser = new OCRParser();
    private final Set<MediaType> SUPPORTED_TYPES = getTypes();

    private static Set<MediaType> getTypes() {
        HashSet<MediaType> supportedTypes = new HashSet<MediaType>();
        supportedTypes.add(MediaType.image("png")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("jpeg")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("tiff")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("bmp")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("gif")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("jp2")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("jpx")); //$NON-NLS-1$
        supportedTypes.add(MediaType.image("x-portable-pixmap")); //$NON-NLS-1$
        return supportedTypes;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private Parser getMetadataParser(Metadata metadata, ParseContext context) {
        MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
        if (imageParser.getSupportedTypes(context).contains(type))
            return imageParser;
        else if (jpegParser.getSupportedTypes(context).contains(type))
            return jpegParser;
        else if (tiffParser.getSupportedTypes(context).contains(type))
            return tiffParser;
        else
            return new EmptyParser();
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        handler.startDocument();
        CharCountContentHandler countHandler = new CharCountContentHandler(handler);
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        try {
            if (ocrParser.isEnabled()) {
                File file = tis.getFile();
                try {
                    ocrParser.parse(tis, countHandler, metadata, context);

                } catch (IOException | SAXException | TikaException e) {
                    LOGGER.warn("OCRParser error on '{}' ({} bytes)\t{}", file.getPath(), file.length(), e.toString()); //$NON-NLS-1$
                }
                tis = TikaInputStream.get(file);
                metadata.set(OCRParser.OCR_CHAR_COUNT, countHandler.getCharCount() + ""); //$NON-NLS-1$
            }

            getMetadataParser(metadata, context).parse(tis, countHandler, metadata, context);

        } finally {
            if (ocrParser.isEnabled())
                tis.close();
            tmp.close();
            handler.endDocument();
        }

    }

}
