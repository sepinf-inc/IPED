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
import javax.swing.SwingUtilities;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.HashTask;
import iped.parsers.ares.AresParser;
import iped.parsers.emule.KnownMetParser;
import iped.parsers.shareaza.ShareazaLibraryDatParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class ReferencingTableModel extends BaseTableModel {

    private static final long serialVersionUID = 1L;

    @Override
    public void valueChanged(ListSelectionModel lsm) {
        int id = results.getLuceneIds()[selectedIndex];

        FileProcessor parsingTask = new FileProcessor(id, false);
        parsingTask.execute();
    }

    @Override
    public void listItems(Document doc) {

        // clear table, searching for refs can take some time if they are thousands
        results = new LuceneSearchResult(0);
        fireTableDataChanged();

        StringBuilder textQuery = null;
        Query query = null;

        String[] linkedItems = doc.getValues(ExtraProperties.LINKED_ITEMS);
        if (linkedItems != null && linkedItems.length > 0) {
            textQuery = new StringBuilder();
            for (String q : linkedItems) {
                textQuery.append("(").append(q).append(") ");
            }
        } else {
            linkedItems = doc.getValues(ExtraProperties.SHARED_HASHES);
            if (linkedItems != null && linkedItems.length > 0) {
                String term;
                String mediaType = doc.get(BasicProps.CONTENTTYPE);
                if (KnownMetParser.EMULE_MIME_TYPE.equals(mediaType)) {
                    term = HashTask.HASH.EDONKEY.toString();
                } else if (AresParser.ARES_MIME_TYPE.equals(mediaType)) {
                    term = HashTask.HASH.SHA1.toString();
                } else if (ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE.equals(mediaType)) {
                    term = HashTask.HASH.MD5.toString();
                } else {
                    term = BasicProps.HASH;
                }
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (String hash : linkedItems) {
                    builder.add(new TermQuery(new Term(term, hash)), Occur.SHOULD);
                }
                query = builder.build();
            }
        }

        if (textQuery != null || query != null) {
            try {
                IPEDSearcher task = query != null ? new IPEDSearcher(App.get().appCase, query, BasicProps.NAME) : new IPEDSearcher(App.get().appCase, textQuery.toString(), BasicProps.NAME);
                task.setRewritequery(false);
                results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);

                final int length = results.getLength();

                if (length > 0) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            App.get().referencesDock.setTitleText(Messages.getString("ReferencesTab.Title") + " " + length);
                        }
                    });
                }

            } catch (Exception e) {
                results = new LuceneSearchResult(0);
                e.printStackTrace();
            }
        }

        fireTableDataChanged();
    }
}
