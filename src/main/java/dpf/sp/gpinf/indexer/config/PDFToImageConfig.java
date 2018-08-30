package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.parsers.PDFOCRTextParser;
import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;

public class PDFToImageConfig extends AbstractPropertiesConfigurable {
	public static final String MAXPDFTextSize2OCR = "maxPDFTextSize2OCR";
	public static final String PDFToImgResolution = "pdfToImgResolution";
	public static final String PDFToImgLib = "pdfToImgLib";
	public static final String ExternalPdfToImgConv = "externalPdfToImgConv";
	public static final String ExternalConvMaxMem = "externalConvMaxMem";
	public static final String ProcessImagesInPDFs = "processImagesInPDFs";
	public static final String CONFIG_FILE = "conf/AdvancedConfig.txt"; //$NON-NLS-1$

	public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.endsWith(CONFIG_FILE);
		}
	};

	@Override
	public Filter<Path> getResourceLookupFilter() {
		return filter;
	}

	@Override
	public void processConfigs(List<Path> resources) throws IOException {
		for (Iterator<Path> iterator = resources.iterator(); iterator.hasNext();) {
			Path path = iterator.next();
			processConfig(path);
		}
	}

	public void processConfig(Path resource) throws IOException {
		super.processConfig(resource);

		String value = null;

		value = properties.getProperty("maxPDFTextSize2OCR"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      PDFOCRTextParser.MAXCHARS2OCR = Integer.valueOf(value);
	    }

	    value = properties.getProperty("pdfToImgResolution"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      PDFToImage.RESOLUTION = Integer.valueOf(value);
	    }

	    value = properties.getProperty("pdfToImgLib"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      PDFToImage.PDFLIB = value;
	    }

	    value = properties.getProperty("externalPdfToImgConv"); //$NON-NLS-1$
	    if (value != null && !value.trim().isEmpty()) {
	      PDFToImage.externalConversion = Boolean.valueOf(value.trim());
	    }

	    value = properties.getProperty("externalConvMaxMem"); //$NON-NLS-1$
	    if (value != null && !value.trim().isEmpty()) {
	      PDFToImage.externalConvMaxMem = value.trim();
	    }

	    value = properties.getProperty("processImagesInPDFs"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      PDFOCRTextParser.processEmbeddedImages = Boolean.valueOf(value);
	    }

	    value = properties.getProperty("sortPDFChars"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      PDFOCRTextParser.sortPDFChars = Boolean.valueOf(value);
	    }
	}
}
