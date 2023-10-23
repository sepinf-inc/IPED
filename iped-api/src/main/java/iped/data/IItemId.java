/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped.data;

/**
 *
 * @author WERNECK
 */
public interface IItemId extends Comparable<IItemId> {

    int getId();

    int getSourceId();

}
