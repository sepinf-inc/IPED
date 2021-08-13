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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import iped3.search.IItemSearcher;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class Library extends ShareazaEntity {

    /* private String time; */
    private int version;
    private final LibraryDictionary dictionary = new LibraryDictionary();
    private final LibraryMaps maps;
    private final Map<Integer, LibraryFile> indexToFile;
    private final LibraryFolders folders;
    private final LibraryHistory history;

    public Library() {
        super("LIBRARY"); //$NON-NLS-1$
        indexToFile = new HashMap<>();
        maps = new LibraryMaps();
        folders = new LibraryFolders(indexToFile);
        history = new LibraryHistory();
    }

    @Override
    public void read(MFCParser ar) throws IOException {
        /* time = */ar.readLong();
        version = ar.readInt();
        dictionary.read(ar, version);
        maps.read(ar, version);
        folders.read(ar, version);
        history.read(ar, version);
        maps.readExtra(ar, version, indexToFile);
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Version: " + version); //$NON-NLS-1$
        f.out("Time: ##TODO: decode FILETIME struct"); //$NON-NLS-1$
        folders.write(f);
        history.write(f);
        maps.write(f);
        dictionary.write(f);
    }

    public void printTable(XHTMLContentHandler html, IItemSearcher searcher) throws SAXException {
        folders.printTable(html, searcher);
    }

    public List<LibraryFolder> getLibraryFolders() {
        return folders.getLibraryFolders();
    }

}
