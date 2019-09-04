/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

import java.util.List;
import java.util.Map;

/**
 *
 * @author WERNECK
 */
public interface EntityDescriptor extends Descriptor {

    Map<String, String> getContext();

    String getIconName();

    String getParentClass();

    List<String> getPredicates();

    String getRdfType();

    void setContext(Map<String, String> context);

    void setIconName(String iconName);

    void setParentClass(String parentClass);

    void setPredicates(List<String> predicates);

    void setRdfType(String type);

}
