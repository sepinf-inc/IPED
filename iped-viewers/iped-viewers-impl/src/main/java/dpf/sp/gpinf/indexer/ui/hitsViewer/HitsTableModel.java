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
package dpf.sp.gpinf.indexer.ui.hitsViewer;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ATextViewer;
import java.nio.ByteBuffer;

import javax.swing.table.AbstractTableModel;

public class HitsTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private final ATextViewer textViewer;

    private String[] columnNames = { "", Messages.getString("HitsTableModel.ContentHits") }; //$NON-NLS-1$ //$NON-NLS-2$

    public HitsTableModel(ATextViewer textViewer) {
        this.textViewer = textViewer;
        textViewer.setHitsModel(this);
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public int getRowCount() {

        if (textViewer != null && textViewer.textParser != null) {
            return textViewer.textParser.getSortedHits().size();
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
            } 
            long hitOff = textViewer.textParser.getHits().get(row);
            int hitLen = textViewer.textParser.getSortedHits().get(hitOff)[0];

            ByteBuffer data = ByteBuffer.allocate(hitLen);
            int nread;
            do {
                nread = textViewer.textParser.getParsedFile().read(data, hitOff);
                hitOff += nread;
            } while (nread != -1 && data.hasRemaining());

            data.flip();
            return "<html><body>" + (new String(data.array(), ATextViewer.TEXT_ENCODING)) + "</body></html>"; //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            // e.printStackTrace();
            return ""; //$NON-NLS-1$
        }

    }
}
