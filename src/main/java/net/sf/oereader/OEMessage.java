package net.sf.oereader;

import java.io.ByteArrayOutputStream;

/**
 * Class representing a message in a .dbx file.
 * 
 * Loaded by the {@link net.sf.oereader.OEMessageInfo MessageInfo} constructor.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OEMessage extends OEBase {
	private int marker;
	/**
	 * Length of the body of the message
	 */
	public int bodylength;
	/**
	 * Length of the text segment in the body
	 */
	public int seglength;
	/**
	 * Pointer to the next {@link net.sf.oereader.OEMessage Message} object
	 */
	public int next;
	/**
	 * Text in this segment of the message
	 */
	public String text;

	// tamanho máximo de 100MB para evitar loop infinito ao concatenar corpo do
	// email
	private static int maxSegments = 200000;

	public ByteArrayOutputStream bytes = new ByteArrayOutputStream();

	/**
	 * Loads the OEMessage object.
	 * 
	 * Traverses the linked list of message body text segments and adds them
	 * together
	 * 
	 * @param data
	 *            data to be read
	 * @param i
	 *            index to start from
	 */
	public OEMessage(OEData data, int i) {
		marker = toInt4(data, i);
		bodylength = toInt4(data, i + 4);
		seglength = toInt4(data, i + 8);
		next = toInt4(data, i + 12);
		/*
		 * text = new String(data,i+16,seglength); if (next != 0) { OEMessage n
		 * = new OEMessage(data,next); text += n.text; }
		 */

		if (i + 16 < 0 || seglength <= 0 || i + 16 + seglength > data.length)
			return;
		bytes.write(data.get(i + 16, seglength), 0, seglength);

		int next2 = next;
		int segNum = 0;
		while (next2 != 0 && ++segNum < maxSegments) {
			int j = next2;
			int seglength2 = toInt4(data, j + 8);
			if (j + 16 < 0 || seglength2 <= 0 || j + 16 + seglength2 > data.length)
				return;
			bytes.write(data.get(j + 16, seglength2), 0, seglength2);
			next2 = toInt4(data, j + 12);
		}

		// if(segNum == maxSegments)
		// System.out.println("Maximo de segmentos atingido!");

	}
}
