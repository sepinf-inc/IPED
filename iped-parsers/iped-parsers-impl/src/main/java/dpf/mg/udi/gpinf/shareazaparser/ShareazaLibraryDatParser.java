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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import dpf.sp.gpinf.indexer.parsers.util.LedHashes;
import iped3.search.ItemSearcher;
import iped3.util.ExtraProperties;

/**
 * Parser para arquivo Library{1,2}.dat do Shareaza
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ShareazaLibraryDatParser extends AbstractParser {

    private static final long serialVersionUID = -207806473837042332L;
    public static final String LIBRARY_DAT_MIME_TYPE = "application/x-shareaza-library-dat"; //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.parse(LIBRARY_DAT_MIME_TYPE));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(HttpHeaders.CONTENT_TYPE, LIBRARY_DAT_MIME_TYPE);
        metadata.remove(TikaMetadataKeys.RESOURCE_NAME_KEY);

        MFCParser parser = new MFCParser(stream);
        Library library = new Library();
        library.read(parser);
        // ShareazaOutputGenerator out = new ShareazaOutputGenerator();
        for (LibraryFolder folder : library.getLibraryFolders())
            storeSharedHashes(folder, metadata);

        ItemSearcher searcher = context.get(ItemSearcher.class);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("head"); //$NON-NLS-1$
        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters("table {border-collapse: collapse; table-layout: fixed;} " //$NON-NLS-1$
                + "table, td, th {border: 1px solid black; padding: 3px;}" //$NON-NLS-1$
                + "tr.a {background-color:#AAAAEE;} " //$NON-NLS-1$
                + "tr.b {background-color:#E7E7F0;} " //$NON-NLS-1$
                + "tr.r {background-color:#E77770;} " //$NON-NLS-1$
                + "td.a {word-wrap: break-word; text-align: center;} " //$NON-NLS-1$
                + "td.b {word-wrap: break-word; text-align: left;} " //$NON-NLS-1$
                + "td.c {word-wrap: break-word; text-align: right;}"); //$NON-NLS-1$

        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.startElement("title"); //$NON-NLS-1$
        xhtml.characters("Shareaza Library{1,2}.dat"); //$NON-NLS-1$
        xhtml.endElement("title"); //$NON-NLS-1$
        xhtml.endElement("head"); //$NON-NLS-1$
        xhtml.newline();

        xhtml.startElement("body"); //$NON-NLS-1$
        xhtml.startElement("table"); //$NON-NLS-1$
        xhtml.startElement("tr"); //$NON-NLS-1$
        printTh(xhtml, "Path", "Name", "Index", "Size", "Time", "Shared", "VirtualSize", "VirtualBase", "SHA1", "Tiger", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
                "MD5", "ED2K", "BTH", "Verify", "URI", "MetadataAuto", "MetadataTime", "MetadataModified", "Rating", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
                "Comments", "ShareTags", "HitsTotal", "UploadsTotal", "CachedPreview", "Bogus"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        xhtml.endElement("tr"); //$NON-NLS-1$

        library.printTable(xhtml, searcher);

        xhtml.endElement("table"); //$NON-NLS-1$
        /*
         * xhtml.startElement("h2"); xhtml.characters("Dados completos do arquivo:");
         * xhtml.endElement("h2"); xhtml.startElement("pre"); library.write(out);
         * xhtml.characters(new String(out.getBytes(), "UTF-8"));
         * xhtml.endElement("pre");
         */
        xhtml.endElement("body"); //$NON-NLS-1$
        xhtml.endDocument();

        int numRegistros = 0;
        for (LibraryFolder folder : library.getLibraryFolders())
            numRegistros += countLibraryFiles(folder);
        metadata.set(ExtraProperties.P2P_REGISTRY_COUNT, String.valueOf(numRegistros));

        int kffHits = 0;
        for (LibraryFolder folder : library.getLibraryFolders())
            kffHits += countKffHits(folder);

        if (LedHashes.hashMap != null)
            metadata.set(ExtraProperties.WKFF_HITS, Integer.toString(kffHits));

    }

    private void storeSharedHashes(LibraryFolder folder, Metadata metadata) {
        for (LibraryFolder f : folder.getLibraryFolders())
            storeSharedHashes(f, metadata);

        for (LibraryFile file : folder.getLibraryFiles())
            if (file.isShared() && file.getMd5() != null && file.getMd5().length() == 32)
                metadata.add(ExtraProperties.SHARED_HASHES, file.getMd5());

            else if (file.isShared() && file.getSha1() != null && file.getSha1().length() == 40)
                metadata.add(ExtraProperties.SHARED_HASHES, file.getSha1());
    }

    private int countKffHits(LibraryFolder folder) {
        int result = 0;
        for (LibraryFolder f : folder.getLibraryFolders())
            result += countKffHits(f);

        for (LibraryFile file : folder.getLibraryFiles())
            if (file.isKffHit())
                result++;

        return result;
    }

    private int countLibraryFiles(LibraryFolder folder) {
        int result = folder.getLibraryFiles().size();
        for (LibraryFolder f : folder.getLibraryFolders())
            result += countLibraryFiles(f);
        return result;
    }

    private void printTh(XHTMLContentHandler html, Object... thtext) throws SAXException {
        for (Object o : thtext) {
            html.startElement("th"); //$NON-NLS-1$
            html.characters(o.toString());
            html.endElement("th"); //$NON-NLS-1$
        }
    }

}
