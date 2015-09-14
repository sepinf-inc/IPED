package dpf.sp.gpinf.indexer.search.viewer;

import gpinf.led.HexViewPanel;

import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class HexViewer extends AbstractViewer{
	
	private HexViewPanel hexPanel = new HexViewPanel(16);
	JScrollPane scrollPane = new JScrollPane(hexPanel);
	
	public HexViewer(){
		super(new GridLayout());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		this.getPanel().add(scrollPane);
	}

	@Override
	public String getName() {
		return "Hex";
	}

	@Override
	public boolean isSupportedType(String contentType) {
		return true;
	}

	@Override
	public void init() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void loadFile(final StreamSource content, Set<String> highlightTerms) {
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					hexPanel.setFile(null);
					hexPanel.scrollRectToVisible(new Rectangle());
					if(content != null)
						hexPanel.setFile(content.getStream());
					hexPanel.repaint();
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
	}

	@Override
	public void scrollToNextHit(boolean forward) {
		// TODO Auto-generated method stub
		
	}

}
