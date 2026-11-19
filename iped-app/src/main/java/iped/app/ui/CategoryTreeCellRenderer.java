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

import iped.engine.localization.CategoryLocalization;

public class CategoryTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        String category = value != null ? value.toString() : "";
        category = category.replaceAll("\\((\\d*,*)*(\\d*.*)*\\)", "").trim();
        category = getNonLocalizedCategory(category);
        category = category != null ? category : "";

        if (row == 0 && category.equals("root")) {
            category = Messages.getString("CategoryTreeModel.RootName");
            setText(category);
            category = getNonLocalizedCategory(category);
        }

        Icon icon = IconManager.getCategoryIcon(category.toLowerCase());
        setIcon(icon);

        return this;

    }

    private String getNonLocalizedCategory(String category) {
        return CategoryLocalization.getInstance().getNonLocalizedCategory(category);
    }
}
