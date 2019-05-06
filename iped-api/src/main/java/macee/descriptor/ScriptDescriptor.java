/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

/**
 *
 * @author WERNECK
 */
public interface ScriptDescriptor extends Descriptor {

  String getEvalString();

  String getLanguage();

  void setEvalString(String evalString);

  void setLanguage(String language);
  
}
