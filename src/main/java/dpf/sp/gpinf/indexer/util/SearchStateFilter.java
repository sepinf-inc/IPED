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

import java.io.File;
import java.io.Serializable;

import javax.swing.filechooser.FileFilter;

import dpf.sp.gpinf.indexer.search.Marcadores;

public class SearchStateFilter extends FileFilter implements Serializable {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  @Override
  public boolean accept(File f) {
    if (f.isDirectory()) {
      return true;
    }
    if (f.getName().endsWith(Marcadores.EXT)) {
      return true;
    }

    return false;
  }

  @Override
  public String getDescription() {
    return "Marcadores da Pesquisa";
  }

}
