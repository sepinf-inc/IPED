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

public class CompositeViewer extends JPanel implements ChangeListener, ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2751185904521769139L;
	private final static String VIEWER_CAPTION = "<html>&nbsp;O visualizador pode conter erros. Clique 2 vezes sobre o arquivo para abri-lo.</html>";
	private static String PREV_HIT_TIP = "Ocorrência anterior";
	private static String NEXT_HIT_TIP = "Próxima ocorrência";

	ArrayList<AbstractViewer> viewerList = new ArrayList<AbstractViewer>();
	JPanel cardViewer;

	AbstractViewer viewerToUse;
	volatile int currentTab;
	TextViewer textViewer;
	volatile File file, lastFile, viewFile;
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

		if (viewer instanceof TextViewer) {
			textViewer = (TextViewer) viewer;
			tabbedPane.addTab(viewer.getName(), viewer.getPanel());
		} else {
			if (cardViewer == null) {
				cardViewer = new JPanel(new CardLayout());
				tabbedPane.addTab("Pré-visualização", cardViewer);
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

	public void loadFile(Document doc, File file, String contentType, Set<String> highlightTerms) {
		loadFile(doc, file, file, contentType, highlightTerms);
	}
	
	private void getViewType(){
		if(viewFile != file)
			try {
				viewMediaType = tika.detect(viewFile);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		else
			viewMediaType = contentType;
	}
	
	public void loadFile(Document doc, File file, File viewFile, String contentType, Set<String> highlightTerms) {
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

		textViewer.loadFile(doc, file, contentType);
	}

	public AbstractViewer getCurrentViewer() {
		if (currentTab == 0)
			return textViewer;
		else
			return viewerToUse;
	}

	private void loadFile() {
		if (file != lastFile && currentTab != 0 && viewerToUse != textViewer && viewerToUse.isSupportedType(viewMediaType)) {

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					CardLayout layout = (CardLayout) cardViewer.getLayout();
					layout.show(cardViewer, viewerToUse.getName());
				}
			});

			viewerToUse.loadFile(viewFile, viewMediaType, highlightTerms);
			lastFile = file;
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
				if (viewer == textViewer)
					tabbedPane.setSelectedIndex(0);
				else {
					tabbedPane.setSelectedIndex(1);
				}
			}
		});
	}

	public void setSelectedIndex(int index) {
		tabbedPane.setSelectedIndex(index);
	}

	@Override
	public void actionPerformed(ActionEvent evt) {

		AbstractViewer viewerToScroll = viewerToUse;
		if (currentTab == 0)
			viewerToScroll = textViewer;

		if (evt.getSource() == prevHit) {
			viewerToScroll.scrollToNextHit(false);

		} else if (evt.getSource() == nextHit) {
			viewerToScroll.scrollToNextHit(true);
		}

	}

}
