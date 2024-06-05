package iped.parsers.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PDFToThumb implements Closeable {

    // using volatile to allow closing from other threads
    private volatile PDDocument document;

    public BufferedImage getPdfThumb(File file, int targetSize) throws Exception {
        BufferedImage img = null;
        try {
            document = PDDocument.load(file, MemoryUsageSetting.setupMixed(1 << 24, 1 << 28));
            // document.setResourceCache(new NoResourceCache());
            PDPage page = document.getPage(0);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            pdfRenderer.setSubsamplingAllowed(true);
            PDRectangle rc = page.getCropBox();
            double maxDimension = Math.max(rc.getWidth(), rc.getHeight());
            double zoom = maxDimension <= 0 ? 0.5 : targetSize / maxDimension;
            int w = Math.max(targetSize / 10, Math.min(targetSize, (int) Math.ceil(zoom * rc.getWidth())));
            int h = Math.max(targetSize / 10, Math.min(targetSize, (int) Math.ceil(zoom * rc.getHeight())));

            // Swap w/h, if page rotation is 90 or 270 degrees (see issue #1938)
            if (Math.abs(page.getRotation()) % 180 == 90) {
                int aux = w;
                w = h;
                h = aux;
            }

            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_BGR);
            Graphics2D g = img.createGraphics();
            Shape clip = g.getClip();
            AffineTransform at = g.getTransform();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setBackground(Color.white);
            g.clearRect(0, 0, w, h);
            pdfRenderer.renderPageToGraphics(0, g, (float) zoom);
            g.setClip(clip);
            g.setTransform(at);
            g.setColor(Color.black);
            g.drawRect(0, 0, w - 1, h - 1);
            g.dispose();
        } finally {
            try {
                if (document != null) {
                    document.close();
                    document = null;
                }
            } catch (Exception e) {}
        }
        return img;
    }

    public static void main(String[] args) {
        String itemInfo = args[2];
        boolean debug = Boolean.valueOf(args[3]);
        try (PDFToThumb pdfThumb = new PDFToThumb()) {
            PrintStream systemOut = System.out;
            System.setOut(System.err);
            File file = new File(args[0]);
            int targetSize = Integer.parseInt(args[1]);
            BufferedImage img = pdfThumb.getPdfThumb(file, targetSize);
            if (img != null) {
                BufferedOutputStream out = new BufferedOutputStream(systemOut);
                ImageIO.write(img, "jpg", out);
                out.close();
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.print("Error creating external thumb for " + itemInfo + " " + e.toString());
            if (debug) e.printStackTrace();
        }
        System.exit(-1);
    }

    @Override
    public void close() throws IOException {
        try {
            if (document != null) {
                document.close();
                document = null;
            }
        } catch (Exception e) {
        }
    }
}