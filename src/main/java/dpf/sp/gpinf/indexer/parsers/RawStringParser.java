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
package dpf.sp.gpinf.indexer.parsers;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/*
 * Parser que extrai strings brutas de um arquivo qualquer. Útil para binários,
 * tipos desconhecidos e drivefreespace. É utilizada uma heurística para 
 * detectar codificações ISO-8859-1, UTF-8 e UTF-16 mescladas num mesmo arquivo.
 */
public class RawStringParser extends AbstractParser {

	private static final long serialVersionUID = 1L;
	/**
	 * @param args
	 */
	private static final Set<MediaType> SUPPORTED_TYPES = getTypes();

	public static int MIN_SIZE = 4;
	private int BUF_SIZE = 128 * 1024;
	private static final boolean[] isChar = getCharMap();
	
	private static boolean[] getCharMap(){
	
			boolean[] isChar = new boolean[256];
			for(int c = Byte.MIN_VALUE; c <= Byte.MAX_VALUE; c++)
				if((c >= 0x20 && c <= 0x7E) || (c >= (byte) 0xC0 && c <= (byte) 0xFC) || c == 0x0A || c == 0x0D || c == 0x09){
					isChar[c & 0xFF] = true;
					
				}
			return isChar;
		
	}

	private static Set<MediaType> getTypes() {
		HashSet<MediaType> supportedTypes = new HashSet<MediaType>();

		supportedTypes.add(MediaType.TEXT_PLAIN);
		/*
		 * supportedTypes.add(MediaType.application("x-elf"));
		 * supportedTypes.add(MediaType.application("x-sharedlib"));
		 * supportedTypes.add(MediaType.application("x-executable"));
		 * supportedTypes.add(MediaType.application("x-msdownload"));
		 * supportedTypes.add(MediaType.application("x-coredump"));
		 * supportedTypes.add(MediaType.application("x-object"));
		 * supportedTypes.add(MediaType.application("x-font-ttf"));
		 */
		return supportedTypes;
	}

	private static final boolean isChar(byte c) {
		//return (c >= 0x20 && c <= 0x7E) || (c >= (byte) 0xC0 && c <= (byte) 0xFC) || c == 0x0A || c == 0x0D || c == 0x09;
		return isChar[c & 0xFF];
	}

	private void flushBuffer() throws UnsupportedEncodingException, SAXException {
		if (tmpPos - bufPos >= MIN_SIZE)
			bufPos = tmpPos - MIN_SIZE;

		xhtml.characters(new String(buf, 0, bufPos, "windows-1252"));

		for (int k = 0; k < tmpPos - bufPos; k++)
			buf[k] = buf[bufPos + k];
		tmpPos = tmpPos - bufPos;
		bufPos = 0;
	}

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}

	int tmpPos = 0, bufPos = 0, inSize = 0, inPos = 0;
	byte[] buf = new byte[BUF_SIZE];
	byte[] input = new byte[BUF_SIZE];
	XHTMLContentHandler xhtml;
	//int zeros = 0;

	/*
	 * cria nova instancia para cada parsing para evitar acesso concorrente às
	 * mesmas variáveis locais no caso de parsing concorrente com mesma
	 * instancia
	 * 
	 * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
	 * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata,
	 * org.apache.tika.parser.ParseContext)
	 */
	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
		new RawStringParser().safeParse(stream, handler, metadata, context);
	}

	private void safeParse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		// metadata.set(Metadata.CONTENT_TYPE, MIME_TYPE);
		tmpPos = 0;
		bufPos = 0;

		xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();

		int i = 0;
		do {
			inSize = 0;
			while ((i = stream.read(input, inSize, BUF_SIZE - inSize)) > 0) {
				inSize += i;
			}
			inPos = 0;
			while (inPos < inSize) {
				byte c = input[inPos++];
				boolean utf8 = false;
				if (c == (byte) 0xC3) {
					byte c_ = inPos < inSize ? input[inPos++] : (byte) stream.read();
					if (c_ >= (byte) 0x80 && c_ <= (byte) 0xBC) {
						utf8 = true;
						buf[tmpPos++] = (byte) (c_ + 0x40);
					} else {
						buf[tmpPos++] = c;
						c = c_;
					}
					if (tmpPos == BUF_SIZE)
						flushBuffer();

				} else if (c == (byte) 0xC2) {
					byte c_ = inPos < inSize ? input[inPos++] : (byte) stream.read();
					if (c_ >= (byte) 0xA0 && c_ <= (byte) 0xBF) {
						utf8 = true;
						buf[tmpPos++] = c_;
					} else {
						buf[tmpPos++] = c;
						c = c_;
					}
					if (tmpPos == BUF_SIZE)
						flushBuffer();
				}
				if (!utf8)
					if (isChar(c)) {
						buf[tmpPos++] = c;
						if (tmpPos == BUF_SIZE)
							flushBuffer();
					} else {
						/*if (c == 0)
							zeros++;
						else
							zeros = 0;*/
						if (c != 0 || (inPos >= 3 && isChar(input[inPos - 3])) || (inPos + 1 < inSize && isChar(input[inPos + 1]))) {

							if (tmpPos - bufPos >= MIN_SIZE) {
								buf[tmpPos++] = 0x0A;
								bufPos = tmpPos;

								if (tmpPos == BUF_SIZE)
									flushBuffer();
							} else
								tmpPos = bufPos;

						}
					}
			}
		} while (i != -1 && !Thread.currentThread().isInterrupted());

		if (tmpPos - bufPos >= MIN_SIZE) {
			buf[tmpPos++] = 0x0A;
			bufPos = tmpPos;
		}
		xhtml.characters(new String(buf, 0, bufPos, "windows-1252"));

		xhtml.endDocument();

	}

	/*
	 * Método para Teste
	 */
	public static void main(String[] args) {

		// RawStringParser.MIN_SIZE = Integer.parseInt(args[0]);
		String filepath = "E:\\en_office_suite_2007_service_pack_1_x86_cd_x13-87877\\en_office_suite_2007_service_pack_1_x86_cd_x13-87877.iso";
		String outpath = "E:/strings3.txt";

		try {

			InputStream input = new BufferedInputStream(new FileInputStream(filepath));
			Writer output = new BufferedWriter(new FileWriter(outpath));

			// AutoDetectParser parser = new AutoDetectParser();
			// parser.setFallback(new RawStringParser());
			RawStringParser parser = new RawStringParser();

			Date start = new Date();

			ParseContext context = new ParseContext();
			ToTextContentHandler handler = new ToTextContentHandler(output);
			Metadata metadata = new Metadata();
			context.set(Parser.class, parser);
			parser.parse(input, handler, metadata, context);

			/*
			 * ParsingReader reader = new ParsingReader(parser, new
			 * File(filepath)); char[] buf = new char[1000000]; int i; while ((i
			 * = reader.read(buf, 0, buf.length)) != -1){ for(long j = 0; j <
			 * 10000000L; j++); System.out.println(i); output.write(buf, 0, i);
			 * }
			 */

			output.close();
			System.out.println(((new Date()).getTime() - start.getTime()) + " milisegundos");

		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

}

// Teste
// String[] chars =
// {"á","à","ã","â","Á","À","Ã","Â","é","ê","É","Ê","í","Í","ó","õ","ô","Ó","Õ","Ô","ú","Ú","ç","Ç"};

/*
 * try{ for(String c : chars){ System.out.print(c+" "); byte[] bytes =
 * c.getBytes("utf-8"); for(byte b : bytes)
 * System.out.print(Integer.toHexString(b).substring(6)); System.out.print(" " +
 * new String(bytes, "windows-1252")); bytes = c.getBytes("windows-1252");
 * for(byte b : bytes)
 * System.out.print(" "+Integer.toHexString(b).substring(6));
 * 
 * System.out.println(); } }catch(Exception e){ e.printStackTrace(); }
 */
