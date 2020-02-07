/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
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
package dpf.sp.gpinf.indexer.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {

    private long limit, total = 0;
    private long totalMarked;

    public LimitedInputStream(InputStream in, long limit) {
        super(in);
        this.limit = limit;
    }

    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {

        if (total == limit)
            return -1;

        if (total + len > limit)
            len = (int) (limit - total);

        int result = super.read(b, off, len);

        if (result > 0)
            total += result;

        return result;
    }

    @Override
    public int read() throws IOException {

        if (total == limit)
            return -1;

        int result = super.read();

        if (result > -1)
            total++;

        return result;

    }

    @Override
    public int available() throws IOException {
        int a = super.available();
        long diff = limit - total;

        if (a > diff)
            return (int) diff;
        else
            return a;

    }

    @Override
    public long skip(long n) throws IOException {

        if (n > limit - total)
            n = limit - total;

        long skiped = super.skip(n);

        total += skiped;

        return skiped;

    }

    @Override
    public void mark(int mark) {
        super.mark(mark);
        totalMarked = total;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        total = totalMarked;
    }

}
