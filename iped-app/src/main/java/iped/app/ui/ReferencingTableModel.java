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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.engine.task.HashTask;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
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
    protected void internalListItems(Document doc) {

        // clear table, searching for refs can take some time if they are thousands
        results = new LuceneSearchResult(0);
        fireTableDataChanged();

        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        // linkedItems queries
        String[] linkedItems = doc.getValues(ExtraProperties.LINKED_ITEMS);
        if (linkedItems.length > 0) {
            QueryBuilder b = new QueryBuilder(App.get().appCase);
            for (String q : linkedItems) {
                try {
                    queryBuilder.add(b.getQuery(q), Occur.SHOULD);
                } catch (ParseException | QueryNodeException e) {
                    e.printStackTrace();
                }
            }
        }

        // sharedHashes
        String[] sharedHashes = doc.getValues(ExtraProperties.SHARED_HASHES);
        if (sharedHashes.length > 0) {
            String field;
            String mediaType = doc.get(BasicProps.CONTENTTYPE);
            if (KnownMetParser.EMULE_MIME_TYPE.equals(mediaType)) {
                field = HashTask.HASH.EDONKEY.toString();
            } else if (AresParser.ARES_MIME_TYPE.equals(mediaType)) {
                field = HashTask.HASH.SHA1.toString();
            } else if (ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE.equals(mediaType)) {
                field = HashTask.HASH.MD5.toString();
            } else {
                field = BasicProps.HASH;
            }

            Set<BytesRef> hashes = Arrays.asList(sharedHashes).stream().filter(StringUtils::isNotBlank)
                    .map(h -> new BytesRef(h)).collect(Collectors.toSet());
            queryBuilder.add(new TermInSetQuery(field, hashes), Occur.SHOULD);
        }

        // ufed:jumptargets
        String[] ufedJumpTargets = doc.getValues(ExtraProperties.UFED_JUMP_TARGETS);
        if (ufedJumpTargets.length > 0) {
            Set<BytesRef> targets = Arrays.asList(ufedJumpTargets).stream().filter(StringUtils::isNotBlank)
                    .map(h -> new BytesRef(h)).collect(Collectors.toSet());
            queryBuilder.add(new TermInSetQuery(ExtraProperties.UFED_ID, targets), Occur.SHOULD);
        }

        // ufed:file_id (needed? alrealdy contained in linkedItems)
        String ufedFileId = doc.get(ExtraProperties.UFED_FILE_ID);
        if (ufedFileId != null) {
            queryBuilder.add(new TermQuery(new Term(ExtraProperties.UFED_ID, ufedFileId)), Occur.SHOULD);
        }

        BooleanQuery query = queryBuilder.build();

        if (!query.clauses().isEmpty()) {
            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, query, BasicProps.NAME);
                task.setRewritequery(false);
                results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);

                final int length = results.getLength();

                if (length > 0) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            App.get().referencesDock
                                    .setTitleText(Messages.getString("ReferencesTab.Title") + " " + length);
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
}
