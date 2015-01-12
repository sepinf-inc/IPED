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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

public class RARParser extends AbstractParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6157727985054451501L;
	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-rar-compressed"));

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

		TemporaryResources tmp = new TemporaryResources();
		Archive rar = null;
		try {
			TikaInputStream tis = TikaInputStream.get(stream, tmp);
			rar = new Archive(tis.getFile());
			if (rar.isEncrypted()) {
				throw new EncryptedDocumentException();

			} else {

				FileHeader header = rar.nextFileHeader();
				while (header != null && !Thread.currentThread().isInterrupted()) {

					if (header.isDirectory()) {
						header = rar.nextFileHeader();
						continue;
					}

					InputStream subFile = null;
					try {

						subFile = rar.getInputStream(header);

						Metadata entrydata = new Metadata();
						entrydata.set(Metadata.RESOURCE_NAME_KEY, header.getFileNameString().replace("\\", "/"));
						entrydata.set(TikaCoreProperties.CREATED, header.getCTime());
						entrydata.set(TikaCoreProperties.MODIFIED, header.getMTime());

						if (extractor.shouldParseEmbedded(entrydata))
							extractor.parseEmbedded(subFile, handler, entrydata, true);

					} finally {
						if (subFile != null)
							subFile.close();
					}

					header = rar.nextFileHeader();

				}
			}

		} catch (RarException e) {
			throw new TikaException("RARParser Exception", e);

		} finally {
			if (rar != null)
				rar.close();
			tmp.close();
		}

	}

}
