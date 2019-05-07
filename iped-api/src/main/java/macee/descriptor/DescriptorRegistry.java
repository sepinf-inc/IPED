/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

import java.io.IOException;
import java.util.List;

/**
 *
 * @author WERNECK
 */
public interface DescriptorRegistry<T extends Descriptor> extends List<T> {

    @Override
    boolean add(T e);

    T byGuid(String guid);

    T byName(String name);

    DescriptorRegistry loadFromFile(String filename) throws IOException;

    @Override
    boolean remove(Object o);

    void saveSingleDescriptor(T obj, String file) throws IOException;

    void sort();

}
