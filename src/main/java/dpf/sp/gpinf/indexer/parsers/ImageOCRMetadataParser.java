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
import java.util.Date;
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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.util.CharCountContentHandler;

public class ImageOCRMetadataParser extends AbstractParser {

	private static final long serialVersionUID = 1L;
	private ImageParser imageParser = new ImageParser();
	private JpegParser jpegParser = new JpegParser();
	private TiffParser tiffParser = new TiffParser();
	private OCRParser ocrParser = new OCRParser();
	private Set<MediaType> SUPPORTED_TYPES = getTypes();

	private static Set<MediaType> getTypes() {
		HashSet<MediaType> supportedTypes = new HashSet<MediaType>();
		supportedTypes.add(MediaType.image("png"));
		supportedTypes.add(MediaType.image("jpeg"));
		supportedTypes.add(MediaType.image("tiff"));
		supportedTypes.add(MediaType.image("x-ms-bmp"));
		supportedTypes.add(MediaType.image("gif"));
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
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		handler.startDocument();
		
		TemporaryResources tmp = new TemporaryResources();
		TikaInputStream tis = TikaInputStream.get(stream, tmp);
		try {
			
			if (OCRParser.ENABLED) {
				handler = new CharCountContentHandler(handler);
				File file = tis.getFile();
				try {
					ocrParser.parse(tis, handler, metadata, context);

				} catch (Exception e) {
					System.out.println(new Date() + "\t[AVISO]\t" + "OCRParser error on '" + file.getPath() + "' (" + file.length() + " bytes)\t" + e.toString());
				}
				tis = TikaInputStream.get(file);
				metadata.set("OCRCharCount", "OCRCharCount" + ((CharCountContentHandler)handler).getCharCount());
			}

			getMetadataParser(metadata, context).parse(tis, handler, metadata, context);

		} finally {
			if (OCRParser.ENABLED)
				tis.close();

			tmp.close();
		}
		handler.endDocument();

	}

}
