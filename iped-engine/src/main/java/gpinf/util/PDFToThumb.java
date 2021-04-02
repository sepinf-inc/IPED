package gpinf.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;

import javax.imageio.ImageIO;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PDFToThumb {
    /*
    private static final DefaultResourceCache rc = new DefaultResourceCache() {
        @Override
        public void put(COSObject indirect, PDXObject xobject) throws IOException {
        }
    };
    */

    public static BufferedImage getPdfThumb(File file, int targetSize) throws Exception {
        BufferedImage img = null;
        PDDocument document = null;
        try {
            document = PDDocument.load(file, MemoryUsageSetting.setupMixed(1 << 24, 1 << 28));
//            document.setResourceCache(rc);
            PDPage page = document.getPage(0);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            pdfRenderer.setSubsamplingAllowed(true);
            PDRectangle rc = page.getCropBox();
            double maxDimension = Math.max(rc.getWidth(), rc.getHeight());
            double zoom = maxDimension <= 0 ? 0.5 : targetSize / maxDimension;
            int w = Math.min(targetSize, (int) Math.ceil(zoom * rc.getWidth()));
            int h = Math.min(targetSize, (int) Math.ceil(zoom * rc.getHeight()));
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
        } catch (Exception e) {} finally {
            try {
                if (document != null) {
                    document.close();
                }
            } catch (Exception e) {}
        }
        return img;
    }

    public static void main(String[] args) {
        try {
            File file = new File(args[0]);
            int targetSize = Integer.parseInt(args[1]);
            BufferedImage img = getPdfThumb(file, targetSize);
            if (img != null) {
                BufferedOutputStream out = new BufferedOutputStream(System.out);
                ImageIO.write(img, "jpg", out);
                out.close();
                System.exit(0);
            }
        } catch (Exception e) {}
        System.exit(-1);
    }
}