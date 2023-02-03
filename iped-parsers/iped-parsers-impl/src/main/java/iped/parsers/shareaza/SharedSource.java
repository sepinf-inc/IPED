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
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class SharedSource extends ShareazaEntity {

    private String url;
    private String time;
    private URI surl;

    public SharedSource() {
        super("SHARED SOURCE"); //$NON-NLS-1$
    }

    @Override
    public void read(MFCParser ar, int version) throws IOException {
        url = ar.readString();
        if (version >= 10) {
            time = Util.formatDatetime(Util.convertToEpoch(ar.readLong()));
        } else {
            time = Util.formatDatetime(Util.convertToEpoch(ar.readUInt()));
        }
        try {
            surl = new URI(url);
        } catch (URISyntaxException e) {

        }
    }

    @Override
    protected void writeImpl(ShareazaOutputGenerator f) {
        f.out("URL: " + url); //$NON-NLS-1$
        f.out("Time: " + time); //$NON-NLS-1$
    }

    public String getUrl() {
        return url;
    }

    public String getTime() {
        return time;
    }

    public String getHost() {
        if (surl != null) {
            return surl.getHost();
        } else {
            return null;
        }
    }

    public String getUserInfo() {
        if (surl != null) {
            return surl.getUserInfo();
        } else {
            return null;
        }
    }

    public String getProtocol() {
        if (surl != null) {
            return surl.getScheme();

        } else {
            return null;
        }
    }

    public String getPort() {
        if (surl != null) {
            return Integer.toString(surl.getPort());
        } else {
            return null;
        }
    }

    public String getUrlPath() {
        if (surl != null) {
            return surl.getPath();
        } else {
            return null;
        }
    }

    public String getName() {
        if (surl != null) {
            if (getProtocol().contains("ed2kftp")) {
                return surl.getPath();
            } else {
                return surl.getQuery();
            }
        } else {
            return url;
        }
    }
}
