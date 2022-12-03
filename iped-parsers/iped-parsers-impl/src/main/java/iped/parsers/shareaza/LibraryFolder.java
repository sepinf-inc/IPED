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
public class LibraryFolder extends ShareazaEntity {

    private final List<LibraryFolder> folders = new ArrayList<>();
    private final List<LibraryFile> files = new ArrayList<>();
    private long nFiles = 0;
    private long nVolume = 0;
    private String path;
    private String shared;
    private boolean expanded;
    private final LibraryFolder parentFolder;
    private Map<Integer, LibraryFile> indexToFile;

    public LibraryFolder(LibraryFolder parentFolder, Map<Integer, LibraryFile> indexToFile) {
        super("LIBRARY FOLDER"); //$NON-NLS-1$
        this.parentFolder = parentFolder;
        this.indexToFile = indexToFile;
    }

    public String getInheritedShared() {
        String resp = shared;
        if (resp.equals("Unknown") && parentFolder != null) { //$NON-NLS-1$
            resp = parentFolder.getInheritedShared();
        }
        return resp;
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        path = ar.readString();
        if (version >= 5) {
            shared = Util.decodeTriState(ar.readInt());
        } else {
            if (ar.readByte() == 0) {
                shared = "False"; //$NON-NLS-1$
            } else {
                shared = "Unknown"; //$NON-NLS-1$
            }
        }
        if (version >= 3) {
            expanded = ar.readBool();
        }
        int n = ar.readCount();
        for (int i = 0; i < n; i++) {
            LibraryFolder folder = new LibraryFolder(this, indexToFile);
            folder.read(ar, version);
            folders.add(folder);
            nFiles += folder.nFiles;
            nVolume += folder.nVolume;
        }
        n = ar.readCount();
        for (int i = 0; i < n; i++) {
            LibraryFile file = new LibraryFile(this);
            file.read(ar, version);
            files.add(file);
            nFiles++;
            nVolume += file.getSize();
            if (indexToFile != null) {
                indexToFile.put(file.getIndex(), file);
            }
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Files: " + nFiles); //$NON-NLS-1$
        f.out("Volume: " + nVolume); //$NON-NLS-1$
        f.out("Path: " + path); //$NON-NLS-1$
        f.out("Shared: " + getInheritedShared()); //$NON-NLS-1$
        f.out("Expanded: " + expanded); //$NON-NLS-1$
        for (LibraryFolder folder : folders) {
            folder.write(f);
        }
        for (LibraryFile file : files) {
            file.write(f);
        }
    }

    public void printTable(XHTMLContentHandler html, IItemSearcher searcher, Map<Integer, List<String>> albunsForFiles) throws SAXException {
        for (LibraryFolder folder : folders) {
            folder.printTable(html, searcher, albunsForFiles);
        }
        for (LibraryFile file : files) {
            file.printTableRow(html, path, searcher, albunsForFiles);
            albunsForFiles.remove(file.getIndex());
        }
    }

    public List<LibraryFolder> getLibraryFolders() {
        return folders;
    }

    public List<LibraryFile> getLibraryFiles() {
        return files;
    }

    public String getPath() {
        return path;
    }
}
