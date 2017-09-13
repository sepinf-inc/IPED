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
package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.highlight.TextFragment;

import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.util.DateUtil;

public class ResultTableModel extends AbstractTableModel implements SearchResultTableModel{

  private static final long serialVersionUID = 1L;
  
  private static final List<String> dateFields = Arrays.asList(IndexItem.ACCESSED, IndexItem.MODIFIED, IndexItem.CREATED, IndexItem.RECORDDATE);
  
  private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

  public static String BOOKMARK_COL = "marcador";
  public static String SCORE_COL = "score";

  public static String[] fields;

  private static int fixedColdWidths[] = {55, 20};
  public static String[] fixedCols = {"", ""};

  private static String[] columnNames = {};

  public void initCols() {

    try {
      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {

          updateCols();
          App.get().resultsModel.fireTableStructureChanged();

          for (int i = 0; i < fixedColdWidths.length; i++) {
            App.get().resultsTable.getColumnModel().getColumn(i).setPreferredWidth(fixedColdWidths[i]);
          }

          for (int i = 0; i < ColumnsManager.getInstance().colState.initialWidths.size(); i++) {
            TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i + fixedColdWidths.length);
            tc.setPreferredWidth(ColumnsManager.getInstance().colState.initialWidths.get(i));

            ColumnsManager.getInstance().setColumnRenderer(tc);
          }

        }
      });
    } catch (InvocationTargetException | InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  public void updateCols() {

    ArrayList<String> cols = new ArrayList<String>();
    for (String col : fixedCols) {
      cols.add(col);
    }

    fields = ColumnsManager.getInstance().getLoadedCols();
    for (String col : fields) {
      cols.add(col.substring(0, 1).toUpperCase() + col.substring(1));
    }

    columnNames = cols.toArray(new String[0]);
  }

  private SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss z");
  private SimpleDateFormat fatAccessedDf = new SimpleDateFormat("dd/MM/yyyy");

  public ResultTableModel() {
    super();
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    fatAccessedDf.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Override
  public int getColumnCount() {
    return columnNames.length;
  }

  @Override
  public int getRowCount() {
    return App.get().ipedResult.getLength();
  }

  @Override
  public String getColumnName(int col) {
    if (col == 0) {
      return String.valueOf(App.get().ipedResult.getLength());
    } else {
      return columnNames[col];
    }
  }

  public void updateLengthHeader(long mb) {
    for (int i = 0; i < columnNames.length; i++) {
      if (IndexItem.LENGTH.equalsIgnoreCase(columnNames[i])) {
        int col = App.get().resultsTable.convertColumnIndexToView(i);
        if (mb == -1) {
          App.get().resultsTable.getColumnModel().getColumn(col).setHeaderValue(
              columnNames[i] + " (...)");
        } else {
          App.get().resultsTable.getColumnModel().getColumn(col).setHeaderValue(
              columnNames[i] + " (" + NumberFormat.getNumberInstance().format(mb) + "MB)");
        }
      }
    }

  }

  @Override
  public boolean isCellEditable(int row, int col) {
    if (col == 1) {
      return true;
    } else {
      return false;
    }
  }

  App app = App.get();

  @Override
  public void setValueAt(Object value, int row, int col) {

	app.appCase.getMultiMarcadores().setSelected((Boolean)value, App.get().ipedResult.getItem(row), app.appCase);	
	//app.appCase.getMarcadores().setSelected((Boolean)value, app.appCase.getIds()[app.results.getLuceneIds()[row]], app.appCase);
    if(!MarcadoresController.get().isMultiSetting()){
    	app.appCase.getMultiMarcadores().saveState();
    	MarcadoresController.get().atualizarGUI();
    }
  }

  @Override
  public Class<?> getColumnClass(int c) {
    if (c == 1) {
      return Boolean.class;
    } else if (columnNames[c].equalsIgnoreCase(IndexItem.LENGTH)) {
      return Integer.class;
    } else {
      return String.class;
    }
  }
  
  @Override
  public MultiSearchResult getSearchResult() {
	return App.get().ipedResult;
  }

  private Document doc;
  private int lastDocRead = -1;
  
  private AtomicReader atomicReader;
  private Map<String, SortedSetDocValues> ssdvCache = new HashMap<String, SortedSetDocValues>();

  @Override
  public Object getValueAt(int row, int col) {
    
	if (col == 0)
		return String.valueOf(App.get().resultsTable.convertRowIndexToView(row) + 1);
	      
	String value = "";
	
	ItemId item = App.get().ipedResult.getItem(row);
    int docId = App.get().appCase.getLuceneId(item);
    
    if (docId != lastDocRead) {
        try {
			doc = app.appCase.getSearcher().doc(docId);
		} catch (IOException e) {
			e.printStackTrace();
	        return "ERRO";
		}
    }
    lastDocRead = docId;
      
    if (col == 1) {
      return app.appCase.getMultiMarcadores().isSelected(app.ipedResult.getItem(row));
      
    } else {
      try {
        int fCol = col - fixedCols.length;
        String field = fields[fCol];

        if (field.equals(SCORE_COL)) {
          return app.ipedResult.getScore(row);
        }

        if (field.equals(BOOKMARK_COL)) {
          return app.appCase.getMultiMarcadores().getLabels(app.ipedResult.getItem(row));
        }
        
        if(atomicReader != App.get().appCase.getAtomicReader()){
            atomicReader = App.get().appCase.getAtomicReader();
            ssdvCache.clear();
        }
        
        SortedNumericDocValues sndv = atomicReader.getSortedNumericDocValues(field);
        if(sndv == null)
            sndv = atomicReader.getSortedNumericDocValues("_num_" + field);
        
        SortedSetDocValues ssdv = ssdvCache.get(field);
        if(ssdv == null){
            ssdv = atomicReader.getSortedSetDocValues(field);
            if(ssdv == null)
                ssdv = atomicReader.getSortedSetDocValues("_" + field);
            ssdvCache.put(field, ssdv);
        }
        
        boolean mayBeNumeric = MetadataPanel.mayBeNumeric(field);
        StringBuilder sb = new StringBuilder();
        
        if((mayBeNumeric && sndv != null) || ssdv == null){
            String[] values = doc.getValues(field);
            if(mayBeNumeric && sndv != null && values.length > 1){
                Arrays.sort(values, new Comparator<String>(){
                    @Override
                    public int compare(String o1, String o2){
                        return Double.valueOf(o1).compareTo(Double.valueOf(o2));
                    }
                });
            }
            for(int i = 0; i < values.length; i++){
                sb.append(values[i]);
                if(i != values.length - 1) sb.append(" | ");
            }
        }
        else if(ssdv != null){
            ssdv.setDocument(docId);
            long ord;
            boolean first = true;
            while((ord = ssdv.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS){
                if(!first) sb.append(" | ");
                sb.append(ssdv.lookupOrd(ord).utf8ToString());
                first = false;
            }
        }
        
        value = sb.toString().trim();
        
        if (value.isEmpty())
          return value;
        
        if(dateFields.contains(field))
            try {
              Date date = DateUtil.stringToDate(value);
              if(field.equals(IndexItem.ACCESSED)){
            	  if(doc.get(SleuthkitReader.IN_FAT_FS) != null)
            		  return fatAccessedDf.format(date);
              }
              return df.format(date);
    
            } catch (Exception e) {
                //e.printStackTrace();
            }

        if (field.equals(IndexItem.LENGTH)) {
          value = numberFormat.format(Long.valueOf(value));
          
        } else if (field.equals(IndexItem.NAME)) {
          TextFragment[] fragments = TextHighlighter.getHighlightedFrags(false, value, field, 0);
          if (fragments[0].getScore() > 0) {
            value = "<html><nobr>" + fragments[0].toString() + "</html>";
          }
        }

      } catch (Exception e) {
        e.printStackTrace();
        return "ERRO";
      }
    }

    return value;

  }

}
