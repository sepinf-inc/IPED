/*
 * Copyright 2012-2016, Wladimir Luiz Caldas Leite
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

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.JTextComponent;

import iped.app.ui.columns.ColumnsManager;
import iped.localization.LocalizedProperties;

public class AutoCompleteColumns {
    public AutoCompleteColumns(JTextComponent editor) {
        synchronized (editor) {
            KeyListener[] listeners = editor.getKeyListeners();
            for (KeyListener kl : listeners) {
                if (kl instanceof AutoCompleteKeyListener) {
                    return;
                }
            }
            editor.setFocusTraversalKeysEnabled(false);
            editor.addKeyListener(new AutoCompleteKeyListener(editor));
        }
    }
}

class AutoCompleteKeyListener extends KeyAdapter {
    private final JTextComponent editor;
    private String last = null;
    private int pos = -1, lastIdx = -1;

    public AutoCompleteKeyListener(JTextComponent editor) {
        this.editor = editor;
    }

    public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == '\t') {
            if (editor.getCaretPosition() == editor.getText().length()) {
                if (last == null) {
                    last = editor.getText();
                    pos = last.lastIndexOf(' ') + 1;
                    while (pos > 1 && last.charAt(pos - 2) == '\\') {
                        pos = last.lastIndexOf(' ', pos - 2) + 1;
                    }
                    lastIdx = -1;
                }
                String base = removeEscape(last.substring(pos)).toLowerCase();
                if (base.length() > 0) {
                    String[][] cols = ColumnsManager.getInstance().fieldGroups;
                    if (cols != null) {
                        List<String> l = new ArrayList<String>();
                        for (int step = 0; step <= 1; step++) {
                            for (int i = 0; i < cols.length; i++) {
                                for (int j = 0; j < cols[i].length; j++) {
                                    String orgCol = cols[i][j];
                                    if (orgCol.equals(ResultTableModel.BOOKMARK_COL) || orgCol.equals(ResultTableModel.SCORE_COL))
                                        continue;
                                    NEXT: for (int m = 0; m <= 1; m++) {
                                        String col = orgCol;
                                        if (m == 0) {
                                            col = LocalizedProperties.getLocalizedField(col);
                                            if (col == null) {
                                                continue;
                                            }
                                        }
                                        if ((step == 0 && col.toLowerCase().startsWith(base)) || (step == 1 && col.toLowerCase().indexOf(base) > 0)) {
                                            for (int k = 0; k < l.size(); k++) {
                                                if (l.get(k).equals(col)) {
                                                    continue NEXT;
                                                }
                                            }
                                            l.add(col);
                                        }
                                    }
                                }
                            }
                        }
                        if (!l.isEmpty()) {
                            if (lastIdx == -1) {
                                lastIdx = 0;
                            } else {
                                lastIdx += (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0 ? -1 : 1;
                                if (lastIdx >= l.size()) {
                                    lastIdx = 0;
                                } else if (lastIdx < 0) {
                                    lastIdx = l.size() - 1;
                                }
                            }
                            editor.setText(editor.getText().substring(0, pos) + addEscape(l.get(lastIdx)) + ":"); //$NON-NLS-1$
                            editor.setCaretPosition(editor.getText().length());
                        }
                    }
                }
            } else {
                last = null;
            }
        } else {
            last = null;
        }
    }

    private String addEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String removeEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i < s.length() - 1) {
                c = s.charAt(++i);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
