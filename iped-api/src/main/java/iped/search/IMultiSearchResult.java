/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped.search;

import iped.data.IIPEDSource;
import iped.data.IItemId;

/**
 *
 * @author WERNECK
 */
public interface IMultiSearchResult {

    IItemId getItem(int i);

    IIPEDSource getIPEDSource();

    Iterable<IItemId> getIterator();

    int getLength();

    float getScore(int i);

}
