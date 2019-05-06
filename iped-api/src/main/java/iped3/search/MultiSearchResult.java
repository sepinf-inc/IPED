/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import iped3.ItemId;
import java.util.Iterator;

/**
 *
 * @author WERNECK
 */
public interface MultiSearchResult {

  ItemId getItem(int i);

  Iterable<ItemId> getIterator();

  int getLength();

  float getScore(int i);
  
}
