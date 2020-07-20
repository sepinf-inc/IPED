package dpf.sp.gpinf.indexer.util;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import dpf.sp.gpinf.indexer.util.Messages;

public class SelectImagePathWithDialog implements Runnable {

    private File origImage;
    private File newImage;
    private boolean selectFolder = false;

    public SelectImagePathWithDialog(File origImage) {
        this.origImage = origImage;
    }

    public SelectImagePathWithDialog(File origImage, boolean selectFolder) {
        this.origImage = origImage;
        this.selectFolder = selectFolder;
    }

    public File askImagePathInGUI() {
        int i = 0;
        do {
            try {
                SwingUtilities.invokeAndWait(this);

            } catch (Exception e) {
                e.printStackTrace();
            }
        } while (++i < 2 && (newImage == null || !newImage.exists()));

        return newImage;
    }

    @Override
    public void run() {
        JOptionPane.showMessageDialog(null,
                Messages.getString("SelectImage.ImageNotFound") + origImage.getAbsolutePath()); //$NON-NLS-1$
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(Messages.getString("SelectImage.NewImgPath") + origImage.getName()); //$NON-NLS-1$
        fileChooser.setFileFilter(new ImageFilter(origImage));
        if (selectFolder)
            fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            newImage = fileChooser.getSelectedFile();
        } else
            newImage = null;
    }

    private class ImageFilter extends FileFilter {

        private String ext;

        public ImageFilter(File file) {
            int extIdx = file.getName().lastIndexOf('.');
            if (extIdx >= file.getName().length() - 5)
                ext = file.getName().substring(extIdx).toLowerCase();
        }

        @Override
        public boolean accept(File pathname) {
            if (ext != null && pathname.isFile() && !pathname.getName().toLowerCase().endsWith(ext))
                return false;

            return true;
        }

        @Override
        public String getDescription() {
            return ext == null ? "*.*" : "*" + ext; //$NON-NLS-1$ //$NON-NLS-2$
        }

    }
}
