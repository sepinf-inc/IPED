/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.datasource;

import java.io.File;

/**
 *
 * @author WERNECK
 */
public interface DataSource {

  String getName();

  File getSourceFile();

  String getUUID();

  void setName(String name);

  void setUUID(String uuid);
  
}
