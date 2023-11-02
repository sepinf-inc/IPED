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
package iped.app.ui;

import java.awt.Rectangle;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.apache.lucene.document.Document;

import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.search.IIPEDSearcher;

public class DuplicatesTableModel extends BaseTableModel {

    private static final long serialVersionUID = 1L;

    @Override
    public Object getValueAt(int row, int col) {
        if (col == 3) {
            try {
                Document doc = App.get().appCase.getSearcher().doc(results.getLuceneIds()[row]);
                return doc.get(IndexItem.PATH);
            } catch (Exception e) {
            }
            return "";
        }
        return super.getValueAt(row, col);
    }

    @Override
    public void valueChanged(ListSelectionModel lsm) {
        App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());

        FileProcessor parsingTask = new FileProcessor(results.getLuceneIds()[selectedIndex], false);
        parsingTask.execute();

        App.get().parentItemModel.fireTableDataChanged();
    }

    @Override
    public void listItems(Document doc) {

        String hash = doc.get(IndexItem.HASH);
        if (hash == null || hash.trim().isEmpty())
            return;

        String textQuery = IndexItem.HASH + ":" + hash;

        String id = doc.get(IndexItem.ID);
        String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);

        textQuery += " && NOT (" + IndexItem.ID + ":" + id;
        textQuery += " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID + ")";

        try {
            IIPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery, BasicProps.PATH);
            results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);

            final int duplicates = results.getLength();

            if (duplicates > 0) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        App.get().duplicateDock
                                .setTitleText(duplicates + Messages.getString("DuplicatesTableModel.Duplicates"));
                    }
                });
            }

        } catch (Exception e) {
            results = new LuceneSearchResult(0);
            e.printStackTrace();
        }

        fireTableDataChanged();
    }
}
