/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

/**
 *
 * @author WERNECK
 */
public interface IVersionsMap {

    int getMappings();

    Integer getRaw(int view);

    Integer getView(int raw);

    boolean isRaw(int doc);

    boolean isView(int doc);

    void put(int view, int raw);

}
