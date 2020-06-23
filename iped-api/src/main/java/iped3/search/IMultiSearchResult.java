/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import iped3.IItemId;

/**
 *
 * @author WERNECK
 */
public interface IMultiSearchResult {

    IItemId getItem(int i);

    Iterable<IItemId> getIterator();

    int getLength();

    float getScore(int i);

    void setScore(int i, float score);
    
}
