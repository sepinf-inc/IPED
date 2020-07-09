/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import iped3.IItemId;

/**
 *
 * @author WERNECK
 */
public interface IMultiMarcadores extends Serializable {

    void addLabel(List<IItemId> ids, String labelName);

    void addToTypedWords(String texto);

    void changeLabel(String oldLabel, String newLabel);

    void clearSelected();

    void clearTypedWords();

    void delLabel(String labelName);

    IMultiSearchResult filtrarMarcadores(IMultiSearchResult result, Set<String> labelNames) throws Exception;

    IMultiSearchResult filtrarSelecionados(IMultiSearchResult result) throws Exception;

    IMultiSearchResult filtrarSemEComMarcadores(IMultiSearchResult result, Set<String> labelNames) throws Exception;

    IMultiSearchResult filtrarSemMarcadores(IMultiSearchResult result);

    TreeSet<String> getLabelMap();

    List<String> getLabelList(IItemId item);

    Collection<IMarcadores> getSingleBookmarks();

    int getTotalSelected();

    LinkedHashSet<String> getTypedWords();

    boolean hasLabel(IItemId item);

    boolean hasLabel(IItemId item, Set<String> labelNames);

    boolean hasLabel(IItemId item, String labelName);

    boolean isSelected(IItemId item);

    void loadState();

    void loadState(File file) throws ClassNotFoundException, IOException;

    void newLabel(String labelName);

    void removeLabel(List<IItemId> ids, String labelName);

    void saveState();

    void saveState(File file) throws IOException;

    void selectAll();

    void setSelected(boolean value, IItemId item);

    public String getLabelComment(String labelName);

    void setLabelComment(String texto, String comment);

    boolean isInReport(String label);

    void setInReport(String label, boolean checked);

}
