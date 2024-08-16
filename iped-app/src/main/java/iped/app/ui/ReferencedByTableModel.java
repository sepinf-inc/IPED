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
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;

import iped.data.IItem;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.HashTask;
import iped.parsers.ares.AresParser;
import iped.parsers.emule.KnownMetParser;
import iped.parsers.shareaza.ShareazaLibraryDatParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class ReferencedByTableModel extends BaseTableModel {

    private static final long serialVersionUID = 1L;

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
    public void listItems(Document doc) {

        String md5 = doc.get(HashTask.HASH.MD5.toString());
        String sha1 = doc.get(HashTask.HASH.SHA1.toString());
        String sha256 = doc.get(HashTask.HASH.SHA256.toString());
        String edonkey = doc.get(HashTask.HASH.EDONKEY.toString());
        String ufedId = doc.get(ExtraProperties.UFED_META_PREFIX + "id");
        if (StringUtils.isNotBlank(ufedId)) {
            ufedId = "\"" + ufedId + "\"";
        }

        String hashes = Arrays.asList(md5, sha1, sha256, edonkey).stream().filter(Objects::nonNull).collect(Collectors.joining(" "));

        if (hashes.isEmpty()) {
            results = new LuceneSearchResult(0);
            refDoc = null;
        } else {
            String textQuery = ExtraProperties.LINKED_ITEMS + ":(" + hashes + ") ";
            textQuery += ExtraProperties.SHARED_HASHES + ":(" + hashes + ")";

            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery, BasicProps.NAME);
                results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);

                final int length = results.getLength();

                if (length > 0) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            App.get().referencedByDock.setTitleText(Messages.getString("ReferencedByTab.Title") + " " + length);
                        }
                    });
                    refDoc = doc;
                } else {
                    refDoc = null;
                }

            } catch (Exception e) {
                results = new LuceneSearchResult(0);
                refDoc = null;
                e.printStackTrace();
            }
        }

        fireTableDataChanged();
    }
}
