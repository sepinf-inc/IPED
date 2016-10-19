package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.search.Marcadores;
import dpf.sp.gpinf.indexer.util.SearchStateFilter;

public class MarcadoresController {
	
	public static final String HISTORY_DIV = "-----------------------Histórico-----------------------";
	
	private static MarcadoresController instance;
	
	private static JFileChooser fileChooser;
	private static SearchStateFilter filtro;
	
	private boolean multiSetting = false;
	
	boolean updatingHistory = false;
	
	private MarcadoresController(){
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				fileChooser = new JFileChooser();
				fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
				filtro = new SearchStateFilter();
			}
		});
	}
	
	public static MarcadoresController get(){
		if(instance == null)
			instance = new MarcadoresController();
		return instance;
	}
	
	public void setMultiSetting(boolean value){
		this.multiSetting = value;
	}
	
	public boolean isMultiSetting(){
		return this.multiSetting;
	}
	
	public void addToRecentSearches(String texto) {

		if (!texto.equals(HISTORY_DIV) && !texto.trim().isEmpty() && !App.get().appCase.getMultiMarcadores().getTypedWords().contains(texto)
				&& !App.get().appCase.getKeywords().contains(texto)) {

			if (App.get().appCase.getMultiMarcadores().getTypedWords().size() == 0)
				App.get().termo.addItem(HISTORY_DIV);
			
			App.get().termo.addItem(texto);
			App.get().appCase.getMultiMarcadores().addToTypedWords(texto);
		}
	}
	
	public void atualizarGUIandHistory(){
		atualizarGUIHistory();
		atualizarGUI();
	}

	public void atualizarGUI() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (App.get().resultsTable.getRowCount() > 0) {
					App.get().resultsModel.fireTableRowsUpdated(0, App.get().resultsTable.getRowCount() - 1);
					App.get().galleryModel.fireTableRowsUpdated(0, App.get().gallery.getRowCount() - 1);
				}
				App.get().checkBox.setText(App.get().appCase.getMultiMarcadores().getTotalSelected() + " / " + App.get().appCase.getTotalItens());
				App.get().checkBox.setSelected(App.get().appCase.getMultiMarcadores().getTotalSelected() > 0);
				App.get().bookmarksListener.updateModelAndSelection();
				GerenciadorMarcadores.updateCounters();
			}
		});

	}
	
	public void askAndLoadState() {
		fileChooser.setFileFilter(filtro);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			try {
				App.get().appCase.getMultiMarcadores().loadState(file);
				atualizarGUIandHistory();
				GerenciadorMarcadores.get().updateList();
				JOptionPane.showMessageDialog(App.get(), "Marcadores carregados com sucesso", "Sucesso", JOptionPane.INFORMATION_MESSAGE);

			} catch (Exception e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(App.get(), "Erro ao carregar marcadores!", "Erro", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	public void atualizarGUIHistory(){
		updatingHistory = true;
		Object prevText = App.get().termo.getSelectedItem();
		App.get().termo.removeAllItems();
	    for (String word : App.get().appCase.getKeywords())
	        App.get().termo.addItem(word);
	    
		if (App.get().appCase.getMultiMarcadores().getTypedWords().size() != 0)
			App.get().termo.addItem(HISTORY_DIV);
		
		for (String text : App.get().appCase.getMultiMarcadores().getTypedWords()) {
			App.get().termo.addItem(text);
		}
		App.get().termo.setSelectedItem(prevText);
		updatingHistory = false;
	}

	public void askAndSaveState() {
		fileChooser.setFileFilter(filtro);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			if (!file.getName().endsWith(Marcadores.EXT))
				file = new File(file.getPath() + Marcadores.EXT);

			try {
				App.get().appCase.getMultiMarcadores().saveState(file);
				JOptionPane.showMessageDialog(App.get(), "Marcadores salvos com sucesso", "Sucesso", JOptionPane.INFORMATION_MESSAGE);

			} catch (IOException e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(App.get(), "Erro ao salvar marcadores!", "Erro", JOptionPane.ERROR_MESSAGE);
			}

		}
	}
}
