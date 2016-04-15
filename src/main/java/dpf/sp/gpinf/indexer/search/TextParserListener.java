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
package dpf.sp.gpinf.indexer.search;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.SwingUtilities;

public class TextParserListener implements PropertyChangeListener {

  TextParser fileParser;

  public TextParserListener(TextParser parser) {
    fileParser = parser;
  }

  @Override
  public void propertyChange(final PropertyChangeEvent evt) {
    // if(progressMonitor.isCanceled())
    // this.cancel(false);

    if ("progress" == evt.getPropertyName()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          fileParser.progressMonitor.setProgress((Long) evt.getNewValue());
        }
      });

    }

    if ("hits".equals(evt.getPropertyName())) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          fileParser.progressMonitor.setNote("Encontradas " + evt.getNewValue() + " ocorrências");
        }
      });

      if ((Integer) evt.getNewValue() == 1) {
        try {
          App.get().hitsTable.setRowSelectionInterval(0, 0);
          fileParser.firstHitAutoSelected = true;
        } catch (Exception e) {
        }
      }

      App.get().tabbedHits.setTitleAt(0, App.get().getTextViewer().textParser.hits.size() + " Ocorrências");

    }

    // if(!App.get().resultsTable.hasFocus() &&
    // !App.get().topPanel.hasFocus() && !App.get().tabbedHits.hasFocus())
    // while(!App.get().resultsTable.requestFocusInWindow());
  }

}
