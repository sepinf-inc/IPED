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
package dpf.sp.gpinf.indexer.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.tika.exception.TikaException;

public class PDFToImage {

	public static int RESOLUTION = 200;
	public static String EXT = "png";

	String PASSWORD = "";
	int startPage = 1;
	int endPage = Integer.MAX_VALUE;
	int IMAGETYPE = BufferedImage.TYPE_BYTE_GRAY;

	List<PDPage> pages;
	PDDocument document = null;
	public int numPages;
	File input;

	float scale = 2.8f;
	float rotation = 0f;

	public void load(File pdfFile) throws TikaException, IOException {
		input = pdfFile;

		try {
			document = PDDocument.load(pdfFile);
			if (document.isEncrypted())
				document.decrypt(PASSWORD);

			pages = document.getDocumentCatalog().getAllPages();
			numPages = pages.size();

		} catch (Exception e) {
			if (document != null)
				document.close();

			throw new TikaException("Erro ao carregar PDF", e);
		}

	}

	public void close() throws IOException {
		if (document != null)
			document.close();
	}

	public void convert(int page, File output) {

		try {
			BufferedImage buffImage = null;
			PDPage singlePage = pages.get(page);
			buffImage = singlePage.convertToImage(IMAGETYPE, RESOLUTION);

			boolean success = ImageIO.write(buffImage, EXT, output);
			if (!success) {
				throw new IOException("Error: no writer found for image format '" + EXT + "'");
			}

		} catch (Exception e) {
			System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " erro ao gerar imagem da pag. " + page + " de " + input.getAbsolutePath());
		}

	}

}