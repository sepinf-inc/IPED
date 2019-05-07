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
class MatchFile extends ShareazaEntity {

    private final List<QueryHit> hits = new ArrayList<>();
    private long size;
    private String sSize;
    private String sha1;
    private String tiger;
    private String ed2k;
    private String bth;
    private String md5;
    private boolean busy;
    private boolean push;
    private boolean stable;
    private long speed;
    private String sSpeed;
    private boolean expanded;
    private boolean existing;
    private boolean download;
    private boolean onevalid;
    private int nPreview;
    private String preview;
    private long total;
    /* private byte[] time; */

    public MatchFile() {
        super("MATCH FILE"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        if (version >= 10) {
            size = ar.readLong();
        } else {
            size = ar.readUInt();
        }

        sSize = ar.readString();
        sha1 = ar.readHash(20);
        tiger = ar.readHash(24);
        ed2k = ar.readHash(16);
        if (version >= 13) {
            bth = ar.readHash(20, "base32"); //$NON-NLS-1$
            md5 = ar.readHash(16);
        }

        busy = ar.readBool();
        push = ar.readBool();
        stable = ar.readBool();
        speed = ar.readUInt();
        sSpeed = ar.readString();
        expanded = ar.readBool();
        existing = ar.readBool();
        download = ar.readBool();
        onevalid = ar.readBool();
        nPreview = ar.readCount();
        if (nPreview > 0) {
            byte[] bytes = ar.readBytes(nPreview);
            preview = Util.encodeBase64(bytes);
        }
        total = ar.readCount();
        for (int i = 0; i < total; i++) {
            QueryHit hit = new QueryHit();
            hit.read(ar, version);
            hits.add(hit);
        }
        if (version >= 14) {
            /* time = */
            ar.readBytes(12);
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("Size: %d (%s)", size, sSize); //$NON-NLS-1$
        f.out("SHA1: " + sha1); //$NON-NLS-1$
        f.out("Tiger: " + tiger); //$NON-NLS-1$
        f.out("ED2K: " + ed2k); //$NON-NLS-1$
        f.out("BTH: " + bth); //$NON-NLS-1$
        f.out("MD5: " + md5); //$NON-NLS-1$
        f.out("Busy: " + busy); //$NON-NLS-1$
        f.out("Push: " + push); //$NON-NLS-1$
        f.out("Stable: " + stable); //$NON-NLS-1$
        f.out("Speed: %d (%s)", speed, sSpeed); //$NON-NLS-1$
        f.out("Expanded: " + expanded); //$NON-NLS-1$
        f.out("Existing: " + existing); //$NON-NLS-1$
        f.out("Download: " + download); //$NON-NLS-1$
        f.out("One Valid: " + onevalid); //$NON-NLS-1$
        f.out("Preview Size: " + nPreview); //$NON-NLS-1$
        f.out("Preview: " + preview); //$NON-NLS-1$
        f.out("Total Hits: " + total); //$NON-NLS-1$
        for (QueryHit h : hits) {
            h.write(f);
        }
        f.out("Time: ##TODO: decode CTime struct"); //$NON-NLS-1$
    }

}
