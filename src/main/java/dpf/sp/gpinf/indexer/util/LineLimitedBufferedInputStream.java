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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/*
 * TODO: Implementação incompleta
 */
public class LineLimitedBufferedInputStream extends BufferedInputStream {

	public LineLimitedBufferedInputStream(InputStream in) {
		super(in);
	}

	public int getPosition() {
		return this.count;

	}

	public int read(byte b[], int off, int len) throws IOException {

		if (len > 0 && this.count == this.pos) {
			return super.read(b, off, 1);
		}

		int newLen = 0;
		for (int i = this.pos; i < this.count && i - this.pos < len; i++) {
			newLen++;
			if (this.buf[i] == '\n')
				break;
		}

		return super.read(b, off, newLen);
	}

}
