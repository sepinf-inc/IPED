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
class SearchWnd extends ShareazaEntity {

    private final List<ManagedSearch> managedSearches = new ArrayList<>();
    private final BaseMatchSearch baseMatchSearch = new BaseMatchSearch();

    public SearchWnd() {
        super("SEARCH WINDOW"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar) throws IOException {
        /* int version = */ ar.readInt();

        int n = ar.readCount();
        for (int i = 0; i < n; i++) {
            ManagedSearch search = new ManagedSearch();
            search.read(ar);
            managedSearches.add(search);
        }
        baseMatchSearch.read(ar);
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        for (ManagedSearch search : managedSearches) {
            search.write(f);
        }
        baseMatchSearch.write(f);
    }

}
