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

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class ManagedSearch extends ShareazaEntity {

    private boolean allowG2;
    private boolean allowG1;
    private boolean allowED2K;
    private boolean allowDC;
    private int priority;
    private boolean active;
    private boolean receive;
    private final QuerySearch querySearch = new QuerySearch();

    public ManagedSearch() {
        super("MANAGED SEARCH"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar) throws IOException {
        int version = ar.readInt();
        querySearch.read(ar);
        priority = ar.readInt();
        active = ar.readBool();
        receive = ar.readBool();

        if (version >= 3) {
            allowG2 = ar.readBool();
            allowG1 = ar.readBool();
            allowED2K = ar.readBool();
        }

        if (version >= 4) {
            allowDC = ar.readBool();
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Allow G2: " + allowG2); //$NON-NLS-1$
        f.out("Allow G1: " + allowG1); //$NON-NLS-1$
        f.out("Allow ED2K: " + allowED2K); //$NON-NLS-1$
        f.out("Allow DC: " + allowDC); //$NON-NLS-1$
        f.out("Priority: %d", priority); //$NON-NLS-1$
        f.out("Active: " + active); //$NON-NLS-1$
        f.out("Receive: " + receive); //$NON-NLS-1$
        querySearch.write(f);
    }

}
