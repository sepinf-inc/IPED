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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import iped.search.IItemSearcher;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class AlbumFolder extends ShareazaEntity {

    private final XMLElement xml = new XMLElement();
    private final List<AlbumFolder> albumFolders = new ArrayList<>();
    private final List<Integer> albumFileIndexes = new ArrayList<>();
    private String schemaUri;
    private String collSha1;
    private String guid;
    private String name;
    private boolean expanded;
    private boolean autoDelete;
    private String bestView;

    public AlbumFolder() {
        super("ALBUM FOLDER"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        schemaUri = ar.readString();
        if (ar.readCount() != 0) {
            xml.read(ar);
        }
        if (version >= 19) {
            collSha1 = ar.readHash(20);
        }
        if (version >= 24) {
            guid = ar.readHash(16, "guid"); //$NON-NLS-1$
        }
        name = ar.readString();
        expanded = ar.readBool();
        autoDelete = ar.readBool();
        if (version >= 19) {
            bestView = ar.readString();
        }
        int n = ar.readCount();
        for (int i = 0; i < n; i++) {
            AlbumFolder folder = new AlbumFolder();
            folder.read(ar, version);
            albumFolders.add(folder);
        }
        n = ar.readCount();
        for (int i = 0; i < n; i++) {
            int idx = ar.readInt();
            albumFileIndexes.add(idx);
        }
    }

    public void printTable(XHTMLContentHandler html, IItemSearcher searcher, Map<Integer, LibraryFile> fileByIndex, Map<Integer, List<String>> albunsForFiles) throws SAXException {
        for (AlbumFolder folder : albumFolders) {
            folder.printTable(html, searcher, fileByIndex, albunsForFiles);
        }
        for (int idx : albumFileIndexes) {
            if (albunsForFiles.containsKey(idx)) {
                // only print files that are only in Albuns (not in library folders)
                // other files already printed
                LibraryFile file = fileByIndex.get(idx);
                if (file != null) {
                    file.printTableRow(html, "", searcher, albunsForFiles);
                    albunsForFiles.remove(file.getIndex());
                }
            }
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Name: " + name); //$NON-NLS-1$
        f.out("GUID: " + guid); //$NON-NLS-1$
        f.out("Collection SHA1: " + collSha1); //$NON-NLS-1$
        f.out("Schema URI: " + schemaUri); //$NON-NLS-1$
        f.out("Expanded: " + expanded); //$NON-NLS-1$
        f.out("Auto delete: " + autoDelete); //$NON-NLS-1$
        f.out("Best View: " + bestView); //$NON-NLS-1$
        xml.write(f);
        f.out("Files Indexes: " + albumFileIndexes.toString()); //$NON-NLS-1$
        for (AlbumFolder folder : albumFolders) {
            folder.write(f);
        }
    }

    public List<AlbumFolder> getAlbumFolders() {
        return albumFolders;
    }

    public List<Integer> getAlbumFileIndexes() {
        return albumFileIndexes;
    }

    public void collectAlbunsForFiles(Map<Integer, List<String>> albunsForFiles, String path) {
        if (path == null) {
            path = name;
        } else {
            path = path + "/" + name;
        }
        for (int idx : albumFileIndexes) {
            List<String> albuns = albunsForFiles.get(idx);
            if (albuns == null) {
                albuns = new ArrayList<>();
                albunsForFiles.put(idx, albuns);
            }
            albuns.add(path);
        }
        for (AlbumFolder folder : albumFolders) {
            folder.collectAlbunsForFiles(albunsForFiles, path);
        }

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
