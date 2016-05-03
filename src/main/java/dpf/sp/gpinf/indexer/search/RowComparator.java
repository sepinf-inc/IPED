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

import java.io.IOException;
import java.util.Comparator;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.Bits;

import dpf.sp.gpinf.indexer.process.IndexItem;

public class RowComparator implements Comparator<Integer> {

  private int col;
  private boolean bookmarkCol = false;
  private boolean scoreCol = false;

  private volatile static AtomicReader atomicReader;

  private App app = App.get();

  protected SortedDocValues sdv;
  private NumericDocValues ndv;
  private Bits docsWithField;

  public RowComparator(int col) {
    this.col = col;

    if (col >= ResultTableModel.fixedCols.length) {      
      col -= ResultTableModel.fixedCols.length;
      String[] fields = ResultTableModel.fields;

      if (fields[col].equals(ResultTableModel.BOOKMARK_COL)) {
        bookmarkCol = true;
      } else if (fields[col].equals(ResultTableModel.SCORE_COL)) {
        scoreCol = true;
      } else {
        loadDocValues(fields[col]);
      }
    }
  }

  public RowComparator(String indexedField) {
    loadDocValues(indexedField);
  }

  private void loadDocValues(String indexedField) {
    try {
      if (atomicReader == null) {
        atomicReader = SlowCompositeReaderWrapper.wrap(app.reader);
      }

      if (IndexItem.getMetadataTypes().get(indexedField) == null || !IndexItem.getMetadataTypes().get(indexedField).equals(String.class)) {
        ndv = atomicReader.getNumericDocValues(indexedField);
        docsWithField = atomicReader.getDocsWithField(indexedField);
        if (ndv == null) {
          ndv = atomicReader.getNumericDocValues("_num_" + indexedField);
          docsWithField = atomicReader.getDocsWithField("_num_" + indexedField);
        }
      }
      if (ndv == null) {
        sdv = atomicReader.getSortedDocValues(indexedField);
        if (sdv == null) {
          sdv = atomicReader.getSortedDocValues("_" + indexedField);
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void closeAtomicReader() throws IOException {
    if (atomicReader != null) {
      atomicReader.close();
    }
    atomicReader = null;
  }

  public boolean isStringComparator() {
    return sdv != null || bookmarkCol;
  }

  @Override
  public int compare(Integer a, Integer b) {

    if (Thread.currentThread().isInterrupted()) {
      throw new RuntimeException("Ordenação cancelada.");
    }

    if (scoreCol) {
      return (int) (app.results.scores[a] - app.results.scores[b]);
    }

    a = app.results.docs[a];
    b = app.results.docs[b];

    if (col == 1) {
      if (app.marcadores.selected[app.getIDs()[a]] == app.marcadores.selected[app.getIDs()[b]]) {
        return 0;
      } else if (app.marcadores.selected[app.getIDs()[a]] == true) {
        return -1;
      } else {
        return 1;
      }

    } else if (bookmarkCol) {
      return app.marcadores.getLabels(app.getIDs()[a]).compareTo(app.marcadores.getLabels(app.getIDs()[b]));
    } else if (sdv != null) {
      return sdv.getOrd(a) - sdv.getOrd(b);
    } else if (ndv != null) {
      if (docsWithField.get(a)) {
        if (docsWithField.get(b)) {
          return Long.compare(ndv.get(a), ndv.get(b));
        } else {
          return 1;
        }
      } else if (docsWithField.get(b)) {
        return -1;
      } else {
        return 0;
      }
    } else {
      return 0;
    }
  }

}
