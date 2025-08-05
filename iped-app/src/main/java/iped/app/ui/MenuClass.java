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
package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

import iped.app.ui.themes.Theme;
import iped.app.ui.themes.ThemeManager;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.IndexTaskConfig;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.task.similarity.ImageSimilarityTask;
import iped.parsers.vcard.VCardParser;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;

public class MenuClass extends JPopupMenu {

    private static final long serialVersionUID = 1L;

    JMenuItem exportHighlighted, copyHighlighted, checkHighlighted, uncheckHighlighted, readHighlighted, unreadHighlighted, exportChecked, copyChecked, saveBookmarks, loadBookmarks,
            checkHighlightedAndSubItems, uncheckHighlightedAndSubItems, checkHighlightedAndParent, uncheckHighlightedAndParent, checkHighlightedAndReferences, uncheckHighlightedAndReferences, checkHighlightedAndReferencedBy, uncheckHighlightedAndReferencedBy,
            changeGalleryColCount, defaultLayout, changeLayout, previewScreenshot, manageBookmarks, clearSearchHistory, importKeywords, navigateToParent, exportTerms, manageFilters, manageColumns, exportCheckedToZip, exportCheckedTreeToZip,
            exportTree, exportTreeChecked, similarDocs, openViewfile, createReport, resetColLayout, lastColLayout, saveColLayout, addToGraph, navigateToParentChat, pinFirstColumns, similarImagesCurrent, similarImagesExternal,
            similarFacesCurrent, similarFacesExternal, toggleTimelineView, uiZoom, catIconSize, savePanelsLayout, loadPanelsLayout;

    MenuListener menuListener = new MenuListener(this);
    boolean isTreeMenu;

    private boolean similarFacesExternalEnabled = SimilarFacesFilterActions.isExternalSearchEnabled();

    public MenuClass(boolean isTreeMenu) {
        super();
        this.isTreeMenu = isTreeMenu;
        addExportTreeMenuItems(this);
    }

    public MenuClass(IItem item) {
        super();

        checkHighlighted = new JMenuItem(Messages.getString("MenuClass.CheckHighlighted")); //$NON-NLS-1$
        checkHighlighted.addActionListener(menuListener);
        checkHighlighted.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        this.add(checkHighlighted);

        uncheckHighlighted = new JMenuItem(Messages.getString("MenuClass.UnCheckHighlighted")); //$NON-NLS-1$
        uncheckHighlighted.addActionListener(menuListener);
        uncheckHighlighted.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        this.add(uncheckHighlighted);

        JMenu submenu = new JMenu(Messages.getString("MenuClass.CheckAdvanced")); //$NON-NLS-1$
        this.add(submenu);

        checkHighlightedAndSubItems = new JMenuItem(Messages.getString("MenuClass.CheckHighlightedAndSubItems")); //$NON-NLS-1$
        checkHighlightedAndSubItems.addActionListener(menuListener);
        checkHighlightedAndSubItems.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
        submenu.add(checkHighlightedAndSubItems);

        uncheckHighlightedAndSubItems = new JMenuItem(Messages.getString("MenuClass.UncheckHighlightedAndSubItems")); //$NON-NLS-1$
        uncheckHighlightedAndSubItems.addActionListener(menuListener);
        uncheckHighlightedAndSubItems.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.ALT_MASK));
        submenu.add(uncheckHighlightedAndSubItems);

        submenu.addSeparator();

        checkHighlightedAndParent = new JMenuItem(Messages.getString("MenuClass.CheckHighlightedAndParent")); //$NON-NLS-1$
        checkHighlightedAndParent.addActionListener(menuListener);
        checkHighlightedAndParent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));
        submenu.add(checkHighlightedAndParent);

        uncheckHighlightedAndParent = new JMenuItem(Messages.getString("MenuClass.UncheckHighlightedAndParent")); //$NON-NLS-1$
        uncheckHighlightedAndParent.addActionListener(menuListener);
        uncheckHighlightedAndParent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.ALT_MASK));
        submenu.add(uncheckHighlightedAndParent);

        submenu.addSeparator();

        checkHighlightedAndReferences = new JMenuItem(Messages.getString("MenuClass.CheckHighlightedAndReferences")); //$NON-NLS-1$
        checkHighlightedAndReferences.addActionListener(menuListener);
        checkHighlightedAndReferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK));
        submenu.add(checkHighlightedAndReferences);

        uncheckHighlightedAndReferences = new JMenuItem(Messages.getString("MenuClass.UncheckHighlightedAndReferences")); //$NON-NLS-1$
        uncheckHighlightedAndReferences.addActionListener(menuListener);
        uncheckHighlightedAndReferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.ALT_MASK));
        submenu.add(uncheckHighlightedAndReferences);

        submenu.addSeparator();

        checkHighlightedAndReferencedBy = new JMenuItem(Messages.getString("MenuClass.CheckHighlightedAndReferencedBy")); //$NON-NLS-1$
        checkHighlightedAndReferencedBy.addActionListener(menuListener);
        checkHighlightedAndReferencedBy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, ActionEvent.CTRL_MASK));
        submenu.add(checkHighlightedAndReferencedBy);

        uncheckHighlightedAndReferencedBy = new JMenuItem(Messages.getString("MenuClass.UncheckHighlightedAndReferencedBy")); //$NON-NLS-1$
        uncheckHighlightedAndReferencedBy.addActionListener(menuListener);
        uncheckHighlightedAndReferencedBy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, ActionEvent.ALT_MASK));
        submenu.add(uncheckHighlightedAndReferencedBy);

        this.addSeparator();

        loadBookmarks = new JMenuItem(Messages.getString("MenuClass.LoadBookmarks")); //$NON-NLS-1$
        loadBookmarks.addActionListener(menuListener);
        this.add(loadBookmarks);

        saveBookmarks = new JMenuItem(Messages.getString("MenuClass.SaveBookmarks")); //$NON-NLS-1$
        saveBookmarks.addActionListener(menuListener);
        this.add(saveBookmarks);

        manageBookmarks = new JMenuItem(Messages.getString("MenuClass.ManageBookmarks")); //$NON-NLS-1$
        manageBookmarks.addActionListener(menuListener);
        manageBookmarks.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, ActionEvent.CTRL_MASK));
        this.add(manageBookmarks);

        manageFilters = new JMenuItem(Messages.getString("MenuClass.ManageFilters")); //$NON-NLS-1$
        manageFilters.addActionListener(menuListener);
        this.add(manageFilters);

        submenu = new JMenu(Messages.getString("MenuClass.ManageColumns")); //$NON-NLS-1$
        this.add(submenu);

        manageColumns = new JMenuItem(Messages.getString("MenuClass.ManageVisibleCols")); //$NON-NLS-1$
        manageColumns.addActionListener(menuListener);
        submenu.add(manageColumns);

        pinFirstColumns = new JMenuItem(Messages.getString("MenuClass.PinFirstCols")); //$NON-NLS-1$
        pinFirstColumns.addActionListener(menuListener);
        submenu.add(pinFirstColumns);

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

        exportHighlighted = new JMenuItem(Messages.getString("MenuClass.ExportHighlighted")); //$NON-NLS-1$
        exportHighlighted.addActionListener(menuListener);
        submenu.add(exportHighlighted);

        exportChecked = new JMenuItem(Messages.getString("MenuClass.ExportChecked")); //$NON-NLS-1$
        exportChecked.addActionListener(menuListener);
        submenu.add(exportChecked);

        exportCheckedToZip = new JMenuItem(Messages.getString("MenuClass.ExportCheckedToZip")); //$NON-NLS-1$
        exportCheckedToZip.addActionListener(menuListener);
        submenu.add(exportCheckedToZip);

        addExportTreeMenuItems(submenu);

        this.addSeparator();

        copyHighlighted = new JMenuItem(Messages.getString("MenuClass.ExportProps.Highlighed")); //$NON-NLS-1$
        copyHighlighted.addActionListener(menuListener);
        this.add(copyHighlighted);

        copyChecked = new JMenuItem(Messages.getString("MenuClass.ExportProps.Checked")); //$NON-NLS-1$
        copyChecked.addActionListener(menuListener);
        this.add(copyChecked);

        this.addSeparator();

        importKeywords = new JMenuItem(Messages.getString("MenuClass.ImportKeywords")); //$NON-NLS-1$
        importKeywords.addActionListener(menuListener);
        this.add(importKeywords);

        clearSearchHistory = new JMenuItem(Messages.getString("MenuClass.ClearSearches")); //$NON-NLS-1$
        clearSearchHistory.addActionListener(menuListener);
        this.add(clearSearchHistory);

        exportTerms = new JMenuItem(Messages.getString("MenuClass.ExportIndexedWords")); //$NON-NLS-1$
        exportTerms.addActionListener(menuListener);
        this.add(exportTerms);

        this.addSeparator();

        toggleTimelineView = new JMenuItem(Messages.getString("App.ToggleTimelineView")); //$NON-NLS-1$
        toggleTimelineView.addActionListener(menuListener);
        this.add(toggleTimelineView);

        JMenu layoutAppearance = new JMenu(Messages.getString("MenuClass.LayoutAppearance")); //$NON-NLS-1$
        this.add(layoutAppearance);

        defaultLayout = new JMenuItem(Messages.getString("MenuClass.ResetLayout")); //$NON-NLS-1$
        defaultLayout.addActionListener(menuListener);
        layoutAppearance.add(defaultLayout);

        savePanelsLayout = new JMenuItem(Messages.getString("MenuClass.SavePanelsLayout")); //$NON-NLS-1$
        savePanelsLayout.addActionListener(menuListener);
        layoutAppearance.add(savePanelsLayout);

        loadPanelsLayout = new JMenuItem(Messages.getString("MenuClass.LoadPanelsLayout")); //$NON-NLS-1$
        loadPanelsLayout.addActionListener(menuListener);
        layoutAppearance.add(loadPanelsLayout);

        changeLayout = new JMenuItem(Messages.getString("MenuClass.ChangeLayout")); //$NON-NLS-1$
        changeLayout.addActionListener(menuListener);
        layoutAppearance.add(changeLayout);

        changeGalleryColCount = new JMenuItem(Messages.getString("MenuClass.ChangeGalleryColCount")); //$NON-NLS-1$
        changeGalleryColCount.addActionListener(menuListener);
        layoutAppearance.add(changeGalleryColCount);

        List<Theme> themes = ThemeManager.getInstance().getThemes();
        if (themes.size() > 1) {
            submenu = new JMenu(Messages.getString("MenuClass.ColorTheme")); //$NON-NLS-1$
            layoutAppearance.add(submenu);
            for (Theme theme : themes) {
                JRadioButtonMenuItem themeItem = new JRadioButtonMenuItem(theme.getName(), theme.equals(ThemeManager.getInstance().getCurrentTheme()));
                submenu.add(themeItem);
                themeItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        ThemeManager.getInstance().setTheme(theme);
                    }
                });
            }
        }

        uiZoom = new JMenuItem(Messages.getString("MenuClass.UiZoom")); //$NON-NLS-1$
        uiZoom.addActionListener(menuListener);
        layoutAppearance.add(uiZoom);

        catIconSize = new JMenuItem(Messages.getString("MenuClass.IconSize")); //$NON-NLS-1$
        catIconSize.addActionListener(menuListener);
        layoutAppearance.add(catIconSize);

        previewScreenshot = new JMenuItem(Messages.getString("MenuClass.CopyViewerImage")); //$NON-NLS-1$
        previewScreenshot.addActionListener(menuListener);
        this.add(previewScreenshot);

        this.addSeparator();

        navigateToParent = new JMenuItem(Messages.getString("MenuClass.GoToParent")); //$NON-NLS-1$
        navigateToParent.addActionListener(menuListener);
        this.add(navigateToParent);

        navigateToParentChat = new JMenuItem(Messages.getString("MenuClass.GoToChat")); //$NON-NLS-1$
        navigateToParentChat.addActionListener(menuListener);
        boolean enableGoToChat = false;
        if (item != null) {
            enableGoToChat = MediaTypes.isInstanceOf(item.getMediaType(), MediaTypes.CHAT_MESSAGE_MIME)
                    || MediaTypes.UFED_MESSAGE_MIME.equals(item.getMediaType())
                    || (VCardParser.VCARD_MIME.equals(item.getMediaType()) && item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM) != null && item.getMetadata().get(ExtraProperties.COMMUNICATION_TO) != null);
        }
        navigateToParentChat.setEnabled(enableGoToChat);
        this.add(navigateToParentChat);

        this.addSeparator();

        similarDocs = new JMenuItem(Messages.getString("MenuClass.FindSimilarDocs")); //$NON-NLS-1$
        similarDocs.addActionListener(menuListener);
        IndexTaskConfig indexConfig = ConfigurationManager.get().findObject(IndexTaskConfig.class);
        similarDocs.setEnabled(indexConfig.isStoreTermVectors());
        this.add(similarDocs);

        submenu = new JMenu(Messages.getString("MenuClass.FindSimilarImages")); //$NON-NLS-1$
        submenu.setEnabled(SimilarImagesFilterActions.isFeatureEnabled());
        this.add(submenu);

        similarImagesCurrent = new JMenuItem(Messages.getString("MenuClass.FindSimilarImages.Current")); //$NON-NLS-1$
        similarImagesCurrent.addActionListener(menuListener);
        similarImagesCurrent.setEnabled(item != null && item.getExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES) != null);
        submenu.add(similarImagesCurrent);

        similarImagesExternal = new JMenuItem(Messages.getString("MenuClass.FindSimilarImages.External")); //$NON-NLS-1$
        similarImagesExternal.addActionListener(menuListener);
        similarImagesExternal.setEnabled(submenu.isEnabled());
        submenu.add(similarImagesExternal);

        submenu = new JMenu(Messages.getString("MenuClass.FindSimilarFaces")); //$NON-NLS-1$
        submenu.setEnabled(SimilarFacesFilterActions.isFeatureEnabled());
        this.add(submenu);

        similarFacesCurrent = new JMenuItem(Messages.getString("MenuClass.FindSimilarFaces.Current")); //$NON-NLS-1$
        similarFacesCurrent.addActionListener(menuListener);
        similarFacesCurrent.setEnabled(item != null && item.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES) != null);
        submenu.add(similarFacesCurrent);

        similarFacesExternal = new JMenuItem(Messages.getString("MenuClass.FindSimilarFaces.External")); //$NON-NLS-1$
        similarFacesExternal.addActionListener(menuListener);
        similarFacesExternal.setEnabled(submenu.isEnabled() && similarFacesExternalEnabled);
        submenu.add(similarFacesExternal);

        this.addSeparator();

        openViewfile = new JMenuItem(Messages.getString("MenuClass.OpenViewFile")); //$NON-NLS-1$
        openViewfile.addActionListener(menuListener);
        openViewfile.setEnabled(item != null && item.getViewFile() != null);
        this.add(openViewfile);

        this.addSeparator();
        addToGraph = new JMenuItem(Messages.getString("MenuClass.AddToGraph")); //$NON-NLS-1$
        addToGraph.setEnabled(App.get().appGraphAnalytics.isEnabled() && item != null && item.getMetadata().get(ExtraProperties.COMMUNICATION_FROM) != null && item.getMetadata().get(ExtraProperties.COMMUNICATION_TO) != null);
        addToGraph.addActionListener(menuListener);
        this.add(addToGraph);

        this.addSeparator();

        createReport = new JMenuItem(Messages.getString("MenuClass.GenerateReport")); //$NON-NLS-1$
        createReport.addActionListener(menuListener);
        this.add(createReport);

    }

    public void addExportTreeMenuItems(JComponent menu) {
        exportTree = new JMenuItem(Messages.getString("MenuClass.ExportTree")); //$NON-NLS-1$
        exportTree.addActionListener(menuListener);
        menu.add(exportTree);

        exportTreeChecked = new JMenuItem(Messages.getString("MenuClass.ExportTree.Checked")); //$NON-NLS-1$
        exportTreeChecked.addActionListener(menuListener);
        menu.add(exportTreeChecked);

        exportCheckedTreeToZip = new JMenuItem(Messages.getString("MenuClass.ExportTreeToZip.Checked")); //$NON-NLS-1$
        exportCheckedTreeToZip.addActionListener(menuListener);
        menu.add(exportCheckedTreeToZip);
    }

}