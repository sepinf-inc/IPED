/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.parsers.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.DefaultResourceCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.exception.TikaException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.util.IOUtil;

/**
 * Classe utilitária para converter páginas de arquivos PDF para arquivos de
 * imagem.
 * 
 * @author Nassif
 *
 */
public class PDFToImage implements Closeable {
    private static Logger LOGGER = LoggerFactory.getLogger(PDFToImage.class);

    private static float RESOLUTION_SCALE_RATIO = 72f;
    private static String PDFBOX = "pdfbox"; //$NON-NLS-1$
    private static String ICEPDF = "icepdf"; //$NON-NLS-1$
    public static String EXT = "png"; //$NON-NLS-1$

    public static final String PDFLIB_PROP = "pdfToImg.pdfLib"; //$NON-NLS-1$
    public static final String RESOLUTION_PROP = "pdfToImg.resolution"; //$NON-NLS-1$
    public static final String EXTERNAL_CONV_PROP = "pdfToImg.externalConv"; //$NON-NLS-1$
    public static final String EXTERNAL_CONV_MAXMEM_PROP = "pdfToImg.maxMem"; //$NON-NLS-1$

    private String PDFLIB = System.getProperty(PDFLIB_PROP, PDFBOX);
    private int RESOLUTION = Integer.valueOf(System.getProperty(RESOLUTION_PROP, "250")); //$NON-NLS-1$
    private boolean externalConversion = Boolean.valueOf(System.getProperty(EXTERNAL_CONV_PROP, "false")); //$NON-NLS-1$
    private String externalConvMaxMem = System.getProperty(EXTERNAL_CONV_MAXMEM_PROP, "512M"); //$NON-NLS-1$

    private int numPages;
    private int IMAGETYPE = BufferedImage.TYPE_BYTE_GRAY;

    private File input;

    private PDDocument document = null;
    private PDFRenderer pdfRenderer;

    private Document iceDoc;
    private float rotation = 0f;

    static {
        String javaVer = System.getProperty("java.version"); //$NON-NLS-1$
        if (javaVer.startsWith("1.8.0")) { //$NON-NLS-1$
            int idx = javaVer.indexOf("_"); //$NON-NLS-1$
            if (idx > 0 && Integer.valueOf(javaVer.substring(idx + 1)) < 191)
                System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        // Faster rendering in some cases with a lot of images per page
        System.setProperty("org.apache.pdfbox.rendering.UsePureJavaCMYKConversion", "true"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private class NoResourceCache extends DefaultResourceCache {
        @Override
        public void put(COSObject indirect, PDXObject xobject) throws IOException {
            // do not cache images to prevent OutOfMemory
        }
    }

    public int getNumPages() {
        return numPages;
    }

    public void load(File pdfFile) throws TikaException, IOException {
        input = pdfFile;
        try {
            if (PDFLIB.equals(PDFBOX)) {
                document = PDDocument.load(pdfFile, MemoryUsageSetting.setupMixed(10000000));
                document.setResourceCache(new NoResourceCache());
                pdfRenderer = new PDFRenderer(document);
                pdfRenderer.setSubsamplingAllowed(true);
                numPages = document.getNumberOfPages();

            } else {
                iceDoc = new Document();
                iceDoc.setUrl(pdfFile.toURI().toURL());
                numPages = iceDoc.getNumberOfPages();
            }

        } catch (Exception e) {
            if (document != null)
                document.close();
            if (iceDoc != null)
                iceDoc.dispose();

            throw new TikaException("Error loading PDF", e); //$NON-NLS-1$
        }

    }

    @Override
    public void close() throws IOException {
        if (document != null)
            document.close();
        if (iceDoc != null)
            iceDoc.dispose();
    }

    public boolean convert(int page, File output) throws TikaException {

        boolean success = false;
        try {
            if (!externalConversion) {
                if (PDFLIB.equals(PDFBOX)) {
                    BufferedImage buffImage = null;
                    buffImage = pdfRenderer.renderImageWithDPI(page, RESOLUTION, ImageType.GRAY);
                    success = ImageIO.write(buffImage, EXT, output);

                } else {
                    float scale = RESOLUTION / RESOLUTION_SCALE_RATIO;
                    // BufferedImage buffImage = (BufferedImage) iceDoc.getPageImage(page,
                    // GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, rotation, scale);
                    PDimension pd = iceDoc.getPageDimension(page, rotation);
                    int w = (int) (pd.getWidth() * scale);
                    int h = (int) (pd.getHeight() * scale);
                    BufferedImage buffImage = new BufferedImage(w, h, IMAGETYPE);
                    Graphics2D g = buffImage.createGraphics();
                    iceDoc.paintPage(page, g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, rotation, scale);
                    g.dispose();
                    success = ImageIO.write(buffImage, EXT, output);
                }
                if (!success)
                    throw new IOException("Error: no writer found for image format '" + EXT + "'"); //$NON-NLS-1$ //$NON-NLS-2$

            } else {
                URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                String jarDir = new File(url.toURI()).getParent();
                String classpath = jarDir + "/*"; //$NON-NLS-1$
                String[] cmd = { "java", "-cp", classpath, "-Xmx" + externalConvMaxMem, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        this.getClass().getCanonicalName(), input.getAbsolutePath(), output.getAbsolutePath(),
                        String.valueOf(page), PDFLIB, String.valueOf(RESOLUTION) };

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                Util.ignoreStream(process.getInputStream());

                try {
                    process.waitFor();
                    success = process.exitValue() == 0;
                    if (!success)
                        throw new IOException("External PDF to IMG conversion failed."); //$NON-NLS-1$

                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                    throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$
                }
            }

        } catch (TikaException e) {
            throw e;

        } catch (Exception e) {
            LOGGER.warn("{} error creating image of page {} of {}: {}", Thread.currentThread().getName(), page, //$NON-NLS-1$
                    input.getAbsolutePath(), e.toString());
        }
        return success;

    }

    public static void main(String[] args) {

        File input = new File(args[0]);
        File output = new File(args[1]);
        int page = Integer.parseInt(args[2]);

        boolean success = false;
        PDFToImage pdfConverter = new PDFToImage();
        pdfConverter.PDFLIB = args[3];
        pdfConverter.RESOLUTION = Integer.parseInt(args[4]);
        pdfConverter.externalConversion = false;
        try {
            pdfConverter.load(input);
            success = pdfConverter.convert(page, output);

        } catch (TikaException | IOException e) {
            // e.printStackTrace();

        } finally {
            IOUtil.closeQuietly(pdfConverter);
        }

        if (!success)
            System.exit(1);

    }

}