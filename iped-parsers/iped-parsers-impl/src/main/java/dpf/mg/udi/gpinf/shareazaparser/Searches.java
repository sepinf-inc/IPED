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

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class Searches extends ShareazaEntity {

    private final List<SearchWnd> searchWindows = new ArrayList<>();

    public Searches() {
        super("SEARCHES"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar) throws IOException {
        while (ar.readCount() == 1) {
            SearchWnd searchWnd = new SearchWnd();
            searchWnd.read(ar);
            searchWindows.add(searchWnd);
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        for (SearchWnd searchWnd : searchWindows) {
            searchWnd.write(f);
        }
    }

}
