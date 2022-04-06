/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import java.io.Closeable;
import java.util.List;

import iped3.IItemBase;

/**
 *
 * @author Nassif
 */
public interface IItemSearcher extends Closeable {

    List<IItemBase> search(String luceneQuery);

    Iterable<IItemBase> searchIterable(String luceneQuery);

    String escapeQuery(String string);

}
