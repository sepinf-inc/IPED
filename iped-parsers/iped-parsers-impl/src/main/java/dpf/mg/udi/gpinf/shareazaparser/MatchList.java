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
class MatchList extends ShareazaEntity {

    private final List<MatchFile> files = new ArrayList<>();
    private String sFilter;
    private boolean filterBusy;
    private boolean filterPush;
    private boolean filterUnstable;
    private boolean filterReject;
    private boolean filterLocal;
    private boolean filterBogus;
    private boolean filterDRM;
    private boolean filterAdult;
    private boolean filterSuspicious;
    private boolean bRegExp;
    private long filterMinSize;
    private long filterMaxSize;
    private long filterSources;
    private int sortColumn;
    private boolean sortDir;
    private int nFiles;

    public MatchList() {
        super("MATCH LIST"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar) throws IOException {
        int version = ar.readInt();
        if (version < 8) {
            throw new IOException("Unsupported version"); //$NON-NLS-1$
        }
        sFilter = ar.readString();
        filterBusy = ar.readBool();
        filterPush = ar.readBool();
        filterUnstable = ar.readBool();
        filterReject = ar.readBool();
        filterLocal = ar.readBool();
        filterBogus = ar.readBool();

        if (version >= 12) {
            filterDRM = ar.readBool();
            filterAdult = ar.readBool();
            filterSuspicious = ar.readBool();
            bRegExp = ar.readBool();
        }

        if (version >= 10) {
            filterMinSize = ar.readLong();
            filterMaxSize = ar.readLong();
        } else {
            filterMinSize = ar.readUInt();
            filterMaxSize = ar.readUInt();
        }

        filterSources = ar.readUInt();
        sortColumn = ar.readInt();
        sortDir = ar.readBool();

        nFiles = ar.readCount();
        for (int i = 0; i < nFiles; i++) {
            MatchFile file = new MatchFile();
            file.read(ar, version);
            files.add(file);
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Filter String: " + sFilter); //$NON-NLS-1$
        f.out("Filter .. Busy: %s, Push: %s, Unstable: %s, Reject: %s, Local: %s, Bogus: %s", //$NON-NLS-1$
                "" + filterBusy, "" + filterPush, "" + filterUnstable, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "" + filterReject, "" + filterLocal, "" + filterBogus); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        f.out("Filter .. DRM: %s, Adult: %s, Suspicious: %s, RegExp: %s", //$NON-NLS-1$
                "" + filterDRM, "" + filterAdult, "" + filterSuspicious, "" + bRegExp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        f.out("Filter .. Min Size: %d, Max Size: %d", filterMinSize, filterMaxSize); //$NON-NLS-1$
        f.out("Filter Sources: %d", filterSources); //$NON-NLS-1$
        f.out("Sort Column: %d", sortColumn); //$NON-NLS-1$
        f.out("Sort Dir: " + sortDir); //$NON-NLS-1$
        f.out("Files: %d", nFiles); //$NON-NLS-1$
        for (MatchFile file : files) {
            file.write(f);
        }
    }

}
