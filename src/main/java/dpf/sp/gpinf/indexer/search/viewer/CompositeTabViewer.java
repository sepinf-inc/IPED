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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class CompositeTabViewer extends JPanel implements ChangeListener, ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2751185904521769139L;
	private final static String VIEWER_CAPTION = "<html>&nbsp;O visualizador pode conter erros. Clique 2 vezes sobre o arquivo para abri-lo.</html>";
	private static String PREV_HIT_TIP = "Ocorrência anterior";
	private static String NEXT_HIT_TIP = "Próxima ocorrência";
	private static String FIX_VIEWER = "Fixar Visualizador";

	ArrayList<AbstractViewer> viewerList = new ArrayList<AbstractViewer>();
	HashSet<AbstractViewer> loadedViewers = new HashSet<AbstractViewer>();

	volatile AbstractViewer bestViewer, currentViewer, textViewer;
	volatile StreamSource file, viewFile;
	volatile String contentType, viewMediaType;
	Set<String> highlightTerms;

	JTabbedPane tabbedPane;
	JCheckBox fixViewer;
	JButton prevHit, nextHit;
	
	Tika tika = new Tika();

	public CompositeTabViewer() {
		super(new BorderLayout());

		tabbedPane = new JTabbedPane();
		tabbedPane.addChangeListener(this);
		JLabel viewerLabel = new JLabel(VIEWER_CAPTION);
		fixViewer = new JCheckBox(FIX_VIEWER);
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
		tabbedPane.addTab(viewer.getName(), viewer.getPanel());
		if (viewer instanceof TextViewer)
			textViewer = viewer;

	}

	public void initViewers() {
		for (AbstractViewer viewer : viewerList)
			viewer.init();
	}

	public void clear() {
		for (AbstractViewer viewer : viewerList)
			viewer.loadFile(null);
		file = null;
		viewFile = null;
	}

	public void dispose() {
		for (AbstractViewer viewer : viewerList)
			viewer.dispose();
	}

	public void loadFile(StreamSource file, String contentType, Set<String> highlightTerms) {
		loadFile(file, file, contentType, highlightTerms);
	}
	
	private String getViewType(){
		if(viewFile != file)
			try {
				return tika.detect(viewFile.getFile());
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		return contentType;
	}
	
	public void loadFile(StreamSource file, StreamSource viewFile, String contentType, Set<String> highlightTerms) {
		this.file = file;
		this.viewFile = viewFile;
		this.contentType = contentType;
		if(contentType == null)
			try {
				this.contentType = tika.detect(file.getFile());
			} catch (IOException e) {
				this.contentType = MediaType.OCTET_STREAM.toString();
			}
		this.viewMediaType = getViewType();
		this.highlightTerms = highlightTerms;
		
		loadedViewers.clear();
		bestViewer = getBestViewer(viewMediaType);
		
		if (fixViewer.isSelected() || currentViewer == bestViewer){
			loadInViewer(currentViewer);
		}else{
			changeToViewerInEDT(bestViewer);
		}
		
		if(highlightTerms != null && !highlightTerms.isEmpty())
			loadInViewer(textViewer);
		
		for (AbstractViewer viewer : viewerList)
			if(!loadedViewers.contains(viewer))
				viewer.loadFile(null);

	}
	
	//Assume que os visualizadores foam adicionados em ordem crescente de prioridade
	private AbstractViewer getBestViewer(String contentType){
		AbstractViewer result = null;
		for (AbstractViewer viewer : viewerList)
			if (viewer.isSupportedType(contentType))
				result = viewer;
		return result;
	}

	private void loadInViewer(AbstractViewer viewer) {
		if (viewer.isSupportedType(viewMediaType)) {
			if(!loadedViewers.contains(viewer)){
				loadedViewers.add(viewer);
				if(viewer == textViewer && bestViewer != textViewer)
					viewer.loadFile(file, contentType, highlightTerms);
				
				else if(viewer instanceof HexViewer)
					viewer.loadFile(file, contentType, highlightTerms);
				
				else
					viewer.loadFile(viewFile, viewMediaType, highlightTerms);
			}
		}else
			viewer.loadFile(null);

	}

	@Override
	public void stateChanged(ChangeEvent arg0) {
		int currentTab = tabbedPane.getSelectedIndex();
		currentViewer = getViewerAtTab(currentTab);
		loadInViewer(currentViewer);
	}
	
	private AbstractViewer getViewerAtTab(int tab){
		String tabName = tabbedPane.getTitleAt(tab);
		for(AbstractViewer viewer : viewerList)
			if(viewer.getName().equals(tabName))
				return viewer;
		
		return null;
		
	}

	public void changeToViewer(AbstractViewer viewer) {
		for(int i = 0; i < tabbedPane.getTabCount(); i++)
			if(tabbedPane.getTitleAt(i).equals(viewer.getName())){
				tabbedPane.setSelectedIndex(i);
				break;
			}
	}
	
	private void changeToViewerInEDT(final AbstractViewer viewer){
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					changeToViewer(viewer);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public AbstractViewer getCurrentViewer(){
		return currentViewer;
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		
		if (evt.getSource() == prevHit) {
			currentViewer.scrollToNextHit(false);

		} else if (evt.getSource() == nextHit) {
			currentViewer.scrollToNextHit(true);
		}

	}

}
