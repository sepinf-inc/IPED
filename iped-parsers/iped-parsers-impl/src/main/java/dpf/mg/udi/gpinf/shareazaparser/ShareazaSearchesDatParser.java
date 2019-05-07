/*
 * Copyright 2015-2015, Fabio Melo Pfeifer
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
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
package dpf.mg.udi.gpinf.shareazaparser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser para arquivo Searches.dat do Shareaza
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ShareazaSearchesDatParser extends AbstractParser {

    private static final long serialVersionUID = -6775874100144141162L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("x-shareaza-searches-dat")); //$NON-NLS-1$
    private static final String SEARCH_DAT_MIME_TYPE = "application/x-shareaza-searches-dat"; //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(HttpHeaders.CONTENT_TYPE, SEARCH_DAT_MIME_TYPE);
        metadata.remove(TikaMetadataKeys.RESOURCE_NAME_KEY);

        MFCParser parser = new MFCParser(stream);
        Searches searches = new Searches();
        searches.read(parser);
        ShareazaOutputGenerator out = new ShareazaOutputGenerator();

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("head"); //$NON-NLS-1$
        xhtml.startElement("title"); //$NON-NLS-1$
        xhtml.characters("Shareaza Search.dat"); //$NON-NLS-1$
        xhtml.endElement("title"); //$NON-NLS-1$
        xhtml.endElement("head"); //$NON-NLS-1$
        xhtml.newline();

        xhtml.startElement("body"); //$NON-NLS-1$
        xhtml.startElement("pre"); //$NON-NLS-1$
        searches.write(out);
        xhtml.characters(new String(out.getBytes(), "UTF-8")); //$NON-NLS-1$
        xhtml.endElement("pre"); //$NON-NLS-1$
        xhtml.endElement("body"); //$NON-NLS-1$
        xhtml.endDocument();

    }

}
