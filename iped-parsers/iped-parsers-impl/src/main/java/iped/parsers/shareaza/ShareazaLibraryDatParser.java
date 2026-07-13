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
package iped.parsers.shareaza;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.util.BeanMetadataExtraction;
import iped.parsers.util.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

/**
 * Parser para arquivo Library{1,2}.dat do Shareaza
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ShareazaLibraryDatParser extends AbstractParser {

    private static final long serialVersionUID = -207806473837042332L;
    public static final String LIBRARY_DAT_MIME_TYPE = "application/x-shareaza-library-dat"; //$NON-NLS-1$
    public static final String LIBRARY_DAT_ENTRY_MIME_TYPE = "application/x-shareaza-library-dat-entry"; //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.parse(LIBRARY_DAT_MIME_TYPE));

    private boolean extractEntries = false;

    @Field
    public void setExtractEntries(boolean value) {
        this.extractEntries = value;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(HttpHeaders.CONTENT_TYPE, LIBRARY_DAT_MIME_TYPE);
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        MFCParser parser = new MFCParser(stream);
        Library library = new Library();
        library.read(parser);
        // ShareazaOutputGenerator out = new ShareazaOutputGenerator();
        LibraryFolders folders = library.getLibraryFolders();
        for (LibraryFolder folder : folders.getLibraryFolders())
            storeSharedHashes(folder, metadata);
        storeSharedHashes(folders.getAlbumRoot(), folders.getIndexToFile(), metadata);

        IItemSearcher searcher = context.get(IItemSearcher.class);
        IItemReader item = context.get(IItemReader.class);

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
        xhtml.startElement("body"); //$NON-NLS-1$
        xhtml.newline();

        xhtml.startElement("div");
        xhtml.characters(Messages.getString("P2P.FoundInPedoHashDB"));
        xhtml.endElement("div");

        xhtml.startElement("table"); //$NON-NLS-1$
        xhtml.startElement("tr"); //$NON-NLS-1$
        printTh(xhtml, "Path", "Name", "Albums", "Index", "Size", "Time", "Shared", "VirtualSize", "VirtualBase", "SHA1", "Tiger", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
                "MD5", "ED2K", "BTH", "Verify", "URI", "MetadataAuto", "MetadataTime", "MetadataModified", "Rating", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
                "Comments", "ShareTags", "HitsTotal", "UploadsTotal", "CachedPreview", "Bogus", "Found in Hash Alert Database", "Found in the Case"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
        xhtml.endElement("tr"); //$NON-NLS-1$

        library.printTable(xhtml, searcher);

        metadata.set(BasicProps.HASCHILD, "true");
        metadata.set(ExtraProperties.EMBEDDED_FOLDER, "true");

        if (extractEntries) {
            BeanMetadataExtraction bme = new BeanMetadataExtraction(ExtraProperties.P2P_META_PREFIX, LIBRARY_DAT_ENTRY_MIME_TYPE);
            bme.addPropertyExclusion(LibraryFolders.class, "indexToFile");
            bme.addPropertyExclusion(LibraryFolder.class, "indexToFile");
            bme.addPropertyExclusion(LibraryFolder.class, "parentFolder");
            bme.addPropertyExclusion(LibraryFile.class, "parentFolder");
            bme.addPropertyExclusion(LibraryFile.class, "hashDBHit");
            bme.addPropertyExclusion(LibraryFile.class, "hashSetHits");
            bme.addPropertyExclusion(LibraryFile.class, "sharedSources");
            bme.addPropertyExclusion(LibraryFolder.class, "shared");
            bme.registerCollectionPropertyToMerge(LibraryFolder.class, "libraryFiles");
            bme.registerCollectionPropertyToMerge(LibraryFolder.class, "libraryFolders");
            bme.registerCollectionPropertyToMerge(AlbumFolder.class, "albumFolders");
            bme.registerCollectionPropertyToMerge(AlbumFolder.class, "albumFileIndexes");
            bme.registerClassNameProperty(LibraryFolder.class, "path");
            bme.registerClassNameProperty(AlbumFolder.class, "name");
            bme.registerTransformationMapping(AlbumFolder.class, ExtraProperties.EMBEDDED_FOLDER, Boolean.toString(true));
            bme.registerTransformationMapping(LibraryFolders.class, ExtraProperties.EMBEDDED_FOLDER, Boolean.toString(true));
            bme.registerTransformationMapping(LibraryFolder.class, ExtraProperties.EMBEDDED_FOLDER, Boolean.toString(true));
            bme.registerTransformationMapping(LibraryFile.class, ExtraProperties.LINKED_ITEMS, "${sha1 != null ? \"sha-1:\" + sha1 : null}");
            bme.registerTransformationMapping(LibraryFile.class, ExtraProperties.SHARED_HASHES, "${shared != null && shared ? (md5 != null ? md5 : sha1) : null}");
            bme.registerTransformationMapping(LibraryFile.class, BasicProps.NAME, "Library-Entry-[${name}].dat");

            String albumLibraryFilesQuery = String.format(
                    "path:\"%s\" && p2p\\:index:(${T(org.apache.commons.lang3.StringUtils).join(albumFileIndexes, ',')})",
                    searcher.escapeQuery(item.getPath()));
            bme.registerTransformationMapping(AlbumFolder.class, ExtraProperties.LINKED_ITEMS, albumLibraryFilesQuery);

            bme.extractEmbedded(0, context, metadata, handler, library.getLibraryFolders());
        }

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
        for (LibraryFolder folder : library.getLibraryFolders().getLibraryFolders())
            numRegistros += countLibraryFiles(folder);
        numRegistros += countLibraryFiles(library.getLibraryFolders().getAlbumRoot(), library.getLibraryFolders().getIndexToFile());
        metadata.set(ExtraProperties.P2P_REGISTRY_COUNT, Integer.toString(numRegistros));

        int hashDBHits = countHashDBHits(library.getLibraryFolders());

        if (hashDBHits > 0)
            metadata.set(ExtraProperties.CSAM_HASH_HITS, Integer.toString(hashDBHits));

    }

    private void storeSharedHashes(LibraryFolder folder, Metadata metadata) {
        for (LibraryFolder f : folder.getLibraryFolders())
            storeSharedHashes(f, metadata);

        for (LibraryFile file : folder.getLibraryFiles()) {
            storeSharedHashes(file, metadata);
        }
    }

    private void storeSharedHashes(LibraryFile file, Metadata metadata) {
        if (BooleanUtils.isTrue(file.getShared())) {
            if (file.getMd5() != null && file.getMd5().length() == 32) {
                metadata.add(ExtraProperties.SHARED_HASHES, file.getMd5());
            }
            if (file.getSha1() != null && file.getSha1().length() == 40) {
                metadata.add(ExtraProperties.SHARED_HASHES, file.getSha1());
            }
            if (file.getEd2k() != null && file.getEd2k().length() == 32) {
                metadata.add(ExtraProperties.SHARED_HASHES, file.getEd2k());
            }
        }
    }

    private void storeSharedHashes(AlbumFolder folder, Map<Integer, LibraryFile> indexToFile, Metadata metadata) {
        for (AlbumFolder f : folder.getAlbumFolders()) {
            storeSharedHashes(f, indexToFile, metadata);
        }

        for (int idx : folder.getAlbumFileIndexes()) {
            LibraryFile file = indexToFile.get(idx);
            if (file != null) {
                storeSharedHashes(file, metadata);
            }
        }
    }

    private int countHashDBHits(LibraryFolders folders) {
        Map<Integer, LibraryFile> indexToFile = folders.getIndexToFile();
        int result = 0;
        Set<Integer> indexesCounted = new HashSet<>();
        for (LibraryFolder f : folders.getLibraryFolders())
            result += countHashDBHits(f, indexesCounted);
        result += countHashDBHits(folders.getAlbumRoot(), indexToFile, indexesCounted);

        return result;
    }

    private int countHashDBHits(LibraryFolder folder, Set<Integer> indexesCounted) {
        int result = 0;
        for (LibraryFolder f : folder.getLibraryFolders())
            result += countHashDBHits(f, indexesCounted);

        for (LibraryFile file : folder.getLibraryFiles())
            if (file.isHashDBHit() && !indexesCounted.contains(file.getIndex())) {
                result++;
                indexesCounted.add(file.getIndex());
            }

        return result;
    }

    private int countHashDBHits(AlbumFolder folder, Map<Integer, LibraryFile> indexToFile, Set<Integer> indexesCounted) {
        int result = 0;
        for (AlbumFolder f : folder.getAlbumFolders())
            result += countHashDBHits(f, indexToFile, indexesCounted);

        for (int idx : folder.getAlbumFileIndexes()) {
            LibraryFile file = indexToFile.get(idx);
            if (file != null) {
                if (file.isHashDBHit() && !indexesCounted.contains(idx)) {
                    result++;
                    indexesCounted.add(idx);
                }
            }
        }
        return result;
    }

    private int countLibraryFiles(LibraryFolder folder) {
        int result = folder.getLibraryFiles().size();
        for (LibraryFolder f : folder.getLibraryFolders())
            result += countLibraryFiles(f);
        return result;
    }

    private int countLibraryFiles(AlbumFolder folder, Map<Integer, LibraryFile> indexToFile) {
        int result = 0;
        for (AlbumFolder f : folder.getAlbumFolders())
            result += countLibraryFiles(f, indexToFile);

        for (int idx : folder.getAlbumFileIndexes()) {
            if (indexToFile.containsKey(idx))
                result++;
        }
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
