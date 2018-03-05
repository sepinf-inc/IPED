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

import java.awt.event.ActionListener;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class MenuClass extends JPopupMenu {

  private static final long serialVersionUID = 1L;

  JMenuItem exportarSelecionados, copiarSelecionados, marcarSelecionados, desmarcarSelecionados, lerSelecionados, deslerSelecionados, exportarMarcados, copiarMarcados, salvarMarcadores,
      carregarMarcadores, aumentarGaleria, diminuirGaleria, layoutPadrao, disposicao, copiarPreview, gerenciarMarcadores, limparBuscas, importarPalavras, navigateToParent, exportTerms,
      gerenciarFiltros, gerenciarColunas, exportCheckedToZip, exportCheckedTreeToZip, exportTree, exportTreeChecked, similarDocs, openViewfile;

  // JCheckBoxMenuItem changeViewerTab;
  public MenuClass() {
    super();

    ActionListener menuListener = new MenuListener(this);

    marcarSelecionados = new JMenuItem(Messages.getString("MenuClass.CheckHighlighted")); //$NON-NLS-1$
    marcarSelecionados.addActionListener(menuListener);
    this.add(marcarSelecionados);

    desmarcarSelecionados = new JMenuItem(Messages.getString("MenuClass.UnCheckHighlighted")); //$NON-NLS-1$
    desmarcarSelecionados.addActionListener(menuListener);
    this.add(desmarcarSelecionados);

    /*lerSelecionados = new JMenuItem("Marcar selecionados como lido");
     lerSelecionados.addActionListener(menuListener);
     this.add(lerSelecionados);

     deslerSelecionados = new JMenuItem("Marcar selecionados como novo");
     deslerSelecionados.addActionListener(menuListener);
     this.add(deslerSelecionados);
     */
    //this.addSeparator();
    carregarMarcadores = new JMenuItem(Messages.getString("MenuClass.LoadBookmarks")); //$NON-NLS-1$
    carregarMarcadores.addActionListener(menuListener);
    this.add(carregarMarcadores);

    salvarMarcadores = new JMenuItem(Messages.getString("MenuClass.SaveBookmarks")); //$NON-NLS-1$
    salvarMarcadores.addActionListener(menuListener);
    this.add(salvarMarcadores);

    gerenciarMarcadores = new JMenuItem(Messages.getString("MenuClass.ManageBookmarks")); //$NON-NLS-1$
    gerenciarMarcadores.addActionListener(menuListener);
    this.add(gerenciarMarcadores);

    gerenciarFiltros = new JMenuItem(Messages.getString("MenuClass.ManageFilters")); //$NON-NLS-1$
    gerenciarFiltros.addActionListener(menuListener);
    this.add(gerenciarFiltros);

    gerenciarColunas = new JMenuItem(Messages.getString("MenuClass.ManageColumns")); //$NON-NLS-1$
    gerenciarColunas.addActionListener(menuListener);
    this.add(gerenciarColunas);

    this.addSeparator();
    
    JMenu submenu = new JMenu(Messages.getString("MenuClass.ExportItens")); //$NON-NLS-1$
    this.add(submenu);
    
    exportarSelecionados = new JMenuItem(Messages.getString("MenuClass.ExportHighlighted")); //$NON-NLS-1$
    exportarSelecionados.addActionListener(menuListener);
    submenu.add(exportarSelecionados);

    exportarMarcados = new JMenuItem(Messages.getString("MenuClass.ExportChecked")); //$NON-NLS-1$
    exportarMarcados.addActionListener(menuListener);
    submenu.add(exportarMarcados);

    exportTree = new JMenuItem(Messages.getString("MenuClass.ExportTree")); //$NON-NLS-1$
    exportTree.addActionListener(menuListener);
    submenu.add(exportTree);

    exportTreeChecked = new JMenuItem(Messages.getString("MenuClass.ExportTree.Checked")); //$NON-NLS-1$
    exportTreeChecked.addActionListener(menuListener);
    submenu.add(exportTreeChecked);
    
    exportCheckedToZip = new JMenuItem(Messages.getString("MenuClass.ExportCheckedToZip")); //$NON-NLS-1$
    exportCheckedToZip.addActionListener(menuListener);
    submenu.add(exportCheckedToZip);
    
    exportCheckedTreeToZip = new JMenuItem(Messages.getString("MenuClass.ExportTreeToZip.Checked")); //$NON-NLS-1$
    exportCheckedTreeToZip.addActionListener(menuListener);
    submenu.add(exportCheckedTreeToZip);

    this.addSeparator();

    copiarSelecionados = new JMenuItem(Messages.getString("MenuClass.ExportProps.Highlighed")); //$NON-NLS-1$
    copiarSelecionados.addActionListener(menuListener);
    this.add(copiarSelecionados);

    copiarMarcados = new JMenuItem(Messages.getString("MenuClass.ExportProps.Checked")); //$NON-NLS-1$
    copiarMarcados.addActionListener(menuListener);
    this.add(copiarMarcados);

    this.addSeparator();

    importarPalavras = new JMenuItem(Messages.getString("MenuClass.ImportKeywords")); //$NON-NLS-1$
    importarPalavras.addActionListener(menuListener);
    this.add(importarPalavras);

    limparBuscas = new JMenuItem(Messages.getString("MenuClass.ClearSearches")); //$NON-NLS-1$
    limparBuscas.addActionListener(menuListener);
    this.add(limparBuscas);

    exportTerms = new JMenuItem(Messages.getString("MenuClass.ExportIndexedWords")); //$NON-NLS-1$
    exportTerms.addActionListener(menuListener);
    this.add(exportTerms);

    this.addSeparator();

    layoutPadrao = new JMenuItem(Messages.getString("MenuClass.ResetLayout")); //$NON-NLS-1$
    layoutPadrao.addActionListener(menuListener);
    this.add(layoutPadrao);
    
    disposicao = new JMenuItem(Messages.getString("MenuClass.ChangeLayout")); //$NON-NLS-1$
    disposicao.addActionListener(menuListener);
    this.add(disposicao);

    copiarPreview = new JMenuItem(Messages.getString("MenuClass.CopyViewerImage")); //$NON-NLS-1$
    copiarPreview.addActionListener(menuListener);
    this.add(copiarPreview);

    aumentarGaleria = new JMenuItem(Messages.getString("MenuClass.ChangeGalleryColCount")); //$NON-NLS-1$
    aumentarGaleria.addActionListener(menuListener);
    this.add(aumentarGaleria);

    if (!App.get().appCase.isFTKReport()) {
      navigateToParent = new JMenuItem(Messages.getString("MenuClass.GoToParent")); //$NON-NLS-1$
      navigateToParent.addActionListener(menuListener);
      this.add(navigateToParent);
    }
    
    similarDocs = new JMenuItem(Messages.getString("MenuClass.FindSimilarDocs")); //$NON-NLS-1$
    similarDocs.addActionListener(menuListener);
    this.add(similarDocs); 
    
    openViewfile = new JMenuItem(Messages.getString("MenuClass.OpenViewFile")); //$NON-NLS-1$
    openViewfile.addActionListener(menuListener);
    this.add(openViewfile); 

  }

}
