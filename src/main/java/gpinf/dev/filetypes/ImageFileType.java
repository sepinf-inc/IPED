package gpinf.dev.filetypes;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.Property;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Implementação da classe base utilizada para arquivos de imagem.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public abstract class ImageFileType extends EvidenceFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = 4456575742233251001L;

	/** Ícone associado ao tipo. */
	// private static final Icon icon = IconUtil.createIcon("ft-image");

	/**
	 * Método auxiliar que retorna as dimensões de uma imagem.
	 * 
	 * @param file
	 *            arquivo de imagem
	 * @return dimensão com largura e altura da imagem
	 */
	protected static final Dimension getImageDimension(File file) {
		try {
			ImageInputStream imageStream = ImageIO.createImageInputStream(file);

			java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);

			ImageReader reader = null;
			if (readers.hasNext()) {
				reader = readers.next();
			} else {
				imageStream.close();
				return null;
			}
			reader.setInput(imageStream, true, true);

			int imageWidth = reader.getWidth(0);
			int imageHeight = reader.getHeight(0);
			reader.dispose();
			imageStream.close();

			return new Dimension(imageWidth, imageHeight);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Processa arquivos deste tipo.
	 * 
	 * @param baseDir
	 *            diretório base onde arquivo de evidência exportados estão
	 *            armazenados
	 * @param evidenceFiles
	 *            lista de arquivos a ser processada
	 */
	@Override
	public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles) {
		String category = "Características da Imagem";
		for (int i = 0; i < evidenceFiles.size(); i++) {
			EvidenceFile evidenceFile = evidenceFiles.get(i);
			File file = new File(baseDir, evidenceFile.getExportedFile());
			Dimension dim = getImageDimension(file);
			if (dim != null) {
				evidenceFile.addExtraProperty(category, new Property("Largura", dim.width + " pixels"));
				evidenceFile.addExtraProperty(category, new Property("Altura", dim.height + " pixels"));
			}
		}
	}

	/**
	 * Retorna o tipo de visulização que deve ser utilizado pelo visualizador
	 * para este tipo de arquivo.
	 * 
	 * @return Tipo de visualização "Imagem".
	 * 
	 *         public ViewType getViewType() { return ViewType.IMAGE; }
	 * 
	 *         /** Retorna o ícone correspondente ao tipo de arquivo.
	 * 
	 *         public Icon getIcon() { return icon; }
	 */
}
