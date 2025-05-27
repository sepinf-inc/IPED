package iped.utils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import iped.data.IItemReader;

public class ImageUtil {
    private static final int[] orientations = new int[] { 1, 6, 3, 8 };

    private static final String JBIG2 = "image/x-jbig2";
    private static final String ICO = "image/vnd.microsoft.icon";

    private static boolean pluginsPriorityUpdated;

    public static BufferedImage resizeImage(BufferedImage img, int maxW, int maxH) {
        return resizeImage(img, maxW, maxH, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * Redimensiona um imagem, mantendo sua proporção original se possível, mas
     * utilizando mantendo dimensões mínimas.
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

    public static BufferedImage getSubSampledImage(IItemReader itemReader, int size) {
        return doGetSubSampledImage(itemReader, size, null, null);
    }

    public static BufferedImage getSubSampledImage(IItemReader itemReader, int size, String mimeType) {
        return doGetSubSampledImage(itemReader, size, null, mimeType);
    }

    public static BufferedImage getSubSampledImage(InputStream inputStream, int size) {
        return doGetSubSampledImage(inputStream, size, null, null);
    }

    public static BufferedImage getSubSampledImage(File file, int size, String mimeType) {
        return doGetSubSampledImage(file, size, null, mimeType);
    }

    public static BufferedImage getSubSampledImage(IItemReader source, int size, BooleanWrapper renderException,
            String mimeType) {
        return doGetSubSampledImage(source, size, renderException, mimeType);
    }

    public static class BooleanWrapper {
        public boolean value;
    }

    public static final int getSamplingFactor(int w0, int h0, int targetSize) {
        int sampling = 1;
        if (w0 > targetSize || h0 > targetSize) {
            if (targetSize * h0 < w0 * targetSize) {
                sampling = w0 / targetSize;
            } else {
                sampling = h0 / targetSize;
            }
        }
        return sampling;
    }

    private static BufferedImage doGetSubSampledImage(Object source, int targetSize, BooleanWrapper renderException,
            String mimeType) {
        ImageInputStream iis = null;
        ImageReader reader = null;
        BufferedImage image = null;
        try {
            if (source instanceof IItemReader) {
                iis = ((IItemReader) source).getImageInputStream();
            } else {
                iis = ImageIO.createImageInputStream(source);
            }
            // JBIG2 needs that reader is get by mime type
            Iterator<ImageReader> iter = JBIG2.equals(mimeType) ? ImageIO.getImageReadersByMIMEType(mimeType)
                    : ImageIO.getImageReaders(iis);
            if (!iter.hasNext())
                return null;
            reader = iter.next();
            reader.setInput(iis, false, true);

            int idx = 0;
            // ICO may contains multiple images, get the largest one
            if (ICO.equals(mimeType)) {
                try {
                    int n = reader.getNumImages(false);
                    if (n > 1) {
                        int max = -1;
                        for (int i = 0; i < n; i++) {
                            int wi = reader.getWidth(i);
                            if (wi > max) {
                                max = wi;
                                idx = i;
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }

            int w0 = reader.getWidth(idx);
            int h0 = reader.getHeight(idx);
            int sampling = getSamplingFactor(w0, h0, targetSize);

            int finalW = (int) Math.ceil((float) w0 / sampling);
            int finalH = (int) Math.ceil((float) h0 / sampling);

            ImageReadParam params = reader.getDefaultReadParam();
            image = reader.getImageTypes(idx).next().createBufferedImage(finalW, finalH);
            params.setDestination(image);
            params.setSourceSubsampling(sampling, sampling, 0, 0);

            // seems jbig2 codec does not populate the destination image
            image = reader.read(idx, params);

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
            IOUtil.closeQuietly(iis);
        }

        return image;
    }

    public static boolean isMonoColorImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        int color = -1;
        if (pixels.length > 0) {
            // Starts at ~5% of the pixels
            for (int i = pixels.length / 20; i < pixels.length; i++) {
                int p = pixels[i];
                // Consider only non-transparent pixels
                if ((p & 0xFF000000) != 0) {
                    int c = p & 0xFFFFFF;
                    if (color == -1) {
                        // Store first color found
                        color = c;
                    } else if (color != c) {
                        // Has different colors
                        return false;
                    }
                }
            }
        }
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

    public static BufferedImage getImageFromType(BufferedImage img, int type) {
        if (img == null || img.getType() == type) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g2 = (Graphics2D) out.getGraphics();
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
        ImageReader reader = null;
        ImageInputStream iis = null;
        try {
            reader = ImageIO.getImageReadersBySuffix("jpeg").next();
            iis = ImageIO.createImageInputStream(inFile);
            reader.setInput(iis);
            BufferedImage img = reader.read(0);
            IIOMetadata meta = reader.getImageMetadata(0);
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_jpeg_image_1.0");
            return new Object[] { img, findAttribute(root, "comment") };
        } finally {
            IOUtil.closeQuietly(iis);
            try {
                if (reader != null)
                    reader.dispose();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Read image comment.
     *
     * @return Comment metadata, or null if no metadata is present.
     */
    public static String readJpegMetaDataComment(InputStream is) {
        ImageReader reader = null;
        ImageInputStream iis = null;
        String ret = null;
        try {
            reader = ImageIO.getImageReadersBySuffix("jpeg").next();
            iis = ImageIO.createImageInputStream(is);
            reader.setInput(iis);
            IIOMetadata meta = reader.getImageMetadata(0);
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_jpeg_image_1.0");
            ret = findAttribute(root, "comment");
        } catch (Exception e) {
        } finally {
            IOUtil.closeQuietly(iis);
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
        if (nRows <= 0 || nCols <= 0)
            return null;

        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();

        final int border = 2;
        int frameWidth = (imgWidth - 2 - border * (nCols + 1)) / nCols;
        int frameHeight = (imgHeight - 2 - border * (nRows + 1)) / nRows;
        if (frameWidth <= 2 || frameHeight <= 2)
            return null;

        List<BufferedImage> frames = new ArrayList<BufferedImage>();
        for (int row = 0; row < nRows; row++) {
            int y = row * (frameHeight + border) + 1 + border;
            for (int col = 0; col < nCols; col++) {
                int x = col * (frameWidth + border) + 1 + border;
                frames.add(img.getSubimage(x, y, frameWidth, frameHeight));
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
    public static void saveJpegWithMetadata(BufferedImage img, File outFile, String data, int compression) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("jpeg").next(); //$NON-NLS-1$
        ImageWriteParam jpgWriteParam = writer.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(compression / 100f);
        IIOMetadata imageMetadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(img), jpgWriteParam);

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
        writer.write(null, iioimage, jpgWriteParam);
        os.close();
        writer.dispose();
    }

    public static BufferedImage rotate(BufferedImage src, int pos) {
        if (pos < 0 || pos > 3) {
            return src;
        }
        return applyOrientation(src, orientations[pos]);
    }

    public static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        if (orientation > 4) {
            int aux = w;
            w = h;
            h = aux;
        }
        BufferedImage dest = new BufferedImage(w, h, src.getType());
        Graphics2D g = dest.createGraphics();
        if (orientation == 2 || orientation == 3 || orientation == 5 || orientation == 8) {
            g.scale(-1, 1);
            g.translate(-w, 0);
        }
        if (orientation == 3 || orientation == 4 || orientation == 7 || orientation == 8) {
            g.scale(1, -1);
            g.translate(0, -h);
        }
        if (orientation >= 5) {
            g.rotate(Math.PI / 2);
            g.translate(0, -w);
        }
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

    public static void writeCompressedJPG(BufferedImage img, OutputStream baos, int compression) throws IOException {
        ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(compression / 100f);
        jpgWriter.setOutput(new MemoryCacheImageOutputStream(baos));
        IIOImage outputImage = new IIOImage(img, null, null);
        jpgWriter.write(null, outputImage, jpgWriteParam);
        jpgWriter.dispose();        
    }

    /**
     * @param intensity
     *            A proportion between the blurring window and the image dimensions.
     *            Typical values are between 0.01 and 0.05.
     */
    public static BufferedImage blur(BufferedImage image, int maxSize, double intensity) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w > maxSize || h > maxSize) {
            if (w > h) {
                h = Math.max(1, h * maxSize / w);
                w = maxSize;
            } else {
                w = Math.max(1, w * maxSize / h);
                h = maxSize;
            }
        }
        BufferedImage newImage = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics g = newImage.getGraphics();
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        int radius = (int) Math.ceil(intensity * (newImage.getWidth() + newImage.getHeight()) / 2);
        byte[] pixels = ((DataBufferByte) newImage.getRaster().getDataBuffer()).getData();
        int[] len = { newImage.getWidth(), newImage.getHeight() };
        int[] step = { 1, newImage.getWidth() };
        for (int pass = 0; pass <= 1; pass++) {
            int len1 = len[pass];
            int len2 = len[1 - pass];
            int mult1 = step[pass];
            int mult2 = step[1 - pass];
            int[] update = new int[len2 << 2];
            for (int i = 0; i < len1; i++) {
                int pi = i * mult1;
                for (int j = 0; j < len2; j++) {
                    int cnt = 0;
                    int sum0 = 0;
                    int sum1 = 0;
                    int sum2 = 0;
                    int sum3 = 0;
                    for (int d = -radius; d <= radius; d++) {
                        int jd = j + d;
                        if (jd >= 0 && jd < len2) {
                            cnt++;
                            int off = (pi + jd * mult2) << 2;
                            sum0 += pixels[off] & 255;
                            sum1 += pixels[off + 1] & 255;
                            sum2 += pixels[off + 2] & 255;
                            sum3 += pixels[off + 3] & 255;
                        }
                    }
                    int off = j << 2;
                    update[off] = sum0 / cnt;
                    update[off + 1] = sum1 / cnt;
                    update[off + 2] = sum2 / cnt;
                    update[off + 3] = sum3 / cnt;
                }
                for (int j = 0; j < len2; j++) {
                    int off1 = (pi + j * mult2) << 2;
                    int off2 = j << 2;
                    pixels[off1] = (byte) update[off2];
                    pixels[off1 + 1] = (byte) update[off2 + 1];
                    pixels[off1 + 2] = (byte) update[off2 + 2];
                    pixels[off1 + 3] = (byte) update[off2 + 3];
                }
            }
        }
        return newImage;
    }

    public static BufferedImage grayscale(BufferedImage image) {
        if (image == null || image.getType() == BufferedImage.TYPE_BYTE_GRAY
                || image.getType() == BufferedImage.TYPE_USHORT_GRAY)
            return image;
        if (image.getColorModel().hasAlpha()) {
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
            ColorConvertOp op = new ColorConvertOp(cs, null);
            return op.filter(image, null);
        }
        return getImageFromType(image, BufferedImage.TYPE_BYTE_GRAY);
    }

    public static synchronized void updateImageIOPluginsPriority() {
        if (pluginsPriorityUpdated) {
            return;
        }
        IIORegistry registry = IIORegistry.getDefaultInstance();
        String[] mimes = ImageIO.getReaderMIMETypes();
        for (String mime : mimes) {
            Map<ImageReaderSpi, Integer> priorityBySpi = new HashMap<ImageReaderSpi, Integer>();
            Iterator<ImageReader> itReaders = ImageIO.getImageReadersByMIMEType(mime);
            while (itReaders.hasNext()) {
                ImageReader reader = itReaders.next();
                ImageReaderSpi spi = reader.getOriginatingProvider();
                String name = spi.getClass().toString().toLowerCase();
                int priority = 100;
                if (name.contains(".sun")) {
                    priority = 0;
                } else if (name.contains(".twelvemonkeys")) {
                    priority = 10;
                } else if (name.contains(".apache")) {
                    priority = 20;
                } else if (name.contains(".jai")) {
                    priority = 30;
                }
                if (name.contains(".big")) {
                    priority++;
                }
                priorityBySpi.put(spi, priority);
            }
            if (priorityBySpi.size() > 1) {
                for (ImageReaderSpi spi1 : priorityBySpi.keySet()) {
                    int p1 = priorityBySpi.get(spi1);
                    for (ImageReaderSpi spi2 : priorityBySpi.keySet()) {
                        int p2 = priorityBySpi.get(spi2);
                        if (p1 < p2) {
                            registry.setOrdering(ImageReaderSpi.class, spi1, spi2);
                        }
                    }
                }
            }
        }
        pluginsPriorityUpdated = true;
    }
}
