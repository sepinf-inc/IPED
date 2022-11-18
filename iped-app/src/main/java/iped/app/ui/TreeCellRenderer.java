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
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.apache.lucene.document.Document;

import iped.app.ui.TreeViewModel.Node;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;

public class TreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    private static Icon diskIcon = UIManager.getIcon("FileView.hardDriveIcon"); //$NON-NLS-1$

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

        Node node = (Node) value;
        boolean notDir = !Boolean.valueOf(node.getDoc().get(IndexItem.ISDIR)) && node.docId != -1;
        DefaultTreeCellRenderer result = (DefaultTreeCellRenderer) super.getTreeCellRendererComponent(tree, value,
                selected, expanded, notDir, row, hasFocus);

        if (notDir && Boolean.valueOf(node.getDoc().get(IndexItem.ISROOT))) {
            result.setIcon(diskIcon);
        } else if (notDir) {
            Document doc = node.getDoc();
            String type = doc.get(BasicProps.TYPE);
            String contentType = doc.get(BasicProps.CONTENTTYPE);
            Icon icon = IconManager.getFileIcon(contentType, type);
            result.setIcon(icon);
        }

        return result;

    }

}
