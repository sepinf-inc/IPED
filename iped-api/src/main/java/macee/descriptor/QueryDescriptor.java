/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

import java.util.Map;

/**
 *
 * @author WERNECK
 */
public interface QueryDescriptor extends Descriptor {

    Map<String, String> getParams();

    String getQueryType();

    String getText();

    void setParams(Map<String, String> params);

    void setQueryType(String type);

    void setText(String text);

}
