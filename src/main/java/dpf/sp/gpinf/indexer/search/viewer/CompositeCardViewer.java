package dpf.sp.gpinf.indexer.search.viewer;

import java.awt.CardLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class CompositeCardViewer extends AbstractViewer{
	
	private JPanel cardViewer = new JPanel(new CardLayout());
	private ArrayList<AbstractViewer> viewerList = new ArrayList<AbstractViewer>();
	private AbstractViewer currentViewer;
	
	public CompositeCardViewer(){
		super(new GridLayout());
		this.getPanel().add(cardViewer);
	}

	@Override
	public String getName() {
		return "Pré-visualização";
	}
	
	public void addViewer(AbstractViewer viewer) {
		viewerList.add(viewer);
		cardViewer.add(viewer.getPanel(), viewer.getName());
	}

	@Override
	public boolean isSupportedType(String contentType) {
		return getSupportedViewer(contentType) != null;
	}
	
	private AbstractViewer getSupportedViewer(String contentType){
		AbstractViewer result = null;
		for (AbstractViewer viewer : viewerList)
			if (viewer.isSupportedType(contentType))
				result = viewer;
		return result;
	}
	
	private void clear() {
		for (AbstractViewer viewer : viewerList)
			viewer.loadFile(null);
	}

	@Override
	public void init() {
		for (AbstractViewer viewer : viewerList)
			viewer.init();
	}

	@Override
	public void dispose() {
		for (AbstractViewer viewer : viewerList)
			viewer.dispose();
	}

	@Override
	public void loadFile(StreamSource content, Set<String> highlightTerms) {
		loadFile(content, null, highlightTerms);
	}
	
	@Override
	public void loadFile(StreamSource content, String contentType, Set<String> highlightTerms) {
		
		if(content == null){
			clear();
			return;
		}
		
		currentViewer = getSupportedViewer(contentType);
		if(currentViewer != null){
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					CardLayout layout = (CardLayout) cardViewer.getLayout();
					layout.show(cardViewer, currentViewer.getName());
				}
			});
			currentViewer.loadFile(content, contentType, highlightTerms);
		}
		
		for (AbstractViewer viewer : viewerList)
			if(viewer != currentViewer)
				viewer.loadFile(null);
			
	}

	@Override
	public void scrollToNextHit(boolean forward) {
		if(currentViewer != null)
			currentViewer.scrollToNextHit(forward);
	}

}
