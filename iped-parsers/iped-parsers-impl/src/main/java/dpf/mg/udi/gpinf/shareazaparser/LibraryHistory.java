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
class LibraryHistory extends ShareazaEntity {

    private final List<LibraryRecent> recentList = new ArrayList<>();
    private String lastSeededTorrentPath = ""; //$NON-NLS-1$
    private String lastSeededTorrentName = ""; //$NON-NLS-1$
    private String lastSeededTorrentTime = ""; //$NON-NLS-1$
    private String lastSeededTorrentBTH = ""; //$NON-NLS-1$

    public LibraryHistory() {
        super("LIBRARY HISTORY"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        int n = ar.readCount();
        for (int i = 0; i < n; i++) {
            LibraryRecent recent = new LibraryRecent();
            recent.read(ar, version);
            recentList.add(recent);
        }
        if (version > 22) {
            lastSeededTorrentPath = ar.readString();
            if (lastSeededTorrentPath.length() > 0) {
                lastSeededTorrentName = ar.readString();
                lastSeededTorrentTime = Util.formatDatetime(Util.convertToEpoch(ar.readUInt()));
                lastSeededTorrentBTH = ar.readHash(20, "base32"); //$NON-NLS-1$
            }
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Last Seeded Torrent Path: " + lastSeededTorrentPath); //$NON-NLS-1$
        f.out("Last Seeded Torrent Name: " + lastSeededTorrentName); //$NON-NLS-1$
        f.out("Last Seeded Torrent Time: " + lastSeededTorrentTime); //$NON-NLS-1$
        f.out("Last Seeded Torrent BTH: " + lastSeededTorrentBTH); //$NON-NLS-1$
        for (LibraryRecent recent : recentList) {
            recent.write(f);
        }
    }
}
