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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import iped.search.IItemSearcher;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class LibraryFolders extends ShareazaEntity {

    private final List<LibraryFolder> folders = new ArrayList<>();
    private final AlbumFolder albumRoot = new AlbumFolder();
    private Map<Integer, LibraryFile> indexToFile;

    public LibraryFolders(Map<Integer, LibraryFile> indexToFile) {
        super("LIBRARY FOLDERS"); //$NON-NLS-1$
        this.indexToFile = indexToFile;
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        int n = ar.readCount();
        for (int i = 0; i < n; i++) {
            LibraryFolder folder = new LibraryFolder(null, indexToFile);
            folder.read(ar, version);
            folders.add(folder);
        }
        if (version >= 6) {
            albumRoot.read(ar, version);
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        for (LibraryFolder folder : folders) {
            folder.write(f);
        }
        albumRoot.write(f);
    }

    public void printTable(XHTMLContentHandler html, IItemSearcher searcher) throws SAXException {
        Map<Integer, List<String>> albunsForFiles = new HashMap<>();
        albumRoot.collectAlbunsForFiles(albunsForFiles, null);

        for (LibraryFolder folder : folders) {
            folder.printTable(html, searcher, albunsForFiles);
        }
        albumRoot.printTable(html, searcher, indexToFile, albunsForFiles);
    }

    public List<LibraryFolder> getLibraryFolders() {
        return folders;
    }

    public AlbumFolder getAlbumRoot() {
        return albumRoot;
    }

    public Map<Integer, LibraryFile> getIndexToFile() {
        return indexToFile;
    }

    public Collection<LibraryFile> getOrphanFiles() {
        return indexToFile.values().stream().filter(f -> f.getParentFolder() == null).collect(Collectors.toList());
    }

    public String getName() {
        return albumRoot.getName();
    }
}
