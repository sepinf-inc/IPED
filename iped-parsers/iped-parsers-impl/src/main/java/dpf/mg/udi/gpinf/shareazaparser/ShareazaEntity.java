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
abstract class ShareazaEntity {
	
	private final String entityName;
	
	protected ShareazaEntity(String entityName) {
		this.entityName = entityName;
	}

	public void read(MFCParser ar) throws IOException {
	}

	public void read(MFCParser ar, int version) throws IOException {
	}

	public void write(ShareazaOutputGenerator f) {
		f.out(entityName);
		f.incIdent();
		writeImpl(f);
		f.decIdent();
	}
	
	protected abstract void writeImpl(ShareazaOutputGenerator f); 
}
