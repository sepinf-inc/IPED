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

import iped.data.IItem;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.properties.ExtraProperties;

public class ParentTableModel extends BaseTableModel {

    private static final long serialVersionUID = 1L;

    @Override
    public void valueChanged(ListSelectionModel lsm) {

        boolean scrolled = false;
        if (refDoc != null) {
            IItem item = IndexItem.getItem(refDoc, App.get().appCase, false);
            if (item != null) {
                String parentViewPosition = item.getMetadata().get(ExtraProperties.PARENT_VIEW_POSITION);
                if (parentViewPosition != null) {
                    App.get().getViewerController().getHtmlLinkViewer().setElementIDToScroll(parentViewPosition);
                    scrolled = true;
                }
            }
        }

        if (!scrolled) {
            App.get().getTextViewer().textTable.scrollRectToVisible(new Rectangle());
        }

        FileProcessor parsingTask = new FileProcessor(results.getLuceneIds()[selectedIndex], false);
        parsingTask.execute();

        App.get().subItemModel.fireTableDataChanged();
    }

    @Override
    protected void internalListItems(Document doc) {

        String textQuery = null;
        String parentId = doc.get(IndexItem.PARENTID);
        if (parentId != null) {
            textQuery = IndexItem.ID + ":" + parentId;
        }

        String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
        textQuery += " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID;

        results = new LuceneSearchResult(0);

        if (textQuery != null) {
            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
                results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (results.getLength() > 0) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    App.get().parentDock.setTitleText(Messages.getString("ParentTableModel.ParentCount"));
                }
            });
        }

        fireTableDataChanged();
    }
}
