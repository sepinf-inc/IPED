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

		if (!texto.equals(HISTORY_DIV) && !texto.trim().isEmpty() && !App.get().appCase.getMarcadores().getTypedWords().contains(texto) && !App.get().keywordSet.contains(texto)) {

			if (App.get().appCase.getMarcadores().getTypedWords().size() == 0)
				App.get().termo.addItem(HISTORY_DIV);
			
			App.get().termo.addItem(texto);
			App.get().appCase.getMarcadores().addToTypedWords(texto);
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
				App.get().checkBox.setText(App.get().appCase.getMarcadores().getTotalSelected() + " / " + App.get().appCase.getTotalItens());
				App.get().checkBox.setSelected(App.get().appCase.getMarcadores().getTotalSelected() > 0);
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
				App.get().appCase.getMarcadores().loadState(file);
				atualizarGUIandHistory();
				GerenciadorMarcadores.get().updateList();
				JOptionPane.showMessageDialog(App.get(), "Marcadores carregados com sucesso", "Sucesso", JOptionPane.INFORMATION_MESSAGE);

			} catch (Exception e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(App.get(), "Erro ao carregar marcadores!", "Erro", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	private void atualizarGUIHistory(){
		boolean historyIndex = false;
		for(int i = 0; i < App.get().termo.getItemCount(); i++){
			if(App.get().termo.getItemAt(i).equals(HISTORY_DIV))
				historyIndex = true;
			if(historyIndex)
				App.get().termo.removeItemAt(i--);
		}
		if (App.get().appCase.getMarcadores().getTypedWords().size() != 0){
			App.get().termo.addItem(HISTORY_DIV);
		}
		for (String text : App.get().appCase.getMarcadores().getTypedWords()) {
			App.get().termo.addItem(text);
		}
	}

	public void askAndSaveState() {
		fileChooser.setFileFilter(filtro);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			if (!file.getName().endsWith(Marcadores.EXT))
				file = new File(file.getPath() + Marcadores.EXT);

			try {
				App.get().appCase.getMarcadores().saveState(file);
				JOptionPane.showMessageDialog(App.get(), "Marcadores salvos com sucesso", "Sucesso", JOptionPane.INFORMATION_MESSAGE);

			} catch (IOException e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(App.get(), "Erro ao salvar marcadores!", "Erro", JOptionPane.ERROR_MESSAGE);
			}

		}
	}
}
