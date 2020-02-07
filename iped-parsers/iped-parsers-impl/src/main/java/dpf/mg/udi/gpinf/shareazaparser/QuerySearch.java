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
class QuerySearch extends ShareazaEntity {

    private String guid;
    private String sSearch;
    private String sha1;
    private String tiger;
    private String ed2k;
    private String bth;
    private String md5;
    private String uri;
    private final XMLElement xml = new XMLElement();
    private boolean wantURL = false;
    private boolean wantDN = false;
    private boolean wantXML = false;
    private boolean wantCOM = false;
    private boolean wantPFS = false;
    private long minSize = 0;
    private long maxSize = 0;

    public QuerySearch() {
        super("QUERY SEARCH"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar) throws IOException {
        int version = ar.readInt();
        if (version < 4) {
            throw new IOException("Unsupported Version"); //$NON-NLS-1$
        }
        guid = Util.encodeGUID(ar.readBytes(16));
        sSearch = ar.readString();
        sha1 = ar.readHash(20);
        tiger = ar.readHash(24);
        ed2k = ar.readHash(16);
        bth = ar.readHash(20, "base32"); //$NON-NLS-1$
        if (version >= 7) {
            md5 = ar.readHash(16);
        }
        uri = ar.readString();
        if (uri.length() > 0) {
            xml.read(ar);
        }

        if (version >= 5) {
            wantURL = ar.readBool();
            wantDN = ar.readBool();
            wantXML = ar.readBool();
            wantCOM = ar.readBool();
            wantPFS = ar.readBool();
        }

        if (version >= 8) {
            minSize = ar.readLong();
            maxSize = ar.readLong();
        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("GUID: " + guid); //$NON-NLS-1$
        f.out("Search: " + sSearch); //$NON-NLS-1$
        f.out("SHA1: " + sha1); //$NON-NLS-1$
        f.out("Tiger: " + tiger); //$NON-NLS-1$
        f.out("ED2K: " + ed2k); //$NON-NLS-1$
        f.out("BTH: " + bth); //$NON-NLS-1$
        f.out("MD5: " + md5); //$NON-NLS-1$
        f.out("URI: " + uri); //$NON-NLS-1$
        xml.write(f);
        f.out("Want URL: " + wantURL); //$NON-NLS-1$
        f.out("Want DN: " + wantDN); //$NON-NLS-1$
        f.out("Want XML: " + wantXML); //$NON-NLS-1$
        f.out("Want COM: " + wantCOM); //$NON-NLS-1$
        f.out("Want PFS: " + wantPFS); //$NON-NLS-1$
        f.out("Min Size: " + minSize); //$NON-NLS-1$
        f.out("Max Size: " + maxSize); //$NON-NLS-1$
    }

}
