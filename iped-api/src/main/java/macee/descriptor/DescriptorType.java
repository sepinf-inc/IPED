/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

import java.io.Serializable;

/**
 *
 * @author WERNECK
 */
public interface DescriptorType extends Comparable<DescriptorType>, Serializable {

  String getDescriptorClass();

  String getLabel();

  boolean isUserCreated();
  
}
