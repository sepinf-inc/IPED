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
public interface CommandDescriptor extends Descriptor {

  String commandTemplate();

  String getAlias();

  String getAliasOf();

  Map<String, Object> getDefaults();

  String getNamespace();

  Map<String, String> getParams();

  void setAlias(String alias);

  void setAliasOf(String aliasOf);

  void setDefaults(Map<String, Object> defaults);

  void setNamespace(String namespace);

  void setParams(Map<String, String> params);
  
}
