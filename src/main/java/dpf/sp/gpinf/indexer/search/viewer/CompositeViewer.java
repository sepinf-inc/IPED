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
package dpf.sp.gpinf.indexer.search.viewer;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.lucene.document.Document;
import org.apache.tika.Tika;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class CompositeViewer extends JPanel implements ChangeListener, ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2751185904521769139L;
	private final static String VIEWER_CAPTION = "<html>&nbsp;O visualizador pode conter erros. Clique 2 vezes sobre o arquivo para abri-lo.</html>";
	private static String PREV_HIT_TIP = "Ocorrência anterior";
	private static String NEXT_HIT_TIP = "Próxima ocorrência";
	private static String PREVIEW_TAB_TITLE = "Pré-visualização";

	ArrayList<AbstractViewer> viewerList = new ArrayList<AbstractViewer>();
	JPanel cardViewer;

	AbstractViewer viewerToUse;
	volatile int currentTab;
	AbstractViewer textViewer, hexViewer;
	volatile StreamSource file, lastFile, viewFile;
	volatile String contentType, viewMediaType;
	volatile Document doc;
	Set<String> highlightTerms;

	JTabbedPane tabbedPane;
	JCheckBox fixViewer;
	JButton prevHit, nextHit;
	
	Tika tika = new Tika();

	public CompositeViewer() {
		super(new BorderLayout());

		tabbedPane = new JTabbedPane();
		tabbedPane.addChangeListener(this);
		JLabel viewerLabel = new JLabel(VIEWER_CAPTION);
		fixViewer = new JCheckBox("Fixar Aba");
		prevHit = new JButton("<");
		prevHit.setToolTipText(PREV_HIT_TIP);
		nextHit = new JButton(">");
		nextHit.setToolTipText(NEXT_HIT_TIP);
		prevHit.addActionListener(this);
		nextHit.addActionListener(this);
		JPanel navHit = new JPanel(new GridLayout());
		navHit.add(prevHit);
		navHit.add(nextHit);

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(navHit, BorderLayout.WEST);
		topPanel.add(viewerLabel, BorderLayout.CENTER);
		topPanel.add(fixViewer, BorderLayout.EAST);

		this.add(topPanel, BorderLayout.NORTH);
		this.add(tabbedPane, BorderLayout.CENTER);

	}

	public void addViewer(AbstractViewer viewer) {
		viewerList.add(viewer);

		if (viewer instanceof HexViewer) {
			hexViewer = viewer;
			tabbedPane.addTab(viewer.getName(), viewer.getPanel());
			
		}else if (viewer instanceof TextViewer) {
			textViewer = viewer;
			tabbedPane.addTab(viewer.getName(), viewer.getPanel());
			
		}else {
			if (cardViewer == null) {
				cardViewer = new JPanel(new CardLayout());
				tabbedPane.addTab(PREVIEW_TAB_TITLE, cardViewer);
			}
			cardViewer.add(viewer.getPanel(), viewer.getName());
		}

	}

	public void initViewers() {
		for (AbstractViewer viewer : viewerList)
			viewer.init();
	}

	public void clear() {
		for (AbstractViewer viewer : viewerList)
			viewer.loadFile(null);
		file = null;
	}

	public void dispose() {
		for (AbstractViewer viewer : viewerList)
			viewer.dispose();
	}

	public void loadFile(StreamSource file, String contentType, Set<String> highlightTerms) {
		loadFile(file, file, contentType, highlightTerms);
	}
	
	private void getViewType(){
		if(viewFile != file)
			try {
				viewMediaType = tika.detect(viewFile.getFile());
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		else
			viewMediaType = contentType;
	}
	
	public void loadFile(StreamSource file, StreamSource viewFile, String contentType, Set<String> highlightTerms) {
		this.file = file;
		this.viewFile = viewFile;
		this.contentType = contentType;
		this.doc = doc;
		this.highlightTerms = highlightTerms;
		
		getViewType();

		viewerToUse = null;
		for (AbstractViewer viewer : viewerList) {
			if (viewer.isSupportedType(viewMediaType)) {
				viewerToUse = viewer;
			} else
				viewer.loadFile(null);
		}

		loadFile();

		if (!fixViewer.isSelected())
			changeTab(viewerToUse);

		if(viewFile != file && viewerToUse == textViewer)
			textViewer.loadFile(viewFile, viewMediaType, highlightTerms);
		
		else
			textViewer.loadFile(file, contentType, highlightTerms);
		
		hexViewer.loadFile(file, highlightTerms);
	}

	public AbstractViewer getCurrentViewer() {
		return getViewerAtTab(currentTab);
	}
	
	private AbstractViewer getViewerAtTab(int tab){
		String tabName = tabbedPane.getTitleAt(tab);
		for(AbstractViewer viewer : viewerList)
			if(viewer.getName().equals(tabName))
				return viewer;
		
		//nao encontrado, retorna viewer do cardViewer
		return viewerToUse;
		
	}

	private void loadFile() {
		if (viewFile != lastFile && viewerToUse == getCurrentViewer() && viewerToUse.isSupportedType(viewMediaType)) {

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					CardLayout layout = (CardLayout) cardViewer.getLayout();
					layout.show(cardViewer, viewerToUse.getName());
				}
			});

			viewerToUse.loadFile(viewFile, viewMediaType, highlightTerms);
			lastFile = viewFile;
		}

	}

	@Override
	public void stateChanged(ChangeEvent arg0) {
		currentTab = tabbedPane.getSelectedIndex();
		loadFile();
	}

	private void changeTab(final AbstractViewer viewer) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				boolean viewerFound = false;
				for(int i = 0; i < tabbedPane.getTabCount(); i++)
					if(tabbedPane.getTitleAt(i).equals(viewer.getName())){
						tabbedPane.setSelectedIndex(i);
						viewerFound = true;
						break;
					}
				if(!viewerFound)
					tabbedPane.setSelectedComponent(cardViewer);
			}
		});
	}

	public void setSelectedIndex(int index) {
		tabbedPane.setSelectedIndex(index);
	}

	@Override
	public void actionPerformed(ActionEvent evt) {

		AbstractViewer viewer = getCurrentViewer();

		if (evt.getSource() == prevHit) {
			viewer.scrollToNextHit(false);

		} else if (evt.getSource() == nextHit) {
			viewer.scrollToNextHit(true);
		}

	}

}
