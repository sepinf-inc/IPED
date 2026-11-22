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

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

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

    public ReferencingTableModel() {
        cleanBeforeListItems = true;
    }

    @Override
    public void valueChanged(ListSelectionModel lsm) {
        int id = results.getLuceneIds()[selectedIndex];

        FileProcessor parsingTask = new FileProcessor(id, false);
        parsingTask.execute();
    }

    @Override
    public Query createQuery(Document doc) {
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

        // ufed:jumpTargets
        String[] jumpTargets = doc.getValues(ExtraProperties.UFED_JUMP_TARGETS);
        for (String jumpTarget : jumpTargets) {
            queryBuilder.add(new TermQuery(new Term(ExtraProperties.UFED_ID, jumpTarget)), Occur.SHOULD);
        }

        // ufed:file_id
        String fileId = doc.get(ExtraProperties.UFED_FILE_ID);
        if (StringUtils.isNotBlank(fileId)) {
            queryBuilder.add(new TermQuery(new Term(ExtraProperties.UFED_ID, fileId)), Occur.SHOULD);
        }

        // don't reference itself
        queryBuilder.add(IntPoint.newExactQuery(BasicProps.ID, Integer.parseInt(doc.get(BasicProps.ID))), Occur.MUST_NOT);

        return queryBuilder.build();
    }

    @Override
    public void onListItemsResultsComplete() {
        App.get().referencesDock.setTitleText(Messages.getString("ReferencesTab.Title") + " " + results.getLength());
    }
}
