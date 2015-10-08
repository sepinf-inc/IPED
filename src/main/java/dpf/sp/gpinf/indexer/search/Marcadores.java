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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.util.SearchStateFilter;
import dpf.sp.gpinf.indexer.util.Util;

public class Marcadores implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4728708012271393485L;
	/**
	 * 
	 */
	public static String HISTORY_DIV = "-----------------------Histórico-----------------------";
	
	public static String EXT = "." + Versao.APP_EXT.toLowerCase();
	public static String STATEFILENAME = "marcadores" + EXT;
	static int labelBits = Byte.SIZE;

	boolean[] selected, read;
	ArrayList<byte[]> labels;
	int selectedItens = 0;
	TreeMap<Integer, String> labelNames = new TreeMap<Integer, String>();

	LinkedHashSet<String> typedWords = new LinkedHashSet<String>();
	private File indexDir;
	File stateFile, cookie;
	public boolean multiSetting = false;

	private static JFileChooser fileChooser;
	private static SearchStateFilter filtro;

	public Marcadores(final String basePath) {
		selected = new boolean[App.get().lastId + 1];
		//read = new boolean[App.get().lastId + 1];
		labels = new ArrayList<byte[]>();
		indexDir = new File(basePath, "index");
		long date = indexDir.lastModified();
		cookie = new File(Configuration.javaTmpDir, "indexer" + date + EXT);
		stateFile = new File(basePath, STATEFILENAME);
		try {
			stateFile = stateFile.getCanonicalFile();
		} catch (IOException e) {}
			

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				fileChooser = new JFileChooser();
				fileChooser.setCurrentDirectory(new File(basePath));
				filtro = new SearchStateFilter();
			}
		});

	}
	
	public void resetAndSetIndexDir(File indexDir){
		//read = null;
		selected = null;
		selectedItens = 0;
		typedWords = new LinkedHashSet<String>();
		this.indexDir = indexDir;
	}

	public File getIndexDir() {
		return indexDir;
	}
	
	public TreeMap<Integer, String> getLabelMap(){
		return labelNames;
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
		
		int labelId = -1;
		if (labelNames.size() > 0)
			for (int i = 0; i <= labelNames.lastKey(); i++)
				if (labelNames.get(i) == null) {
					labelId = i;
					break;
				}

		if (labelId == -1 && labelNames.size() % labelBits == 0) {
			byte[] newLabels = new byte[App.get().lastId + 1];
			labels.add(newLabels);
		}
		if (labelId == -1)
			labelId = labelNames.size();

		labelNames.put(labelId, labelName);

		return labelId;
	}

	public void delLabel(int label) {
		
		labelNames.remove(label);
		
		int labelOrder = label / labelBits;
		int labelMod = label % labelBits;
		for (int i = 0; i < labels.get(labelOrder).length; i++) {
			labels.get(labelOrder)[i] = (byte) (labels.get(labelOrder)[i] & (~(1 << labelMod)));
		}
	}
	
	public void changeLabel(int labelId, String newLabel){
		labelNames.put(labelId, newLabel);
	}

	public int getLabelId(String labelName) {
		for (int i : labelNames.keySet())
			if (labelNames.get(i).equals(labelName))
				return i;

		return -1;
	}
	
	private boolean canCreateFile(File dir){
		boolean result;
		File test = new File(dir, "writeTest");
		try {
			result = test.createNewFile();
			
		} catch (IOException e) {
			result = false;
		}
		test.delete();
		
		return result;
	}

	public void saveState() {
		try {			
			if(stateFile.canWrite() || (!stateFile.exists() && canCreateFile(stateFile.getParentFile())))
				saveState(stateFile);
			else
				saveState(cookie);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void saveState(File file) throws IOException {
		Util.writeObject(this, file.getAbsolutePath());
	}

	public void askAndSaveState() {
		fileChooser.setFileFilter(filtro);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			if (!file.getName().endsWith(EXT))
				file = new File(file.getPath() + EXT);

			try {
				saveState(file);
				JOptionPane.showMessageDialog(App.get(), "Marcadores salvos com sucesso", "Sucesso", JOptionPane.INFORMATION_MESSAGE);

			} catch (IOException e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(App.get(), "Erro ao salvar marcadores!", "Erro", JOptionPane.ERROR_MESSAGE);
			}

		}
	}

	public void addToTypedWordList(String texto) {

		if (!texto.equals(HISTORY_DIV) && !texto.trim().isEmpty() && !typedWords.contains(texto) && !App.get().keywordSet.contains(texto)) {

			if (typedWords.size() == 0)
				App.get().termo.addItem(HISTORY_DIV);

			typedWords.add(texto);
			App.get().termo.addItem(texto);
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

		Marcadores state = (Marcadores) Util.readObject(file.getAbsolutePath());
		
		if(state.selected != null /*&&  state.read != null*/){
			System.arraycopy(state.selected, 0, this.selected, 0, state.selected.length);
			//System.arraycopy(state.read, 0, this.read, 0, state.read.length);
		}
		
		for(byte[] array : state.labels){
			byte[] newArray = new byte[App.get().lastId + 1];
			int len = Math.min(newArray.length, array.length);
			System.arraycopy(array, 0, newArray, 0, len);
			this.labels.add(newArray);
		}
		
		this.typedWords = state.typedWords;
		this.selectedItens = state.selectedItens;
		this.labelNames = state.labelNames;

		if (App.get().termo != null) {
			if (typedWords.size() != 0)
				App.get().termo.addItem(HISTORY_DIV);
			for (String text : typedWords) {
				App.get().termo.addItem(text);
			}
			atualizarGUI();
		}

	}

	public void askAndLoadState() {
		fileChooser.setFileFilter(filtro);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			try {
				loadState(file);
				JOptionPane.showMessageDialog(App.get(), "Marcadores carregados com sucesso", "Sucesso", JOptionPane.INFORMATION_MESSAGE);

			} catch (Exception e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(App.get(), "Erro ao carregar marcadores!", "Erro", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void atualizarGUI() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (App.get().resultsTable.getRowCount() > 0) {
					App.get().resultsModel.fireTableRowsUpdated(0, App.get().resultsTable.getRowCount() - 1);
					App.get().galleryModel.fireTableRowsUpdated(0, App.get().gallery.getRowCount() - 1);
				}
				App.get().checkBox.setText(String.valueOf(selectedItens) + " / " + App.get().totalItens);
				App.get().checkBox.setSelected(selectedItens > 0);
				App.get().bookmarksListener.updateModelAndSelection();
			}
		});

	}

	public void setValueAtId(Object value, int id, int col, boolean changeCount) {
		boolean[] array;
		if (col == 1) {
			array = selected;
			if ((Boolean) value != selected[id] && changeCount) {
				if ((Boolean) value)
					selectedItens++;
				else
					selectedItens--;
			}
			// seta valor nos outros fragmentos
			try {
				array[id] = (Boolean) value;
				setValueAtOtherVersion(value, id, col, array);

				if (!multiSetting) {
					if (col == 1)
						saveState();
					atualizarGUI();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// seta valor na outra versao
	private void setValueAtOtherVersion(Object value, int doc, int col, boolean[] array) {
		Integer id2 = App.get().viewToRawMap.getRaw(doc);
		if (id2 != null && array[id2] != (Boolean) value)
			setValueAtId(value, id2, col, false);
		id2 = App.get().viewToRawMap.getView(doc);
		if (id2 != null && array[id2] != (Boolean) value)
			setValueAtId(value, id2, col, false);
	}

}
