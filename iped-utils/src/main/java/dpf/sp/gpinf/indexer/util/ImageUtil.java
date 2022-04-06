package dpf.sp.gpinf.indexer.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/* 
 * @author Wladimir Leite (GPINF/SP)
 */
public class ImageUtil {
    private static final int[] orientations = new int[] { 1, 5, 3, 7 };

    public static BufferedImage resizeImage(BufferedImage img, int maxW, int maxH) {
        return resizeImage(img, maxW, maxH, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * Redimensiona um imagem, mantendo sua proporção original se possível, mas utilizando mantendo dimensões mínimas.
     */
    public static BufferedImage resizeImage(BufferedImage img, int maxW, int maxH, int minW, int minH, int imageType) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        if (imgW * maxH > imgH * maxW) {
            imgH = imgH * maxW / imgW;
            imgW = maxW;
        } else {
            imgW = imgW * maxH / imgH;
            imgH = maxH;
        }
        if (imgW <= minW) {
            imgW = minW;
        }
        if (imgH <= minH) {
            imgH = minH;
        }
        return resizeImageFixed(img, imgW, imgH, imageType);
    }

    /**
     * Redimensiona um imagem, mantendo sua proporção original.
     */
    public static BufferedImage resizeImage(BufferedImage img, int maxW, int maxH, int imageType) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        if (imgW * maxH > imgH * maxW) {
            imgH = imgH * maxW / imgW;
            imgW = maxW;
        } else {
            imgW = imgW * maxH / imgH;
            imgH = maxH;
        }
        if (imgW <= 0) {
            imgW = 1;
        }
        if (imgH <= 0) {
            imgH = 1;
        }
        return resizeImageFixed(img, imgW, imgH, imageType);
    }

    /**
     * Redimensiona um imagem numa área determinada.
     */
    public static BufferedImage resizeImageFixed(BufferedImage img, int imgW, int imgH, int imageType) {
        BufferedImage bufferedImage = new BufferedImage(imgW, imgH, imageType);
        Graphics2D graphics2D = bufferedImage.createGraphics();
        // graphics2D.setComposite(AlphaComposite.Src);
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.drawImage(img, 0, 0, imgW, imgH, null);
        graphics2D.dispose();
        return bufferedImage;
    }

    public static BufferedImage getSubSampledImage(InputStream source, int w, int h) {
        return doGetSubSampledImage(source, w, h, null, null);
    }

    public static BufferedImage getSubSampledImage(InputStream source, int w, int h, String mimeType) {
        return doGetSubSampledImage(source, w, h, null, mimeType);
    }

    public static BufferedImage getSubSampledImage(File source, int w, int h) {
        return doGetSubSampledImage(source, w, h, null, null);
    }

    public static BufferedImage getSubSampledImage(File source, int w, int h, String mimeType) {
        return doGetSubSampledImage(source, w, h, null, mimeType);
    }

    public static class BooleanWrapper {
        public boolean value;
    }

    public static final int getSamplingFactor(int w0, int h0, int w, int h) {
        int sampling = 1;
        if (w0 > w || h0 > h) {
            if (w * h0 < w0 * h) {
                sampling = w0 / w;
            } else {
                sampling = h0 / h;
            }
        }
        return sampling;
    }

    // Contribuição do PCF Wladimir e Nassif
    public static BufferedImage getSubSampledImage(InputStream source, int w, int h, BooleanWrapper renderException,
            String mimeType) {
        return doGetSubSampledImage(source, w, h, renderException, mimeType);
    }

    private static BufferedImage doGetSubSampledImage(Object source, int w, int h, BooleanWrapper renderException,
            String mimeType) {
        ImageInputStream iis = null;
        ImageReader reader = null;
        BufferedImage image = null;
        try {
            iis = ImageIO.createImageInputStream(source);
            Iterator<ImageReader> iter = mimeType == null ? ImageIO.getImageReaders(iis)
                    : ImageIO.getImageReadersByMIMEType(mimeType);
            if (!iter.hasNext())
                return null;
            reader = iter.next();
            reader.setInput(iis, false, true);

            int w0 = reader.getWidth(0);
            int h0 = reader.getHeight(0);
            int sampling = getSamplingFactor(w0, h0, w, h);

            int finalW = (int) Math.ceil((float) w0 / sampling);
            int finalH = (int) Math.ceil((float) h0 / sampling);

            ImageReadParam params = reader.getDefaultReadParam();
            image = reader.getImageTypes(0).next().createBufferedImage(finalW, finalH);
            params.setDestination(image);
            params.setSourceSubsampling(sampling, sampling, 0, 0);

            // seems jbig2 codec does not populate the destination image
            image = reader.read(0, params);

        } catch (Throwable e) {
            // e.printStackTrace();
            if (renderException != null)
                renderException.value = true;

            if (image != null && isMonoColorImage(image))
                image = null;

        } finally {
            if (reader != null) {
                reader.dispose();
            }
            try {
                iis.close();
            } catch (IOException e) {
            }
        }

        return image;
    }

    public static boolean isMonoColorImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        int color;
        if (pixels.length > 0) {
            color = pixels[0];
        } else
            return false;

        for (int p : pixels)
            if (p != color)
                return false;

        return true;
    }

    public static BufferedImage trim(BufferedImage img) {
        if (img == null) {
            return null;
        }

        double WHITE_TOLERANCE = 20;
        int[] pixels = new int[0];

        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 2 || h <= 2) {
            return img;
        }
        if (pixels.length < w * h) {
            pixels = new int[w * h];
        }
        img.getRGB(0, 0, w, h, pixels, 0, w);
        Rectangle rc = new Rectangle(1, 1, w - 2, h - 2);
        for (int dir = 0; dir <= 1; dir++) {
            while (rc.height > 1) {
                int off = (dir == 0 ? rc.y : rc.y + rc.height - 1) * w;
                int sum = 0;
                for (int x = rc.x; x < rc.width + rc.x; x++) {
                    int pixel = pixels[off + x];
                    sum += 255 - red(pixel) + 255 - green(pixel) + 255 - blue(pixel);
                }
                if (sum < WHITE_TOLERANCE * rc.width) {
                    if (dir == 0) {
                        rc.y++;
                    }
                    rc.height--;
                } else {
                    break;
                }
            }
        }
        for (int dir = 0; dir <= 1; dir++) {
            while (rc.width > 1) {
                int off = dir == 0 ? rc.x : rc.x + rc.width - 1;
                int sum = 0;
                for (int y = rc.y; y < rc.height + rc.y; y++) {
                    int pixel = pixels[off + y * w];
                    sum += 255 - red(pixel) + 255 - green(pixel) + 255 - blue(pixel);
                }
                if (sum < WHITE_TOLERANCE * rc.height) {
                    if (dir == 0) {
                        rc.x++;
                    }
                    rc.width--;
                } else {
                    break;
                }
            }
        }
        return img.getSubimage(rc.x, rc.y, rc.width, rc.height);
    }

    /**
     * Obtém as dimensões de uma imagem. Este método visa evitar que a imagem seja
     * lida/descompactada, já que apenas necessita-se das dimensões.
     */
    public static Dimension getImageFileDimension(Object img) {
        ImageInputStream in = null;
        try {
            in = ImageIO.createImageInputStream(img);
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(in);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            }
        } catch (Exception e) {
            // ignore
        } finally {
            IOUtil.closeQuietly(in);
        }
        return null;
    }

    /**
     * Cria uma nova imagem das dimensões especificadas, com a imagem original
     * centralizada na mesma.
     */
    public static BufferedImage getCenteredImage(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = (Graphics2D) out.getGraphics();
        int x = (w - img.getWidth()) / 2;
        int y = (h - img.getHeight()) / 2;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.drawImage(img, x, y, null);
        g2.dispose();
        return out;
    }

    public static BufferedImage getOpaqueImage(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = (Graphics2D) out.getGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return out;
    }

    private static final int red(int color) {
        return color & 0xff;
    }

    private static final int green(int color) {
        return (color >>> 8) & 0xff;
    }

    private static final int blue(int color) {
        return (color >>> 16) & 0xff;
    }

    /**
     * Lê um arquivo JPEG e retorna a imagem e um comentário, se este estiver
     * presente nos metadados da imagem.
     *
     * @return Array com {BufferedImage, String}
     */
    public static Object[] readJpegWithMetaData(File inFile) throws IOException {
        ImageReader reader = ImageIO.getImageReadersBySuffix("jpeg").next(); //$NON-NLS-1$
        ImageInputStream is = ImageIO.createImageInputStream(inFile);
        reader.setInput(is);
        BufferedImage img = reader.read(0);
        IIOMetadata meta = reader.getImageMetadata(0);
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_jpeg_image_1.0"); //$NON-NLS-1$
        is.close();
        reader.dispose();
        return new Object[] { img, findAttribute(root, "comment") }; //$NON-NLS-1$
    }

    /**
     * Read image comment.
     *
     * @return Comment metadata, or null if no metadata is present.
     */
    public static String readJpegMetaDataComment(InputStream is) throws IOException {
        ImageReader reader = null;
        ImageInputStream iis = null;
        String ret = null;
        try {
            reader = ImageIO.getImageReadersBySuffix("jpeg").next();
            iis = ImageIO.createImageInputStream(is);
            reader.setInput(iis);
            IIOMetadata meta = reader.getImageMetadata(0);
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_jpeg_image_1.0"); //$NON-NLS-1$
            ret = findAttribute(root, "comment");
        } catch (Exception e) {
        } finally {
            try {
                if (iis != null)
                    iis.close();
            } catch (Exception e) {
            }
            try {
                if (reader != null)
                    reader.dispose();
            } catch (Exception e) {
            }
        }
        return ret;
    }

    public static BufferedImage getBestFramesFit(BufferedImage img, String comment, int targetWidth, int targetHeight) {
        return getBestFramesFit(img, comment, targetWidth, targetHeight, -1, -1);
    }

    public static BufferedImage getBestFramesFit(BufferedImage img, String comment, int targetWidth, int targetHeight,
            int minFrames, int maxFrames) {
        int nRows = 0;
        int nCols = 0;
        if (comment != null && comment.startsWith("Frames")) { //$NON-NLS-1$
            int p1 = comment.indexOf('='); // $NON-NLS-1$
            int p2 = comment.indexOf('x'); // $NON-NLS-1$
            if (p1 > 0 && p2 > 0) {
                nRows = Integer.parseInt(comment.substring(p1 + 1, p2));
                nCols = Integer.parseInt(comment.substring(p2 + 1));
            }
        }
        if (nRows <= 0 || nCols <= 0)
            return img;

        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();

        final int border = 2;
        int frameWidth = (imgWidth - 2 * border - border * nCols) / nCols;
        int frameHeight = (imgHeight - 2 * border - border * nRows) / nRows;

        int totalFrames = nCols * nRows;
        if (maxFrames < 0 || maxFrames > totalFrames)
            maxFrames = totalFrames;
        int bestRows = nCols;
        int bestCols = nRows;
        if (minFrames < 0)
            minFrames = (int) Math.ceil(maxFrames * 0.8);
        double maxUsage = 0;
        for (int cols = 1; cols <= maxFrames; cols++) {
            int maxRows = Math.max(1, maxFrames / cols);
            int minRows = Math.max(1, minFrames / cols);
            for (int rows = minRows; rows <= maxRows; rows++) {
                int nf = rows * cols;
                if (nf < minFrames || nf > totalFrames)
                    continue;
                int ww = (frameWidth + 1) * cols + border * 2;
                int hh = (frameHeight + 1) * rows + border * 2;
                double z = Math.min(targetWidth / (double) ww, targetHeight / (double) hh);
                double currUsage = ww * z * hh * z + nf;
                if (currUsage > maxUsage) {
                    maxUsage = currUsage;
                    bestRows = rows;
                    bestCols = cols;
                }
            }
        }
        if (bestRows == nRows && bestCols == nCols)
            return img;

        double rate = totalFrames / (double) (bestRows * bestCols);
        BufferedImage fit = new BufferedImage(bestCols * (frameWidth + 1) + border * 2,
                bestRows * (frameHeight + 1) + border * 2, BufferedImage.TYPE_INT_BGR);
        Graphics2D g2 = (Graphics2D) fit.getGraphics();
        g2.setColor(new Color(222, 222, 222));
        g2.fillRect(0, 0, fit.getWidth(), fit.getHeight());
        g2.setColor(new Color(22, 22, 22));
        g2.drawRect(0, 0, fit.getWidth() - 1, fit.getHeight() - 1);

        double pos = rate * 0.5;
        for (int row = 0; row < bestRows; row++) {
            int y = row * (frameHeight + 1) + border;
            for (int col = 0; col < bestCols; col++) {
                int x = col * (frameWidth + 1) + border;
                int idx = (int) pos;
                int sx = border + (border + frameWidth) * (idx % nCols);
                int sy = border + (border + frameHeight) * (idx / nCols);
                g2.drawImage(img, x, y, x + frameWidth, y + frameHeight, sx, sy, sx + frameWidth, sy + frameHeight,
                        null);
                pos += rate;
            }
        }
        g2.dispose();
        return fit;
    }

    public static List<byte[]> getBmpFrames(File videoFramesFile) throws IOException {
        ArrayList<byte[]> result = new ArrayList<>();
        List<BufferedImage> frames = getFrames(videoFramesFile);
        if (frames != null) {
            for (BufferedImage frame : frames) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(frame, "bmp", baos);
                result.add(baos.toByteArray());
            }
        }
        return result;
    }

    public static List<BufferedImage> getFrames(File videoFramesFile) throws IOException {
        Object[] read = ImageUtil.readJpegWithMetaData(videoFramesFile);
        if (read != null && read.length == 2) {
            String videoComment = (String) read[1];
            if (videoComment != null && videoComment.startsWith("Frames=")) {
                return ImageUtil.getFrames((BufferedImage) read[0], videoComment);
            }
        }
        return null;
    }

    public static List<BufferedImage> getFrames(BufferedImage img, String comment) {
        int nRows = 0;
        int nCols = 0;
        if (comment != null && comment.startsWith("Frames")) {
            int p1 = comment.indexOf('=');
            int p2 = comment.indexOf('x');
            if (p1 > 0 && p2 > 0) {
                nRows = Integer.parseInt(comment.substring(p1 + 1, p2));
                nCols = Integer.parseInt(comment.substring(p2 + 1));
            }
        }
        if (nRows <= 0 || nCols <= 0) return null;

        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();

        final int border = 2;
        int frameWidth = (imgWidth - 2 * border - border * nCols) / nCols;
        int frameHeight = (imgHeight - 2 * border - border * nRows) / nRows;
        if (frameWidth <= 2 || frameHeight <= 2) return null;

        List<BufferedImage> frames = new ArrayList<BufferedImage>();
        for (int row = 0; row < nRows; row++) {
            int y = row * (frameHeight + border) + border;
            for (int col = 0; col < nCols; col++) {
                int x = col * (frameWidth + border) + border;
                BufferedImage frame = new BufferedImage(frameWidth - 2, frameHeight - 2, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g2 = frame.createGraphics();
                g2.drawImage(img, 0, 0, frameWidth - 2, frameHeight - 2, x + 1, y + 1, x + frameWidth - 1, y + frameHeight - 1, null);
                g2.dispose();
                frames.add(frame);
            }
        }
        return frames;
    }
    
    /**
     * Método auxiliar que percorre uma árvore buscando o valor de um nó com
     * determinado nome.
     */
    private static String findAttribute(Node node, String name) {
        String ret = null;
        if (node.getNodeName().equals(name)) {
            ret = node.getNodeValue();
        }
        NamedNodeMap map = node.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            String s = findAttribute(map.item(i), name);
            if (ret == null) {
                ret = s;
            }
        }
        NodeList nl = node.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            String s = findAttribute(nl.item(i), name);
            if (ret == null) {
                ret = s;
            }
        }
        return ret;
    }

    /**
     * Grava uma imagem JPEG e insere um comentário nos metadados da imagem.
     */
    public static void saveJpegWithMetadata(BufferedImage img, File outFile, String data) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("jpeg").next(); //$NON-NLS-1$
        JPEGImageWriteParam jpegParams = (JPEGImageWriteParam) writer.getDefaultWriteParam();
        IIOMetadata imageMetadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(img), jpegParams);

        Element tree = (Element) imageMetadata.getAsTree("javax_imageio_jpeg_image_1.0"); //$NON-NLS-1$
        NodeList comNL = tree.getElementsByTagName("com"); //$NON-NLS-1$
        IIOMetadataNode comNode;
        if (comNL.getLength() == 0) {
            comNode = new IIOMetadataNode("com"); //$NON-NLS-1$
            Node markerSequenceNode = tree.getElementsByTagName("markerSequence").item(0); //$NON-NLS-1$
            markerSequenceNode.insertBefore(comNode, markerSequenceNode.getFirstChild());
        } else {
            comNode = (IIOMetadataNode) comNL.item(0);
        }
        comNode.setUserObject((data).getBytes("ISO-8859-1")); //$NON-NLS-1$
        imageMetadata.setFromTree("javax_imageio_jpeg_image_1.0", tree); //$NON-NLS-1$

        IIOImage iioimage = new IIOImage(img, null, imageMetadata);
        ImageOutputStream os = ImageIO.createImageOutputStream(outFile);
        writer.setOutput(os);
        writer.write(null, iioimage, jpegParams);
        os.close();
        writer.dispose();
    }

    public static BufferedImage rotatePos(BufferedImage src, int pos) {
        if (pos < 0 || pos > 3) {
            return src;
        }
        return rotate(src, orientations[pos]);
    }

    public static BufferedImage rotate(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8)
            return src;
        int angle = (orientation - 1) / 2;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(angle == 1 ? w : h, angle == 1 ? h : w, src.getType());
        Graphics2D g = dest.createGraphics();
        double d = (h - w) / 2.0;
        if (angle == 2)
            g.translate(d, d);
        else if (angle == 3)
            g.translate(-d, -d);
        g.rotate((angle == 1 ? 2 : angle == 2 ? 1 : 3) * Math.PI / 2, dest.getWidth() / 2.0, dest.getHeight() / 2.0);
        g.drawRenderedImage(src, null);
        g.dispose();
        return dest;
    }
    
    public static boolean isCompressedBMP(File file) throws FileNotFoundException, IOException {
        byte[] b = new byte[34];
        if (file.length() >= b.length) {
            try (FileInputStream is = new FileInputStream(file)) {
                is.read(b);
                if (b[30] == 0 && b[31] == 0 && b[32] == 0 && b[33] == 0)
                    return false;
            }
        }
        return true;
    }
}
