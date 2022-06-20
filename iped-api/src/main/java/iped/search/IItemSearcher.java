/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped.search;

import java.io.Closeable;
import java.util.List;

import iped.data.IItemReader;

/**
 *
 * @author Nassif
 */
public interface IItemSearcher extends Closeable {

    List<IItemReader> search(String luceneQuery);

    Iterable<IItemReader> searchIterable(String luceneQuery);

    String escapeQuery(String string);

}
