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
import java.util.Set;
import java.util.TreeSet;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp4.MP4Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;

/**
 * Extrai conteúdo vazio para vídeos sem parser específico, enquanto nao é
 * implementada extração de metadados para eles.
 */
public class EmptyVideoParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static final Set<MediaType> SUPPORTED_TYPES = getTypes();

    private static Set<MediaType> getTypes() {
        Set<MediaType> supportedTypes = new TreeSet<MediaType>();

        Set<MediaType> videosWithParser = new TreeSet<>();
        videosWithParser.addAll(new MP4Parser().getSupportedTypes(null));
        videosWithParser.add(MediaType.video("x-flv"));

        for (MediaType type : MediaTypeRegistry.getDefaultRegistry().getTypes()) {
            type = type.getBaseType();
            if (MetadataUtil.isVideoType(type) && !videosWithParser.contains(type)) {
                supportedTypes.add(type);
            }
        }
        return supportedTypes;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {

        return SUPPORTED_TYPES;
    }

// commenting this section. Tests are made using JUnit now.
// Teste
//    public static void main(String[] args) {
//        EmptyVideoParser parser = new EmptyVideoParser();
//        Set<MediaType> types = parser.getSupportedTypes(null);
//        for (MediaType type : types)
//            System.out.println(type.getBaseType());
//    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();

    }

}
