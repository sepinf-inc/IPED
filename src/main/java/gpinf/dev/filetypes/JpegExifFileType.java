package gpinf.dev.filetypes;

/**
 * import com.drew.imaging.jpeg.JpegMetadataReader; import
 * com.drew.lang.Rational; import com.drew.metadata.Directory; import
 * com.drew.metadata.Metadata; import com.drew.metadata.exif.ExifDirectory;
 * 
 * /** Implementação da classe utilizada para arquivos de Imagem JPEG subtipo
 * EXIF.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class JpegExifFileType extends JpegFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = 841933903820732777L;

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Imagem JPEG (EXIF)";
	}

	/**
	 * Processa arquivos deste tipo. Especializado para extrair dados EXIF dos
	 * arquivos.
	 * 
	 * @param baseDir
	 *            diretório base onde arquivo de evidência exportados estão
	 *            armazenados
	 * @param evidenceFiles
	 *            lista de arquivos a ser processada
	 */
	/*
	 * public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles)
	 * { String category = "Dados EXIF da Imagem"; String noExifCategory =
	 * "Características da Imagem"; for (int i = 0; i < evidenceFiles.size();
	 * i++) { EvidenceFile evidenceFile = evidenceFiles.get(i); File file = new
	 * File(baseDir, evidenceFile.getExportedFile()); try { Metadata metadata =
	 * JpegMetadataReader.readMetadata(file); Directory exifDirectory =
	 * metadata.getDirectory(ExifDirectory.class); boolean hasExif = false;
	 * 
	 * if (exifDirectory.containsTag(ExifDirectory.TAG_MAKE)) {
	 * evidenceFile.addExtraProperty(category, new Property("Marca Câmera",
	 * exifDirectory.getString(ExifDirectory.TAG_MAKE).trim())); hasExif = true;
	 * } if (exifDirectory.containsTag(ExifDirectory.TAG_MODEL)) {
	 * evidenceFile.addExtraProperty(category, new Property("Modelo Câmera",
	 * exifDirectory.getString(ExifDirectory.TAG_MODEL).trim())); hasExif =
	 * true; } if
	 * (exifDirectory.containsTag(ExifDirectory.TAG_DATETIME_ORIGINAL)) {
	 * evidenceFile.addExtraProperty(category, new Property("Foto tirada em",
	 * FormatUtil.format(exifDirectory
	 * .getDate(ExifDirectory.TAG_DATETIME_ORIGINAL)))); hasExif = true; }
	 * 
	 * if (exifDirectory.containsTag(ExifDirectory.TAG_SOFTWARE)) {
	 * evidenceFile.addExtraProperty(category, new
	 * Property("Programa utilizado",
	 * exifDirectory.getString(ExifDirectory.TAG_SOFTWARE).trim())); hasExif =
	 * true; } if (exifDirectory.containsTag(ExifDirectory.TAG_EXPOSURE_TIME)) {
	 * Rational ratio =
	 * exifDirectory.getRational(ExifDirectory.TAG_EXPOSURE_TIME); int d =
	 * ratio.getDenominator(); int n = ratio.getNumerator(); int g = gcd(d, n);
	 * d /= g; n /= g; String value = (n == 1) ? (n + "/" + d) :
	 * FormatUtil.format(ratio.doubleValue());
	 * evidenceFile.addExtraProperty(category, new
	 * Property("Tempo de Exposição (seg)", value)); hasExif = true; } if
	 * (exifDirectory.containsTag(ExifDirectory.TAG_APERTURE)) { Rational ratio
	 * = exifDirectory.getRational(ExifDirectory.TAG_APERTURE);
	 * evidenceFile.addExtraProperty(category, new Property("Abertura",
	 * FormatUtil.format(ratio.doubleValue()))); hasExif = true; } Dimension dim
	 * = getImageDimension(file); if (dim != null) { String cat = hasExif ?
	 * category : noExifCategory; evidenceFile.addExtraProperty(cat, new
	 * Property("Largura", dim.width + " pixels"));
	 * evidenceFile.addExtraProperty(cat, new Property("Altura", dim.height +
	 * " pixels")); }
	 * 
	 * } catch (Exception e) { e.printStackTrace(); } } }
	 * 
	 * /** Calcula o máximo dividor comum de dois números.
	 */
	private static final int gcd(int m, int n) {
		return (n == 0) ? m : gcd(n, m % n);
	}
}
