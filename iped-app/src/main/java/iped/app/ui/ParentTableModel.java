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

import javax.swing.ListSelectionModel;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import iped.data.IItem;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;


public class ParentTableModel extends BaseTableModel {

    private static final long serialVersionUID = 1L;

    public ParentTableModel() {
    }

    @Override
    public void valueChanged(ListSelectionModel lsm) {

        if (refDoc != null) {
            IItem item = IndexItem.getItem(refDoc, App.get().appCase, false);
            if (item != null) {
                String parentViewPosition = item.getMetadata().get(ExtraProperties.PARENT_VIEW_POSITION);
                if (parentViewPosition != null) {
                    App.get().getViewerController().getHtmlLinkViewer().setElementIDToScroll(parentViewPosition);
                }
            }
        }

        FileProcessor parsingTask = new FileProcessor(results.getLuceneIds()[selectedIndex], false);
        parsingTask.execute();

        App.get().subItemModel.fireTableDataChanged();
    }

    @Override
    public Query createQuery(Document doc) {

        String parentId = doc.get(BasicProps.PARENTID);
        if (parentId == null) {
            return null;
        }

        String sourceUUID = doc.get(BasicProps.EVIDENCE_UUID);

        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(IntPoint.newExactQuery(BasicProps.ID, Integer.parseInt(parentId)), Occur.MUST);
        queryBuilder.add(new TermQuery(new Term(BasicProps.EVIDENCE_UUID, sourceUUID)), Occur.MUST);

        return queryBuilder.build();
    }

    @Override
    public void onListItemsResultsComplete() {
        App.get().parentDock.setTitleText(Messages.getString("ParentTableModel.ParentCount"));
    }
}
