package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import iped3.io.IStreamSource;

public class CADViewer extends Viewer {

    private static Logger LOGGER = LoggerFactory.getLogger(CADViewer.class);

    private static final String CAFFVIEWER_PATH = "tools/caffviewer/caffviewer.jar"; //$NON-NLS-1$

    private JButton openExternalViewerButton;

    private File basePath;

    private volatile File temp;

    public CADViewer() {
        super(new GridLayout());
        JPanel externalViewerPanel = new JPanel();
        openExternalViewerButton = new JButton(Messages.getString("ExternalViewer.Open"));
        addButtonListener();
        externalViewerPanel.add(openExternalViewerButton);
        this.getPanel().add(externalViewerPanel);

        try {
            basePath = new File(CADViewer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            basePath = basePath.getParentFile().getParentFile();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
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
        // || contentType.equals("image/vnd.dxf"); //$NON-NLS-1$

    }

    private void addButtonListener() {
        openExternalViewerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (temp == null) {
                    return;
                }
                File caffviewerPath = new File(basePath, CAFFVIEWER_PATH);
                // handles spaces in paths
                String[] cmd = { "java", "-jar", caffviewerPath.getAbsolutePath(), temp.getAbsolutePath() };
                LOGGER.debug("Openning external viewer: {}", Arrays.asList(cmd));

                ProcessBuilder pb = new ProcessBuilder();
                pb.command(cmd);
                pb.redirectErrorStream(true);
                try {
                    Process process = pb.start();
                    Util.ignoreStream(process.getInputStream());

                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {

        // clear previous temp file, opened caffviewer does not need it to work
        if (temp != null) {
            temp.delete();
        }

        // create temp file here to not block EDT
        if (content != null) {
            try (InputStream in = content.getStream()) {
                // put file in temp
                temp = File.createTempFile("IPED", ".dwg", null);
                temp.deleteOnExit();
                Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);

            } catch (IOException e) {
                e.printStackTrace();
                temp = null;
            }
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