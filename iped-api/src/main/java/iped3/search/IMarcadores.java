/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import iped3.IIPEDSource;

/**
 *
 * @author WERNECK
 */
public interface IMarcadores extends Serializable {

    void addLabel(List<Integer> ids, int label);

    void addToTypedWords(String texto);

    void changeLabel(int labelId, String newLabel);

    void clearSelected();

    void delLabel(int label);

    LuceneSearchResult filtrarMarcadores(LuceneSearchResult result, Set<String> labelNames, IIPEDSource ipedCase)
            throws Exception;

    LuceneSearchResult filtrarSelecionados(LuceneSearchResult result, IIPEDSource ipedCase) throws Exception;

    LuceneSearchResult filtrarSemEComMarcadores(LuceneSearchResult result, Set<String> labelNames, IIPEDSource ipedCase)
            throws Exception;

    LuceneSearchResult filtrarSemMarcadores(LuceneSearchResult result, IIPEDSource ipedCase);

    File getIndexDir();

    byte[] getLabelBits(int[] labelids);

    int getLabelId(String labelName);

    ArrayList<Integer> getLabelIds(int id);

    Map<Integer, String> getLabelMap();

    String getLabelName(int labelId);

    public List<String> getLabelList(int itemId);

    int getLastId();

    int getTotalItens();

    int getTotalSelected();

    LinkedHashSet<String> getTypedWords();

    boolean hasLabel(int id);

    boolean hasLabel(int id, byte[] labelbits);

    boolean hasLabel(int id, int label);

    boolean isSelected(int id);

    void loadState();

    void loadState(File file) throws IOException, ClassNotFoundException;

    int newLabel(String labelName);

    void removeLabel(List<Integer> ids, int label);

    void saveState();

    void saveState(File file) throws IOException;

    void selectAll();

    void setSelected(boolean value, int id);

    void updateCookie();

    void setLabelComment(int labelId, String comment);

    String getLabelComment(int labelId);

    int getLabelCount(int labelId);
    
    void setInReport(int labelId, boolean inReport);

    boolean isInReport(int labelId);

    LuceneSearchResult filterInReport(LuceneSearchResult luceneSearch, IIPEDSource ipedCase) throws Exception;

}
