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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import iped3.search.IItemSearcher;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class AlbumFolder extends ShareazaEntity {

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

    public void printTable(XHTMLContentHandler html, IItemSearcher searcher, Map<Integer, LibraryFile> fileByIndex, String path) throws SAXException {
        if (path == null) {
            path = "[ALBUM]/" + name; //$NON-NLS-1$
        } else {
            path = path + "/" + name; //$NON-NLS-1$
        }
        for (AlbumFolder folder : albumFolders) {
            folder.printTable(html, searcher, fileByIndex, path);
        }
        for (int idx : albumFileIndexes) {
            LibraryFile file = fileByIndex.get(idx);
            if (file != null) {
                file.printTableRow(html, path, searcher);
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

}
