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

import java.nio.ByteBuffer;

import javax.swing.table.AbstractTableModel;

public class HitsTableModel extends AbstractTableModel {

  private static final long serialVersionUID = 1L;

  private String[] columnNames = {"", "Ocorrências no conteúdo"};

  @Override
  public int getColumnCount() {
    return columnNames.length;
  }

  @Override
  public int getRowCount() {

    if (App.get().getTextViewer() != null && App.get().getTextViewer().textParser != null) {
      return App.get().getTextViewer().textParser.sortedHits.size();
    }

    return 0;
  }

  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  @Override
  public Object getValueAt(int row, int col) {
    try {
      if (col == 0) {
        return row + 1;
      } else {
        long hitOff = App.get().getTextViewer().textParser.hits.get(row);
        int hitLen = App.get().getTextViewer().textParser.sortedHits.get(hitOff)[0];

        ByteBuffer data = ByteBuffer.allocate(hitLen);
        int nread;
        do {
          nread = TextParser.parsedFile.read(data, hitOff);
          hitOff += nread;
        } while (nread != -1 && data.hasRemaining());

        data.flip();
        return "<html><body>" + (new String(data.array(), "windows-1252")) + "</body></html>";
      }
    } catch (Exception e) {
      // e.printStackTrace();
      return "";
    }

  }
}
