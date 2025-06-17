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
import java.util.stream.Collectors;

import javax.swing.ListSelectionModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import iped.data.IItem;
import iped.engine.search.QueryBuilder;
import iped.engine.task.HashTask;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.parsers.ares.AresParser;
import iped.parsers.emule.KnownMetParser;
import iped.parsers.shareaza.ShareazaLibraryDatParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class ReferencedByTableModel extends BaseTableModel {

    private static final long serialVersionUID = 1L;

    public ReferencedByTableModel() {
        cleanBeforeListItems = true;
    }

    @Override
    public void valueChanged(ListSelectionModel lsm) {
        int id = results.getLuceneIds()[selectedIndex];
        IItem item = App.get().appCase.getItemByLuceneID(id);

        String nameToScroll = null;
        if (refDoc != null) {
            if (KnownMetParser.EMULE_MIME_TYPE.equals(item.getMediaType().toString())) {
                nameToScroll = refDoc.get(HashTask.HASH.EDONKEY.toString());
            } else if (AresParser.ARES_MIME_TYPE.equals(item.getMediaType().toString())) {
                nameToScroll = refDoc.get(HashTask.HASH.SHA1.toString());
            } else if (ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE.equals(item.getMediaType().toString())) {
                nameToScroll = refDoc.get(HashTask.HASH.MD5.toString());
            } else {
                nameToScroll = refDoc.get(BasicProps.HASH);
            }
        }

        if (nameToScroll != null) {
            App.get().getViewerController().getHtmlLinkViewer().setElementNameToScroll(nameToScroll);
        }

        FileProcessor parsingTask = new FileProcessor(id, false);
        parsingTask.execute();
    }

    @Override
    public Query createQuery(Document doc) {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        QueryBuilder b = new QueryBuilder(App.get().appCase);

        // hashes
        String md5 = doc.get(HashTask.HASH.MD5.toString());
        String sha1 = doc.get(HashTask.HASH.SHA1.toString());
        String sha256 = doc.get(HashTask.HASH.SHA256.toString());
        String edonkey = doc.get(HashTask.HASH.EDONKEY.toString());
        String hashes = Arrays.asList(md5, sha1, sha256, edonkey).stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));
        if (!hashes.isEmpty()) {
            try {
                queryBuilder.add(b.getQuery(ExtraProperties.LINKED_ITEMS + ":(" + hashes + ") "), Occur.SHOULD);
                queryBuilder.add(b.getQuery(ExtraProperties.SHARED_HASHES + ":(" + hashes + ")"), Occur.SHOULD);
            } catch (ParseException | QueryNodeException e) {
                e.printStackTrace();
            }
        }

        // id
        String idQuery = QueryBuilder.escape(BasicProps.ID + ":" + doc.get(BasicProps.ID));
        try {
            queryBuilder.add(b.getQuery(ExtraProperties.LINKED_ITEMS + ":\"" + idQuery + "\""), Occur.SHOULD);
        } catch (ParseException | QueryNodeException e) {
            e.printStackTrace();
        }

        return queryBuilder.build();
    }

    @Override
    public void onListItemsResultsComplete() {
        App.get().referencedByDock.setTitleText(Messages.getString("ReferencedByTab.Title") + " " + results.getLength());
    }
}
