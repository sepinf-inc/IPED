package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import iped3.io.IStreamSource;

public class CADViewer extends Viewer {

    private static Logger LOGGER = LoggerFactory.getLogger(CADViewer.class);

    
    private JButton openExternalViewerButton;

    public CADViewer() {
        super(new GridLayout());
        JPanel externalViewerPanel = new JPanel();
        openExternalViewerButton = new JButton(Messages.getString("ExternalViewer.Open"));
        externalViewerPanel.add(openExternalViewerButton);
        this.getPanel().add(externalViewerPanel);
        
    }

    @Override
    public String getName() {
        return "CAD"; //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
    	 return contentType.equals("application/acad") //$NON-NLS-1$
                 || contentType.equals("application/x-acad") //$NON-NLS-1$
                 || contentType.equals("application/autocad_dwg") //$NON-NLS-1$
                 || contentType.equals("image/x-dwg") //$NON-NLS-1$
         || contentType.equals("application/dwg") //$NON-NLS-1$
         || contentType.equals("application/x-dwg") //$NON-NLS-1$
         || contentType.equals("application/x-autocad") //$NON-NLS-1$
         || contentType.equals("image/vnd.dwg") //$NON-NLS-1$
         || contentType.equals("drawing/dwg"); //$NON-NLS-1$
         //|| contentType.equals("image/vnd.dxf"); //$NON-NLS-1$
    	 
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {

    	
    	for (ActionListener actionListener: openExternalViewerButton.getActionListeners())
    		openExternalViewerButton.removeActionListener(actionListener);
    		
        if (content != null) {
				
		        
		        openExternalViewerButton.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						
		                try {
		                	InputStream in = new BufferedInputStream(content.getStream());
			                // put file in temp 
							File temp = File.createTempFile("IPED", "dwg", null);
			                temp.deleteOnExit();
							FileOutputStream out = new FileOutputStream(temp);
							byte[] buf = new byte[1024];
							int len;
							while ((len = in.read(buf)) > 0) {
								out.write(buf, 0, len);
							}
							out.close();
							in.close();
							String path = System.getProperty("java.class.path").substring(0, System.getProperty("java.class.path").lastIndexOf(File.separator));
					        LOGGER.debug("Openning external viewer: java -jar "+path+"/../tools/caffviewer.jar "+temp.getAbsolutePath());
							Runtime.getRuntime().exec("java -jar "+path+"/../tools/caffviewer.jar "+temp.getAbsolutePath());
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						
					}
				});
		        
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void copyScreen(Component comp) {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void scrollToNextHit(boolean forward) {
        // TODO Auto-generated method stub

    }
}