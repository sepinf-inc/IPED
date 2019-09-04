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

import java.net.SocketPermission;
import java.security.Permission;

public class AppSecurityManager extends SecurityManager {

    SecurityManager manager;
    Permission socketPerm = new SocketPermission("localhost:8100", "connect, resolve"); //$NON-NLS-1$ //$NON-NLS-2$
    Permission socketPerm2 = new SocketPermission("[0:0:0:0:0:0:0:1]:8100", "connect,resolve"); //$NON-NLS-1$ //$NON-NLS-2$
    String javaFXString = "accessClassInPackage.com.sun.javafx"; //$NON-NLS-1$

    public AppSecurityManager() {
        super();
        manager = System.getSecurityManager();
    }

    @Override
    public void checkPermission(Permission perm) {
        checkPermission(perm, null);

    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        if (perm instanceof SocketPermission) {
            if (socketPerm.implies(perm)) {
                return;
            } else if (socketPerm2.implies(perm)) {
                return;
            } else {
                throw new SecurityException("Internet Access blocked!"); //$NON-NLS-1$
            }
        } else {
            return;
        }

        /*
         * if(context == null) manager.checkPermission(perm); else
         * manager.checkPermission(perm, context);
         */
    }

}
