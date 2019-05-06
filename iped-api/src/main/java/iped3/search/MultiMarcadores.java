/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import iped3.ItemId;
import iped3.IPEDSource;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author WERNECK
 */
public interface MultiMarcadores extends Serializable {

  void addLabel(List<ItemId> ids, String labelName);

  void addToTypedWords(String texto);

  void changeLabel(String oldLabel, String newLabel);

  void clearSelected();

  void clearTypedWords();

  void delLabel(String labelName);

  MultiSearchResult filtrarMarcadores(MultiSearchResult result, Set<String> labelNames) throws Exception;

  MultiSearchResult filtrarSelecionados(MultiSearchResult result) throws Exception;

  MultiSearchResult filtrarSemEComMarcadores(MultiSearchResult result, Set<String> labelNames) throws Exception;

  MultiSearchResult filtrarSemMarcadores(MultiSearchResult result);

  TreeSet<String> getLabelMap();

  List<String> getLabelList(ItemId item);

  Collection<Marcadores> getSingleBookmarks();

  int getTotalSelected();

  LinkedHashSet<String> getTypedWords();

  boolean hasLabel(ItemId item);

  boolean hasLabel(ItemId item, Set<String> labelNames);

  boolean hasLabel(ItemId item, String labelName);

  boolean isSelected(ItemId item);

  void loadState();

  void loadState(File file) throws ClassNotFoundException, IOException;

  void newLabel(String labelName);

  void removeLabel(List<ItemId> ids, String labelName);

  void saveState();

  void saveState(File file) throws IOException;

  void selectAll();

  void setSelected(boolean value, ItemId item, IPEDSource ipedCase);
  
  public String getLabelComment(String labelName);

  void setLabelComment(String texto, String comment);

  boolean isInReport(String label);

  void setInReport(String label, boolean checked);
  
}
