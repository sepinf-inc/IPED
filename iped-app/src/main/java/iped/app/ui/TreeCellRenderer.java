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

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.apache.lucene.document.Document;

import iped.app.ui.TreeViewModel.Node;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class TreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    private static final Icon rootIcon = IconManager.getTreeIcon("evidences-root");

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        Node node = (Node) value;
        boolean isDir = Boolean.valueOf(node.getDoc().get(IndexItem.ISDIR)) || node.docId == -1;
        super.getTreeCellRendererComponent(tree, value, selected, expanded, !isDir, row, hasFocus);

        boolean isDecodedReport = Boolean.valueOf(node.getDoc().get(ExtraProperties.DECODED_DATA));
        isDecodedReport = isDecodedReport && Boolean.valueOf(node.getDoc().get(BasicProps.HASCHILD));

        if (row == 0) {
            setIcon(rootIcon);
        } else if (isDir) {
            setIcon(IconManager.getFolderIcon(expanded));
        } else if (isDecodedReport) {
            setIcon(IconManager.getReportFolderIcon(expanded));
        } else {
            Document doc = node.getDoc();
            String type = doc.get(BasicProps.TYPE);
            String contentType = doc.get(BasicProps.CONTENTTYPE);
            Icon icon = Boolean.valueOf(doc.get(IndexItem.ISROOT)) ? IconManager.getFileIcon(contentType, type, IconManager.getDiskIcon()) : IconManager.getFileIcon(contentType, type);
            setIcon(icon);
        }

        return this;

    }

}
