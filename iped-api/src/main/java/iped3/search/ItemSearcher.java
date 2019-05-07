/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import iped3.io.ItemBase;

import java.io.Closeable;
import java.util.List;

/**
 *
 * @author Nassif
 */
public interface ItemSearcher extends Closeable {

    List<ItemBase> search(String luceneQuery);

    String escapeQuery(String string);

}
