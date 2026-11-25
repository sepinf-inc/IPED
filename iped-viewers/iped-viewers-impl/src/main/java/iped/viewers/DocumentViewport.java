package iped.viewers;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import iped.io.IStreamSource;

/** Snapshot of the displayed document, bound to the item that finished loading. */
public interface DocumentViewport {

    BufferedImage captureDocument(IStreamSource expectedSource);

    static BufferedImage snapshot(JComponent component) {
        Rectangle visible = component.getVisibleRect();
        if (!component.isShowing() || visible.isEmpty()) {
            throw new IllegalStateException("Document viewport is not visible");
        }
        BufferedImage image = new BufferedImage(visible.width, visible.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.translate(-visible.x, -visible.y);
            component.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }
}
