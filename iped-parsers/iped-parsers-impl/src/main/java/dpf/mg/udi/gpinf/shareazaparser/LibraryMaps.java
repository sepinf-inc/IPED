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

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class LibraryMaps extends ShareazaEntity {

    private final List<LibraryFile> libraryFiles = new ArrayList<>();
    private long nextIndex;
    private long indexMapCount;
    private long nameMapCount;
    private long pathMapCount;

    public LibraryMaps() {
        super("LIBRARY MAPS"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        nextIndex = ar.readUInt();
        if (version >= 28) {
            indexMapCount = ar.readUInt();
            nameMapCount = ar.readUInt();
            pathMapCount = ar.readUInt();
        }
    }

    public void readExtra(MFCParser ar, int version, Map<Integer, LibraryFile> indexToFile) throws IOException {
        if (version >= 18) {
            int n = ar.readCount();
            for (int i = 0; i < n; i++) {
                LibraryFile file = new LibraryFile(null);
                file.read(ar, version);
                libraryFiles.add(file);
                indexToFile.put(file.getIndex(), file);
            }
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Next Index: " + nextIndex); //$NON-NLS-1$
        f.out("Index Map Count: " + indexMapCount); //$NON-NLS-1$
        f.out("Name Map Count: " + nameMapCount); //$NON-NLS-1$
        f.out("Path Map Count: " + pathMapCount); //$NON-NLS-1$
        for (LibraryFile file : libraryFiles) {
            file.write(f);
        }
    }

}
