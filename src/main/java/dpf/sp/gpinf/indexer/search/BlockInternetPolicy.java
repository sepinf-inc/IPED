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
package dpf.sp.gpinf.indexer.search;

import java.net.SocketPermission;
import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;

/**
 * 
 * @author PCF Nassif
 */
public class BlockInternetPolicy extends Policy {

	Permission socketPerm = new SocketPermission("localhost:8100", "connect, resolve");
	Permission socketPerm2 = new SocketPermission("[0:0:0:0:0:0:0:1]:8100", "connect,resolve");

	public BlockInternetPolicy() {
		super();
	}

	@Override
	public boolean implies(ProtectionDomain domain, Permission perm) {

		if (perm instanceof SocketPermission) {
			if (socketPerm.implies(perm))
				return true;
			else if (socketPerm2.implies(perm))
				return true;
			else
				return false;
		}

		return true;
	}

}
