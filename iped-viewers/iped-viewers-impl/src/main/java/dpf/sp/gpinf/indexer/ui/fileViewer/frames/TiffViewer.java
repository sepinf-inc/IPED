package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IconUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import iped3.io.IStreamSource;

public class TiffViewer extends ImageViewer {
	private final JTextField textCurrentPage = new JTextField(2);
	private final JLabel labelNumPages = new JLabel();
	private final ExecutorService executor = Executors.newFixedThreadPool(1);
	private final List<JComponent> pageComponents = new ArrayList<JComponent>();

	volatile private InputStream is = null;
	volatile private ImageInputStream iis = null;
	volatile private ImageReader reader = null;

	volatile private IStreamSource currentContent;
	volatile private int currentPage = 0;
	volatile private int numPages = 0;

	private static final String actionFirstPage = "first-page";
	private static final String actionPreviousPage = "previous-page";
	private static final String actionNextPage = "next-page";
	private static final String actionLastPage = "last-page";

	@Override
	public String getName() {
		return "TIFF"; //$NON-NLS-1$
	}

	@Override
	public boolean isSupportedType(String contentType) {
		return contentType.equals("image/tiff"); //$NON-NLS-1$
	}

	public TiffViewer() {
		super(1);
		
		pageComponents.add(textCurrentPage);
		pageComponents.add(labelNumPages);

		textCurrentPage.setEditable(true);
		textCurrentPage.setHorizontalAlignment(SwingConstants.RIGHT);
		textCurrentPage.setToolTipText(Messages.getString("TiffViewer.Page"));
		textCurrentPage.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent a) {
				String value = textCurrentPage.getText().trim();
				try {
					int newPage = Integer.parseInt(value);
					if (newPage > numPages || newPage < 1) {
						return;
					}
					currentPage = newPage;
					displayPage();
				} catch (Exception e) {
					JOptionPane.showMessageDialog(null, "<" + value + "> " + Messages.getString("TiffViewer.InvalidPage") + numPages); //$NON-NLS-1$
				}
			}
		});

		labelNumPages.setPreferredSize(new Dimension(28, 14));
		labelNumPages.setHorizontalAlignment(SwingConstants.LEFT);

		pageComponents.add(createToolBarButton(actionFirstPage));
		pageComponents.add(createToolBarButton(actionPreviousPage));

		toolBar.add(textCurrentPage);
		toolBar.add(labelNumPages);

		pageComponents.add(createToolBarButton(actionNextPage));
		pageComponents.add(createToolBarButton(actionLastPage));

		JLabel separator = new JLabel(IconUtil.getIcon("separator", 24));
		pageComponents.add(separator);
		toolBar.add(separator);
	}

	protected void cleanState() {
		super.cleanState(true);
		currentPage = 1;
		numPages = 0;
		textCurrentPage.setText("");
		labelNumPages.setText(" / ");
	}

	@Override
	public void loadFile(IStreamSource content, Set<String> highlightTerms) {
		cleanState();
		currentContent = content;
		if (currentContent != null) {
			openContent(content);
		}
	}

	private void disposeResources() {
		try {
			if (reader != null) reader.dispose();
		} catch (Exception e) {
		}
		IOUtil.closeQuietly(iis);
		IOUtil.closeQuietly(is);
		reader = null;
		iis = null;
		is = null;
	}

	private void openContent(final IStreamSource content) {
		executor.submit(new Runnable() {
			@Override
			public void run() {
				disposeResources();
				if (content != currentContent) return;
				try {
					is = currentContent.getStream();
					iis = ImageIO.createImageInputStream(is);
					reader = ImageIO.getImageReaders(iis).next();
					reader.setInput(iis, false, true);
					numPages = reader.getNumImages(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
				displayPage(content);
			}
		});
	}

	private void displayPage() {
		super.cleanState(false);
		displayPage(currentContent);
	}

	private void displayPage(final IStreamSource content) {
		executor.submit(new Runnable() {
			@Override
			public void run() {
				if (content != currentContent) return;
				try {
					int w0 = reader.getWidth(currentPage - 1);
					int h0 = reader.getHeight(currentPage - 1);

					ImageReadParam params = reader.getDefaultReadParam();
					int sampling = w0 > h0 ? w0 / 1000 : h0 / 1000;
					if (sampling < 1) sampling = 1;
					int finalW = (int) Math.ceil((float) w0 / sampling);
					int finalH = (int) Math.ceil((float) h0 / sampling);

					params.setSourceSubsampling(sampling, sampling, 0, 0);
					image = reader.getImageTypes(currentPage - 1).next().createBufferedImage(finalW, finalH);
					params.setDestination(image);

					reader.read(currentPage - 1, params);

				} catch (Exception e) {
					e.printStackTrace();

				} finally {
					if (image != null) image = getCompatibleImage(image);
				}
				if (rotation != 0) {
					imagePanel.setImage(ImageUtil.rotatePos(image, rotation));
				} else {
					imagePanel.setImage(image);
				}
				refreshGUI();
			}
		});
	}

	private void refreshGUI() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				for (JComponent c : pageComponents) {
					c.setVisible(numPages > 1);
				}
				textCurrentPage.setText(String.valueOf(currentPage));
				labelNumPages.setText(" / " + numPages); //$NON-NLS-1$
			}
		});
	}

	private BufferedImage getCompatibleImage(BufferedImage image) {
		// obtain the current system graphical settings
		GraphicsConfiguration gfx_config = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

		//if image is already compatible and optimized for current system settings,
		if (image.getColorModel().equals(gfx_config.getColorModel())) {
			return image;
		}

		// image is not optimized, so create a new image that is
		BufferedImage new_image = gfx_config.createCompatibleImage(image.getWidth(), image.getHeight(), image.getTransparency());

		// get the graphics context of the new image to draw the old image on
		Graphics2D g2d = (Graphics2D) new_image.getGraphics();

		// actually draw the image and dispose of context no longer needed
		g2d.drawImage(image, 0, 0, null);
		g2d.dispose();

		// return the new optimized image
		return new_image;
	}

	@Override
	public void init() {
	}

	@Override
	public void dispose() {
	}

	@Override
	public void scrollToNextHit(boolean forward) {
	}

	public synchronized void actionPerformed(ActionEvent e) {
		if (image == null) {
			return;
		}
		super.actionPerformed(e);
		String cmd = e.getActionCommand();
		if (cmd.equals(actionFirstPage)) {
			if (currentContent != null && currentPage != 1) {
				currentPage = 1;
				displayPage();
			}
		} else if (cmd.equals(actionNextPage)) {
			if (currentContent != null && currentPage < numPages) {
				currentPage++;
				displayPage();
			}
		} else if (cmd.equals(actionPreviousPage)) {
			if (currentContent != null && currentPage > 1) {
				currentPage--;
				displayPage();
			}
		} else if (cmd.equals(actionLastPage)) {
			if (currentContent != null && currentPage != numPages) {
				currentPage = numPages;
				displayPage();
			}
		}
	}
}