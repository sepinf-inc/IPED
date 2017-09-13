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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.ItemId;

public class RowComparator implements Comparator<Integer> {
	
  private static Logger LOGGER = LoggerFactory.getLogger(RowComparator.class);

  private int col;
  private boolean bookmarkCol = false;
  private boolean scoreCol = false;

  private volatile static AtomicReader atomicReader;
  private static boolean loadDocValues = true;

  private App app = App.get();
  
  private String field;
  private HashSet<String> fieldsToLoad = new HashSet<String>();
  private boolean isLongField = false;
  private boolean isDoubleField = false;

  protected SortedDocValues sdv;
  protected SortedSetDocValues ssdv;
  private NumericDocValues ndv;
  private SortedNumericDocValues sndv;
  private Bits docsWithField;
  
  public static void setLoadDocValues(boolean load){
	loadDocValues = load;
  }

  public RowComparator(int col) {
    this.col = col;

    if (col >= ResultTableModel.fixedCols.length) {      
      col -= ResultTableModel.fixedCols.length;
      String[] fields = ResultTableModel.fields;

      if (fields[col].equals(ResultTableModel.BOOKMARK_COL))
        bookmarkCol = true;
      
      else if (fields[col].equals(ResultTableModel.SCORE_COL))
        scoreCol = true;
      
      else{
          LOGGER.info("Loading sort data for Column: " + fields[col]);
          loadDocValues(fields[col]);
          LOGGER.info("Sort data loaded for Column: " + fields[col]);
      }   
    }
  }

  public RowComparator(String indexedField) {
    loadDocValues(indexedField);
  }

  private void loadDocValues(String indexedField) {
	  field = indexedField;
	  fieldsToLoad.add(field);
	  String[] fixedNumericFields = {IndexItem.ID, IndexItem.PARENTID, IndexItem.SLEUTHID, IndexItem.LENGTH};
	  isLongField = Arrays.asList(fixedNumericFields).contains(field) || 
				Integer.class.equals(IndexItem.getMetadataTypes().get(field)) ||
				Long.class.equals(IndexItem.getMetadataTypes().get(field));
	  isDoubleField = Float.class.equals(IndexItem.getMetadataTypes().get(field)) || 
				Double.class.equals(IndexItem.getMetadataTypes().get(field));
		
	  if(!loadDocValues)
		  return;
	  
    try {
      atomicReader = App.get().appCase.getAtomicReader();

      if (IndexItem.getMetadataTypes().get(indexedField) == null || !IndexItem.getMetadataTypes().get(indexedField).equals(String.class)) {
        ndv = atomicReader.getNumericDocValues(indexedField);
        docsWithField = atomicReader.getDocsWithField(indexedField);
        if (ndv == null) {
          ndv = atomicReader.getNumericDocValues("_num_" + indexedField);
          docsWithField = atomicReader.getDocsWithField("_num_" + indexedField);
        }
        if (ndv == null) {
            sndv = atomicReader.getSortedNumericDocValues(indexedField);
            if (sndv == null)
                sndv = atomicReader.getSortedNumericDocValues("_num_" + indexedField);
        }
      }
      if (ndv == null && sndv == null) {
          ssdv = atomicReader.getSortedSetDocValues(indexedField);
          if (ssdv == null)
              ssdv = atomicReader.getSortedSetDocValues("_" + indexedField);
      }
      if (ndv == null && sndv == null && ssdv == null) {
        sdv = atomicReader.getSortedDocValues(indexedField);
        if (sdv == null)
          sdv = atomicReader.getSortedDocValues("_" + indexedField);
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public static boolean isNewIndexReader(){
	  return atomicReader != App.get().appCase.getAtomicReader();
  }

  public boolean isStringComparator() {
    return sdv != null || ssdv != null || bookmarkCol;
  }

  @Override
  public final int compare(Integer a, Integer b) {
		
		if(Thread.currentThread().isInterrupted())
			throw new RuntimeException("Ordenação cancelada.");
		
		if(scoreCol)
          return (int)(app.ipedResult.getScore(a) - app.ipedResult.getScore(b));
		
		ItemId itemA = app.ipedResult.getItem(a);
		ItemId itemB = app.ipedResult.getItem(b);
		
		a = app.appCase.getLuceneId(itemA);
		b = app.appCase.getLuceneId(itemB);
		
		if(col == 1){
		    if (app.appCase.getMultiMarcadores().isSelected(itemA) == app.appCase.getMultiMarcadores().isSelected(itemB))
              return 0;
          else if (app.appCase.getMultiMarcadores().isSelected(itemA) == true)
              return -1;
          else
              return 1;
		
		}else if(bookmarkCol)
          return app.appCase.getMultiMarcadores().getLabels(itemA).compareTo(app.appCase.getMultiMarcadores().getLabels(itemB));
      
		else if(sdv != null)
			return sdv.getOrd(a) - sdv.getOrd(b);
		
		else if(ssdv != null){
		    int result, k = 0, ordA = -1, ordB = -1;
		    do{
		        ssdv.setDocument(a);
		        int i = 0;
		        while(i++ <= k) ordA = (int)ssdv.nextOrd();
	            ssdv.setDocument(b);
	            i = 0;
	            while(i++ <= k) ordB = (int)ssdv.nextOrd();
	            result = ordA - ordB;
	            k++;
	            
		    }while(result == 0 && ordA != SortedSetDocValues.NO_MORE_ORDS && ordB != SortedSetDocValues.NO_MORE_ORDS);
		    
		    return result;
		}
		else if(sndv != null){
            int result, k = 0, countA = 0, countB = 0;
            do{
                long ordA, ordB;
                sndv.setDocument(a);
                if(k == 0) countA = sndv.count();
                if(k < countA) ordA = sndv.valueAt(k);
                else ordA = Long.MIN_VALUE;
                sndv.setDocument(b);
                if(k == 0) countB = sndv.count();
                if(k < countB) ordB = sndv.valueAt(k);
                else ordB = Long.MIN_VALUE;
                result = Long.compare(ordA, ordB);
                k++;
                
            }while(result == 0 && (k < countA || k < countB));
            
            return result;
        }
		
      else if(ndv != null){
      	if(docsWithField.get(a)){
      		if(docsWithField.get(b))
      			return Long.compare(ndv.get(a), ndv.get(b));
      		else
      			return 1;
      	}else 
      		if(docsWithField.get(b))
      			return -1;
      		else
      			return 0;
      }else{
      	//Ordenação sob demanda caso não haja DocValues (bem mais lenta)
      	try {
				Document doc1 = app.appCase.getReader().document(a, fieldsToLoad);
				Document doc2 = app.appCase.getReader().document(b, fieldsToLoad);
				
				String v1 = doc1.get(field);
				String v2 = doc2.get(field);
				
				if(v1 == null || v1.isEmpty()){
					if(v2 == null || v2.isEmpty())
						return 0;
					else
						return -1;
				}else if(v2 == null || v2.isEmpty())
					return 1;
				
				if(isLongField){
					long l1 = Long.parseLong(v1);
					long l2 = Long.parseLong(v2);
					return Long.compare(l1, l2);
				}
				if(isDoubleField){
					double d1 = Double.parseDouble(v1);
					double d2 = Double.parseDouble(v2);
					return Double.compare(d1, d2);
				}
				
				return v1.compareTo(v2);
				
			} catch (IOException e) {
				return 0;
			}
      	
      }
          
	}

}
