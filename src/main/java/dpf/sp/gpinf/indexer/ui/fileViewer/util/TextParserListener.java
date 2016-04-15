package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import dpf.sp.gpinf.indexer.search.HitsTable;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JTabbedPane;

import javax.swing.SwingUtilities;

public class TextParserListener implements PropertyChangeListener {

  TextViewer textViewer;
  HitsTable hitsTable;
  JTabbedPane tabbedHits;

  public TextParserListener(TextViewer viewer, HitsTable hits, JTabbedPane tabHits) {
    textViewer = viewer;
    hitsTable = hits;
    tabbedHits = tabHits;
  }

  @Override
  public void propertyChange(final PropertyChangeEvent evt) {

    if ("progress" == evt.getPropertyName()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          textViewer.textParser.progressMonitor.setProgress((Long) evt.getNewValue());
        }
      });

    }

    if ("hits".equals(evt.getPropertyName())) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          textViewer.textParser.progressMonitor.setNote("Encontradas " + evt.getNewValue() + " ocorrências");
        }
      });

      if ((Integer) evt.getNewValue() == 1) {
        try {
          hitsTable.setRowSelectionInterval(0, 0);
          textViewer.textParser.firstHitAutoSelected = true;
        } catch (Exception e) {
        }
      }

      tabbedHits.setTitleAt(0, textViewer.textParser.hits.size() + " Ocorrências");

    }

    // if(!App.get().resultsTable.hasFocus() &&
    // !App.get().topPanel.hasFocus() && !App.get().tabbedHits.hasFocus())
    // while(!App.get().resultsTable.requestFocusInWindow());
  }

}
