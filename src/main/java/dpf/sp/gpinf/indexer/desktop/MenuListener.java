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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;

import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.Viewer;

public class MenuListener implements ActionListener {

  JFileChooser fileChooser = new JFileChooser();
  FileFilter defaultFilter = fileChooser.getFileFilter(), csvFilter = new Filtro();
  MenuClass menu;
  static String CSV = ".csv";

  public MenuListener(MenuClass menu) {
    this.menu = menu;
    File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
    fileChooser.setCurrentDirectory(moduleDir.getParentFile());
  }

  private class Filtro extends FileFilter {

    @Override
    public boolean accept(File f) {
      if (f.isDirectory()) {
        return true;
      }
      if (f.getName().endsWith(CSV)) {
        return true;
      }

      return false;
    }

    @Override
    public String getDescription() {
      return "Comma Separeted Values (" + CSV + ")";
    }

  }

  @Override
  public void actionPerformed(ActionEvent e) {

    if (e.getSource() == menu.disposicao) {
      App.get().alterarDisposicao();

    } else if (e.getSource() == menu.marcarSelecionados) {
      MarcadoresController.get().setMultiSetting(true);
      int col = App.get().resultsTable.convertColumnIndexToView(1);
      for (Integer row : App.get().resultsTable.getSelectedRows()) {
        App.get().resultsTable.setValueAt(true, row, col);
      }
      MarcadoresController.get().setMultiSetting(false);
      App.get().appCase.getMultiMarcadores().saveState();
      MarcadoresController.get().atualizarGUI();

    } else if (e.getSource() == menu.desmarcarSelecionados) {
    	MarcadoresController.get().setMultiSetting(true);
      int col = App.get().resultsTable.convertColumnIndexToView(1);
      for (Integer row : App.get().resultsTable.getSelectedRows()) {
        App.get().resultsTable.setValueAt(false, row, col);
      }
      MarcadoresController.get().setMultiSetting(false);
      App.get().appCase.getMultiMarcadores().saveState();
      MarcadoresController.get().atualizarGUI();

    }
    if (e.getSource() == menu.lerSelecionados) {
    	MarcadoresController.get().setMultiSetting(true);
      int col = App.get().resultsTable.convertColumnIndexToView(2);
      for (Integer row : App.get().resultsTable.getSelectedRows()) {
        App.get().resultsTable.setValueAt(true, row, col);
      }
      MarcadoresController.get().setMultiSetting(false);
      App.get().appCase.getMultiMarcadores().saveState();
      MarcadoresController.get().atualizarGUI();

    } else if (e.getSource() == menu.deslerSelecionados) {
    	MarcadoresController.get().setMultiSetting(true);
      int col = App.get().resultsTable.convertColumnIndexToView(2);
      for (Integer row : App.get().resultsTable.getSelectedRows()) {
        App.get().resultsTable.setValueAt(false, row, col);
      }
      MarcadoresController.get().setMultiSetting(false);
      App.get().appCase.getMultiMarcadores().saveState();
      MarcadoresController.get().atualizarGUI();

    } else if (e.getSource() == menu.exportarSelecionados) {
      fileChooser.setFileFilter(defaultFilter);
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
        File dir = fileChooser.getSelectedFile();
        ArrayList<ItemId> selectedIds = new ArrayList<ItemId>();
        for (int row : App.get().resultsTable.getSelectedRows()) {
          ItemId item = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(row));
          selectedIds.add(item);
          // exporta versão nao selecionada caso exista
					/*Integer docId2 = App.get().viewToRawMap.getRaw(docId);
           if (docId2 == null)
           docId2 = App.get().viewToRawMap.getView(docId);
           if (docId2 != null)
           selectedIds.add(docId2);
           */
        }

        (new CopiarArquivos(dir, selectedIds)).execute();
      }

    } else if (e.getSource() == menu.copiarSelecionados) {
      ArrayList<Integer> selectedIds = new ArrayList<Integer>();
      for (int row : App.get().resultsTable.getSelectedRows()) {
    	ItemId item = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(row));
    	int luceneId = App.get().appCase.getLuceneId(item);
        selectedIds.add(luceneId);
      }

      fileChooser.setFileFilter(csvFilter);
      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        if (!file.getName().endsWith(CSV)) {
          file = new File(file.getAbsolutePath() + CSV);
        }
        (new CopiarPropriedades(file, selectedIds)).execute();
      }

    } else if (e.getSource() == menu.copiarMarcados) {
      ArrayList<Integer> uniqueSelectedIds = new ArrayList<Integer>();
      for (int docId = 0; docId < App.get().appCase.getReader().maxDoc(); docId++) {
    	ItemId item = App.get().appCase.getItemId(docId);
        if (App.get().appCase.getMultiMarcadores().isSelected(item)
            && !App.get().appCase.getAtomicSource(docId).getViewToRawMap().isView(item.getId())) {
          uniqueSelectedIds.add(docId);
        }

      }
      fileChooser.setFileFilter(csvFilter);
      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        if (!file.getName().endsWith(CSV)) {
          file = new File(file.getAbsolutePath() + CSV);
        }
        (new CopiarPropriedades(file, uniqueSelectedIds)).execute();
      }

    } else if (e.getSource() == menu.exportarMarcados) {
    	ArrayList<ItemId> uniqueSelectedIds = new ArrayList<ItemId>();
        for(IPEDSource source : App.get().appCase.getAtomicSources()){
        	for (int id = 0; id <= source.getLastId(); id++) {
                if (source.getMarcadores().isSelected(id)) {
                  uniqueSelectedIds.add(new ItemId(source.getSourceId(), id));
                }
              }
        }
      fileChooser.setFileFilter(defaultFilter);
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
        File dir = fileChooser.getSelectedFile();
        (new CopiarArquivos(dir, uniqueSelectedIds)).execute();
      }

    } else if (e.getSource() == menu.exportCheckedToZip) {
        ArrayList<ItemId> uniqueSelectedIds = new ArrayList<ItemId>();
        for(IPEDSource source : App.get().appCase.getAtomicSources()){
        	for (int id = 0; id <= source.getLastId(); id++) {
        		if (source.getMarcadores().isSelected(id)) {
                    uniqueSelectedIds.add(new ItemId(source.getSourceId(), id));
                }
              }
        }
        
        fileChooser.setFileFilter(defaultFilter);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
          File file = fileChooser.getSelectedFile();
          (new ExportFilesToZip(file, uniqueSelectedIds)).execute();
        }

      } else if (e.getSource() == menu.importarPalavras) {
      fileChooser.setFileFilter(defaultFilter);
      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        new KeywordListImporter(file).execute();
      }

    } else if (e.getSource() == menu.limparBuscas) {
      App.get().appCase.getMultiMarcadores().clearTypedWords();
      App.get().appCase.getMultiMarcadores().saveState();
      MarcadoresController.get().atualizarGUIHistory();

    } else if (e.getSource() == menu.carregarMarcadores) {
    	MarcadoresController.get().askAndLoadState();

    } else if (e.getSource() == menu.salvarMarcadores) {
    	MarcadoresController.get().askAndSaveState();

    } else if (e.getSource() == menu.copiarPreview) {
      Viewer viewer = App.get().getParams().compositeViewer.getCurrentViewer();
      viewer.copyScreen();

    } else if (e.getSource() == menu.aumentarGaleria) {

      int MAX_GALLERY_COLS = 40;

      JDialog dialog = new JDialog();
      dialog.setModal(true);
      dialog.setTitle("Galeria");
      dialog.setBounds(0, 0, 180, 140);
      SpinnerNumberModel model = new SpinnerNumberModel(App.get().galleryModel.colCount, 1, MAX_GALLERY_COLS, 1);
      model.setValue(App.get().galleryModel.colCount);

      JLabel msg = new JLabel("Colunas:");
      JSpinner spinner = new JSpinner(model);
      JButton button = new JButton("OK");

      msg.setBounds(20, 15, 50, 20);
      spinner.setBounds(80, 10, 50, 30);
      button.setBounds(80, 50, 50, 30);

      dialog.getContentPane().add(msg);
      dialog.getContentPane().add(spinner);
      dialog.getContentPane().add(button);
      dialog.getContentPane().add(new JLabel());

      spinner.addChangeListener(new SpinnerListener(model, dialog));
      button.addActionListener(new SpinnerListener(model, dialog));

      dialog.setLocationRelativeTo(App.get());
      dialog.setVisible(true);

    } else if (e.getSource() == menu.gerenciarMarcadores) {

      GerenciadorMarcadores.setVisible();

    } else if (e.getSource() == menu.gerenciarColunas) {

      ColumnsManager.getInstance().setVisible();

    } else if (e.getSource() == menu.gerenciarFiltros) {

      App.get().filterManager.setVisible(true);

    } else if (e.getSource() == menu.navigateToParent) {

      int selIdx = App.get().resultsTable.getSelectedRow();
      if (selIdx != -1) {
    	ItemId item = App.get().ipedResult.getItem(App.get().resultsTable.convertRowIndexToModel(selIdx)); 
        int docId = App.get().appCase.getLuceneId(item); 
        App.get().treeListener.navigateToParent(docId);
      }

    } else if (e.getSource() == menu.exportTerms) {
      new ExportIndexedTerms(App.get().appCase.getReader()).export();
    }

  }

  static class SpinnerListener implements ChangeListener, ActionListener {

    private SpinnerNumberModel model;
    private Dialog dialog;

    public SpinnerListener(SpinnerNumberModel model, Dialog dialog) {
      this.model = model;
      this.dialog = dialog;
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
      App.get().galleryModel.colCount = model.getNumber().intValue();
      int colWidth = (int) App.get().gallery.getWidth() / App.get().galleryModel.colCount;
      App.get().gallery.setRowHeight(colWidth);
      int selRow = App.get().resultsTable.getSelectedRow();
      App.get().galleryModel.fireTableStructureChanged();
      if (selRow >= 0) {
        int galleryRow = selRow / App.get().galleryModel.colCount;
        int galleyCol = selRow % App.get().galleryModel.colCount;
        App.get().gallery.getSelectionModel().setSelectionInterval(galleryRow, galleryRow);
        App.get().gallery.getColumnModel().getSelectionModel().setSelectionInterval(galleyCol, galleyCol);
      }

    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
      dialog.dispose();
    }

  }

}
