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
class SharedSource extends ShareazaEntity {
	
	private String url;
	private String time;
	
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
	}

	@Override
	protected void writeImpl(ShareazaOutputGenerator f) {
		f.out("URL: " + url); //$NON-NLS-1$
		f.out("Time: " + time); //$NON-NLS-1$
	}
	
}
