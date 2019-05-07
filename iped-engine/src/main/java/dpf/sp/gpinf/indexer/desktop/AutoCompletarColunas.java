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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.JTextComponent;

public class AutoCompletarColunas {
    public AutoCompletarColunas(final JTextComponent editor) {
        editor.setFocusTraversalKeysEnabled(false);
        editor.addKeyListener(new KeyAdapter() {
            private String last = null;
            private int pos = -1, lastIdx = -1;

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
                            String[][] cols = ColumnsManagerImpl.getInstance().fieldGroups;
                            if (cols != null) {
                                List<String> l = new ArrayList<String>();
                                for (int step = 0; step <= 1; step++) {
                                    for (int i = 0; i < cols.length; i++) {
                                        NEXT: for (int j = 0; j < cols[i].length; j++) {
                                            String col = cols[i][j];
                                            if ((step == 0 && col.toLowerCase().startsWith(base))
                                                    || (step == 1 && col.toLowerCase().indexOf(base) > 0)) {
                                                for (int k = 0; k < l.size(); k++) {
                                                    if (l.get(k).equals(col))
                                                        continue NEXT;
                                                }
                                                l.add(col);
                                            }
                                        }
                                    }
                                }
                                if (l.isEmpty())
                                    return;
                                if (lastIdx == -1)
                                    lastIdx = 0;
                                else {
                                    lastIdx += (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0 ? -1 : 1;
                                    if (lastIdx >= l.size())
                                        lastIdx = 0;
                                    else if (lastIdx < 0)
                                        lastIdx = l.size() - 1;
                                }
                                editor.setText(editor.getText().substring(0, pos) + addEscape(l.get(lastIdx)) + ":"); //$NON-NLS-1$
                                editor.setCaretPosition(editor.getText().length());
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
                    if (!Character.isLetterOrDigit(c))
                        sb.append('\\');
                    sb.append(c);
                }
                return sb.toString();
            }

            private String removeEscape(String s) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c == '\\' && i < s.length() - 1)
                        c = s.charAt(++i);
                    sb.append(c);
                }
                return sb.toString();
            }
        });
    }
}
