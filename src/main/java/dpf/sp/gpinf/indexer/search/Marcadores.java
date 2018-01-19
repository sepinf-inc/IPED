/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.Util;

public class Marcadores implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4728708012271393485L;
	
	public static String EXT = "." + Versao.APP_EXT.toLowerCase();
	public static String STATEFILENAME = "marcadores" + EXT;
	
	static int labelBits = Byte.SIZE;

	private boolean[] selected;
	private ArrayList<byte[]> labels;
	private TreeMap<Integer, String> labelNames = new TreeMap<Integer, String>();
	private int selectedItens = 0, totalItems, lastId;

	private LinkedHashSet<String> typedWords = new LinkedHashSet<String>();
	private File indexDir;
	private File stateFile, cookie;
	
	private transient IPEDSource ipedCase;

	public Marcadores(IPEDSource ipedCase, File modulePath) {
		this(ipedCase.getTotalItens(), ipedCase.getLastId(), modulePath);
		this.ipedCase = ipedCase;
	}
	
	public Marcadores(int totalItens, int lastId, final File modulePath) {
		this.totalItems = totalItens;
		this.lastId = lastId;
		selected = new boolean[lastId + 1];
		labels = new ArrayList<byte[]>();
		indexDir = new File(modulePath, "index");
		long date = indexDir.lastModified();
        String tempdir = System.getProperty("java.io.basetmpdir");
        if (tempdir == null) System.getProperty("java.io.tmpdir");
		cookie = new File(tempdir, "indexer" + date + EXT);
		stateFile = new File(modulePath, STATEFILENAME);
		try {
			stateFile = stateFile.getCanonicalFile();
		} catch (IOException e) {}
	}
	
	public int getLastId(){
		return lastId;
	}
	
	public int getTotalItens(){
		return this.totalItems;
	}

	public File getIndexDir() {
		return indexDir;
	}
	
	public TreeMap<Integer, String> getLabelMap(){
		return labelNames;
	}
	
	public LinkedHashSet<String> getTypedWords(){
		return typedWords;
	}
	
	public int getTotalSelected(){
		return selectedItens;
	}
	
	public boolean isSelected(int id){
		return selected[id];
	}
	
	public void clearSelected(){
		selectedItens = 0;
        for (int i = 0; i < selected.length; i++) {
          selected[i] = false;
        }
	}
	
	public void selectAll(){
		selectedItens = totalItems;
		int maxLuceneId = ipedCase.getReader().maxDoc() - 1;
        for (int i = 0; i <= maxLuceneId; i++) {
          selected[ipedCase.getId(i)] = true;
        }
	}

	public String getLabels(int id) {

		ArrayList<Integer> labelIds = getLabelIds(id);
		String result = "";
		for (int i = 0; i < labelIds.size(); i++) {
			result += labelNames.get(labelIds.get(i));
			if (i < labelIds.size() - 1)
				result += " | ";
		}

		return result;
	}
	
	public ArrayList<Integer> getLabelIds(int id){
		ArrayList<Integer> labelIds = new ArrayList<Integer>();
		if (labelNames.size() > 0)
			for (int i : labelNames.keySet()) {
				if(hasLabel(id, i))
					labelIds.add(i);
			}
		
		return labelIds;
	}


	public void addLabel(ArrayList<Integer> ids, int label) {
		int labelOrder = label / labelBits;
		int labelMod = label % labelBits;
		for (int i = 0; i < ids.size(); i++) {
			int id = ids.get(i);
			labels.get(labelOrder)[id] = (byte) (labels.get(labelOrder)[id] | (1 << labelMod));
		}

	}
	
	public final boolean hasLabel(int id){
		boolean hasLabel = false;
		for(byte[] b : labels){
			hasLabel = b[id] != 0;
			if(hasLabel)
				return true;
		}
		return hasLabel;
	}
	
	public final byte[] getLabelBits(int[] labelids){
		byte[] bits = new byte[labels.size()];  
		for(int label : labelids)
			bits[label / labelBits] |= (int) Math.pow(2, label % labelBits);
		
		return bits;
	}
	
	public final boolean hasLabel(int id, byte[] labelbits){
		boolean hasLabel = false;
		for(int i = 0; i < labelbits.length; i++){
			hasLabel = (labels.get(i)[id] & labelbits[i]) != 0;
			if(hasLabel)
				return true;
		}
		return hasLabel;
	}
	
	public final boolean hasLabel(int id, int label) {
		int p = (int) Math.pow(2, label % labelBits);
		int bit = labels.get(label / labelBits)[id] & p;
		return bit != 0;

	}

	public void removeLabel(ArrayList<Integer> ids, int label) {
		int labelOrder = label / labelBits;
		int labelMod = label % labelBits;
		for (int i = 0; i < ids.size(); i++) {
			int id = ids.get(i);
			labels.get(labelOrder)[id] = (byte) (labels.get(labelOrder)[id] & (~(1 << labelMod)));
		}

	}

	public int newLabel(String labelName) {
		
		int labelId = getLabelId(labelName);
		if(labelId != -1)
			return labelId;
		
		if (labelNames.size() > 0)
			for (int i = 0; i <= labelNames.lastKey(); i++)
				if (labelNames.get(i) == null) {
					labelId = i;
					break;
				}

		if (labelId == -1 && labelNames.size() % labelBits == 0) {
			byte[] newLabels = new byte[selected.length];
			labels.add(newLabels);
		}
		if (labelId == -1)
			labelId = labelNames.size();

		labelNames.put(labelId, labelName);

		return labelId;
	}

	public void delLabel(int label) {
		if(label == -1)
			return;
		labelNames.remove(label);
		
		int labelOrder = label / labelBits;
		int labelMod = label % labelBits;
		for (int i = 0; i < labels.get(labelOrder).length; i++) {
			labels.get(labelOrder)[i] = (byte) (labels.get(labelOrder)[i] & (~(1 << labelMod)));
		}
	}
	
	public void changeLabel(int labelId, String newLabel){
		if(labelId != -1)
			labelNames.put(labelId, newLabel);
	}

	public int getLabelId(String labelName) {
		for (int i : labelNames.keySet()){
			if (labelNames.get(i).equals(labelName))
				return i;
		}

		return -1;
	}
	
	public String getLabelName(int labelId){
		return labelNames.get(labelId);
	}
	
	public LuceneSearchResult filtrarMarcadores(LuceneSearchResult result, Set<String> labelNames, IPEDSource ipedCase) throws Exception{
	  	result = result.clone();
	  	
	  	int[] labelIds = new int[labelNames.size()];
	  	int i = 0;
	  	for(String labelName : labelNames)
	  		labelIds[i++] = getLabelId(labelName);
		byte[] labelBits = getLabelBits(labelIds);
	  	
		for (i = 0; i < result.getLength(); i++)
			if (!hasLabel(ipedCase.getId(result.getLuceneIds()[i]), labelBits)) {
				result.getLuceneIds()[i] = -1;
			}

		result.clearResults();
		return result;
	  }
	  
	  public LuceneSearchResult filtrarSemEComMarcadores(LuceneSearchResult result, Set<String> labelNames, IPEDSource ipedCase) throws Exception{
		  	result = result.clone();
		  	
		  	int[] labelIds = new int[labelNames.size()];
		  	int i = 0;
		  	for(String labelName : labelNames)
		  		labelIds[i++] = getLabelId(labelName);
			byte[] labelBits = getLabelBits(labelIds);
		  	
			for (i = 0; i < result.getLength(); i++)
				if (hasLabel(ipedCase.getId(result.getLuceneIds()[i])) && !hasLabel(ipedCase.getId(result.getLuceneIds()[i]), labelBits)) {
					result.getLuceneIds()[i] = -1;
				}
	
			result.clearResults();
			return result;
	  }
	  
	  public LuceneSearchResult filtrarSemMarcadores(LuceneSearchResult result, IPEDSource ipedCase){
		  	result = result.clone();
			for (int i = 0; i < result.getLength(); i++)
				if (hasLabel(ipedCase.getId(result.getLuceneIds()[i]))) {
					result.getLuceneIds()[i] = -1;
				}
	
			result.clearResults();
			return result;
	  }
	
	  public LuceneSearchResult filtrarSelecionados(LuceneSearchResult result, IPEDSource ipedCase) throws Exception {
		  	result = result.clone();
			for (int i = 0; i < result.getLength(); i++)
				if (!selected[ipedCase.getId(result.getLuceneIds()[i])]) {
					result.getLuceneIds()[i] = -1;
				}
	
			result.clearResults();
			return result;
	  }

	public void saveState() {
		try {			
	        Log.error("stateFile",stateFile.getAbsolutePath());
            Log.error("cookie",cookie.getAbsolutePath());
			if(stateFile.canWrite() || (!stateFile.exists() && IOUtil.canCreateFile(stateFile.getParentFile())))
				saveState(stateFile);
			else
				saveState(cookie);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void saveState(File file) throws IOException {
		//SaveStateThread.getInstance().saveState(this, file);
	    Log.error("Marcadores",file.getAbsolutePath());
		Util.writeObject(this, file.getAbsolutePath());
	}

	public void addToTypedWords(String texto) {

		if (!texto.trim().isEmpty() && !typedWords.contains(texto)) {
			typedWords.add(texto);
			saveState();
		}
	}

	public void loadState() {
		try {
			if (cookie.exists() && (!stateFile.exists() || cookie.lastModified() > stateFile.lastModified()))
				loadState(cookie);
			
			else if(stateFile.exists())
				loadState(stateFile);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadState(File file) throws IOException, ClassNotFoundException {

		Marcadores state = load(file);
		
		if(state.selected != null /*&&  state.read != null*/){
			int len = Math.min(state.selected.length, this.selected.length);
			System.arraycopy(state.selected, 0, this.selected, 0, len);
		}
		
		this.labels.clear();
		for(byte[] array : state.labels){
			byte[] newArray = new byte[lastId + 1];
			int len = Math.min(newArray.length, array.length);
			System.arraycopy(array, 0, newArray, 0, len);
			this.labels.add(newArray);
		}
		
		this.typedWords = state.typedWords;
		this.selectedItens = state.selectedItens;
		this.labelNames = state.labelNames;

	}
	
	public static Marcadores load(File file) throws ClassNotFoundException, IOException{
		return (Marcadores) Util.readObject(file.getAbsolutePath());
	}
	
	public void setSelected(boolean value, int id, IPEDSource ipedCase) {
		setSelected(value, id, true, ipedCase);
	}

	private void setSelected(boolean value, int id, boolean changeCount, IPEDSource ipedCase) {
		if (value != selected[id] && changeCount) {
			if (value) selectedItens++;
			else selectedItens--;
		}
		// seta valor na versão de visualização ou vice-versa
		selected[id] = value;
		setValueAtOtherVersion(value, id, selected, ipedCase);
	}

	// seta valor na outra versao
	private void setValueAtOtherVersion(boolean value, int doc, boolean[] array, IPEDSource ipedCase) {
		Integer id2 = ipedCase.getViewToRawMap().getRaw(doc);
		if (id2 != null && array[id2] != (Boolean) value)
			setSelected(value, id2, false, ipedCase);
		id2 = ipedCase.getViewToRawMap().getView(doc);
		if (id2 != null && array[id2] != (Boolean) value)
			setSelected(value, id2, false, ipedCase);
	}

}
