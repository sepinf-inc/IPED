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

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class ShareazaOutputGenerator {

    private final ByteArrayOutputStream bout;
    private final PrintWriter writer;
    private int ident = 0;
    private final Map<Integer, String> identMap = new HashMap<>();

    public ShareazaOutputGenerator() throws UnsupportedEncodingException {
        bout = new ByteArrayOutputStream();
        writer = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$
    }

    public void incIdent() {
        ident++;
    }

    public void decIdent() {
        ident--;
    }

    public void out(String text) {
        writer.println(getIdentString() + text);
    }

    public void out(String fmt, Object... text) {
        out(String.format(fmt, text));
    }

    public byte[] getBytes() {
        writer.flush();
        return bout.toByteArray();
    }

    private String getIdentString() {
        String ret = identMap.get(ident);

        if (ret == null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < ident; i++) {
                builder.append("  "); //$NON-NLS-1$
            }
            ret = builder.toString();
            identMap.put(ident, ret);
        }

        return ret;
    }

}
