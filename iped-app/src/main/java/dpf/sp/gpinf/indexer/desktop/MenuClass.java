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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

import org.apache.tika.metadata.Metadata;

import br.gov.pf.labld.graph.desktop.AppGraphAnalytics;
import dpf.mg.udi.gpinf.vcardparser.VCardParser;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.process.task.ImageSimilarityTask;
import iped3.IItem;
import iped3.util.MediaTypes;

public class MenuClass extends JPopupMenu {

    private static final long serialVersionUID = 1L;

    JMenuItem exportarSelecionados, copiarSelecionados, marcarSelecionados, desmarcarSelecionados, 
    		marcarRecursivamenteSelecionados, desmarcarRecursivamenteSelecionados, 
    		lerSelecionados, deslerSelecionados, exportarMarcados, copiarMarcados, salvarMarcadores, carregarMarcadores, aumentarGaleria,
            diminuirGaleria, layoutPadrao, disposicao, copiarPreview, gerenciarMarcadores, limparBuscas,
            importarPalavras, navigateToParent, exportTerms, gerenciarFiltros, gerenciarColunas, exportCheckedToZip,
            exportCheckedTreeToZip, exportTree, exportTreeChecked, similarDocs, similarImagesCurrent, similarImagesExternal, openViewfile, createReport,
            resetColLayout, lastColLayout, saveColLayout, addToGraph, navigateToParentChat;

    MenuListener menuListener;

    public MenuClass() {
        this(null);
    }
    
    public MenuClass(IItem item) {
        super();

        menuListener = new MenuListener(this);

        marcarSelecionados = new JMenuItem(Messages.getString("MenuClass.CheckHighlighted")); //$NON-NLS-1$
        marcarSelecionados.addActionListener(menuListener);
        marcarSelecionados.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        this.add(marcarSelecionados);

		
		desmarcarSelecionados = new JMenuItem(Messages.getString("MenuClass.UnCheckHighlighted")); //$NON-NLS-1$
		desmarcarSelecionados.addActionListener(menuListener);
		desmarcarSelecionados.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
 		this.add(desmarcarSelecionados);
		 
        marcarRecursivamenteSelecionados = new JMenuItem(Messages.getString("MenuClass.CheckRecursivelyHighlighted")); //$NON-NLS-1$
        marcarRecursivamenteSelecionados.addActionListener(menuListener);
        marcarRecursivamenteSelecionados.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
        this.add(marcarRecursivamenteSelecionados);

		
	    desmarcarRecursivamenteSelecionados = new	  JMenuItem(Messages.getString("MenuClass.UnCheckRecursivelyHighlighted"));	//$NON-NLS-1$
		desmarcarRecursivamenteSelecionados.addActionListener(menuListener);
		desmarcarRecursivamenteSelecionados.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.ALT_MASK));
		this.add(desmarcarRecursivamenteSelecionados);
		         
		  /*
         * lerSelecionados = new JMenuItem("Marcar selecionados como lido");
         * lerSelecionados.addActionListener(menuListener); this.add(lerSelecionados);
         * 
         * deslerSelecionados = new JMenuItem("Marcar selecionados como novo");
         * deslerSelecionados.addActionListener(menuListener);
         * this.add(deslerSelecionados);
         */
        // this.addSeparator();
        carregarMarcadores = new JMenuItem(Messages.getString("MenuClass.LoadBookmarks")); //$NON-NLS-1$
        carregarMarcadores.addActionListener(menuListener);
        this.add(carregarMarcadores);

        salvarMarcadores = new JMenuItem(Messages.getString("MenuClass.SaveBookmarks")); //$NON-NLS-1$
        salvarMarcadores.addActionListener(menuListener);
        this.add(salvarMarcadores);

        gerenciarMarcadores = new JMenuItem(Messages.getString("MenuClass.ManageBookmarks")); //$NON-NLS-1$
        gerenciarMarcadores.addActionListener(menuListener);
        gerenciarMarcadores.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, ActionEvent.CTRL_MASK));
        this.add(gerenciarMarcadores);

        gerenciarFiltros = new JMenuItem(Messages.getString("MenuClass.ManageFilters")); //$NON-NLS-1$
        gerenciarFiltros.addActionListener(menuListener);
        this.add(gerenciarFiltros);

        JMenu submenu = new JMenu(Messages.getString("MenuClass.ManageColumns")); //$NON-NLS-1$
        this.add(submenu);

        gerenciarColunas = new JMenuItem(Messages.getString("MenuClass.ManageVisibleCols")); //$NON-NLS-1$
        gerenciarColunas.addActionListener(menuListener);
        submenu.add(gerenciarColunas);

        lastColLayout = new JMenuItem(Messages.getString("MenuClass.LoadLastColLayout")); //$NON-NLS-1$
        lastColLayout.addActionListener(menuListener);
        submenu.add(lastColLayout);

        saveColLayout = new JMenuItem(Messages.getString("MenuClass.SaveColLayout")); //$NON-NLS-1$
        saveColLayout.addActionListener(menuListener);
        submenu.add(saveColLayout);

        resetColLayout = new JMenuItem(Messages.getString("MenuClass.ResetColLayout")); //$NON-NLS-1$
        resetColLayout.addActionListener(menuListener);
        submenu.add(resetColLayout);

        this.addSeparator();

        submenu = new JMenu(Messages.getString("MenuClass.ExportItens")); //$NON-NLS-1$
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
        
        this.addSeparator();

        if (!App.get().appCase.isFTKReport()) {
            navigateToParent = new JMenuItem(Messages.getString("MenuClass.GoToParent")); //$NON-NLS-1$
            navigateToParent.addActionListener(menuListener);
            this.add(navigateToParent);
        }
        
        navigateToParentChat = new JMenuItem(Messages.getString("MenuClass.GoToChat")); //$NON-NLS-1$
        navigateToParentChat.addActionListener(menuListener);
        boolean enableGoToChat = false;
        if(item != null) {
            enableGoToChat = MediaTypes.isInstanceOf(item.getMediaType(), MediaTypes.CHAT_MESSAGE_MIME) ||
                    (VCardParser.VCARD_MIME.equals(item.getMediaType()) && item.getMetadata().get(Metadata.MESSAGE_FROM) != null
                    && item.getMetadata().get(Metadata.MESSAGE_TO) != null);
        }
        navigateToParentChat.setEnabled(enableGoToChat);
        this.add(navigateToParentChat);
        
        this.addSeparator();

        similarDocs = new JMenuItem(Messages.getString("MenuClass.FindSimilarDocs")); //$NON-NLS-1$
        similarDocs.addActionListener(menuListener);
        AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                .findObjects(AdvancedIPEDConfig.class).iterator().next();
        similarDocs.setEnabled(advancedConfig.isStoreTermVectors());
        this.add(similarDocs);
        
        submenu = new JMenu(Messages.getString("MenuClass.FindSimilarImages")); //$NON-NLS-1$
        IPEDConfig ipedConfig = (IPEDConfig)ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator().next();
        String enabled = ipedConfig.getApplicationConfiguration().getProperty(ImageSimilarityTask.enableParam);
        if (enabled != null) submenu.setEnabled(Boolean.valueOf(enabled.trim()));
        else submenu.setEnabled(false);
        this.add(submenu);
        
        similarImagesCurrent = new JMenuItem(Messages.getString("MenuClass.FindSimilarImages.Current")); //$NON-NLS-1$
        similarImagesCurrent.addActionListener(menuListener);
        similarImagesCurrent.setEnabled(item != null && item.getImageSimilarityFeatures() != null);
        submenu.add(similarImagesCurrent);        

        similarImagesExternal = new JMenuItem(Messages.getString("MenuClass.FindSimilarImages.External")); //$NON-NLS-1$
        similarImagesExternal.addActionListener(menuListener);
        similarImagesExternal.setEnabled(submenu.isEnabled());
        submenu.add(similarImagesExternal);        
        
        openViewfile = new JMenuItem(Messages.getString("MenuClass.OpenViewFile")); //$NON-NLS-1$
        openViewfile.addActionListener(menuListener);
        openViewfile.setEnabled(item != null && item.getViewFile() != null);
        this.add(openViewfile);
        
        this.addSeparator();
        addToGraph = new JMenuItem(Messages.getString("MenuClass.AddToGraph")); //$NON-NLS-1$
        addToGraph.setEnabled(App.get().appGraphAnalytics.isEnabled());
        addToGraph.addActionListener(menuListener);
        this.add(addToGraph);
        
        this.addSeparator();

        createReport = new JMenuItem(Messages.getString("MenuClass.GenerateReport")); //$NON-NLS-1$
        createReport.addActionListener(menuListener);
        this.add(createReport);

    }

}