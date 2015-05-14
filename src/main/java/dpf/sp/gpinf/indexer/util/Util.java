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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.zip.Deflater;

import javax.swing.SwingUtilities;

import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ToTextContentHandler;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.search.App;

public class Util {

	public static File getRelativeFile(String basePath, String export) {
		File file;
		if ((export.length() > 1 && export.charAt(1) == ':') || export.startsWith("/"))
			file = new File(export);
		else
			file = new File(basePath + "/" + export);

		return file;
	}

	public static String getRelativePath(File baseFile, File file) throws IOException {
		String basePath = baseFile.getCanonicalFile().getParent();
		if (basePath.endsWith(File.separator))
			basePath = basePath.substring(0, basePath.length() - 1);
		String filePath = file.getCanonicalPath();
		String result = filePath.replace(basePath, "");
		if (result.length() < filePath.length() && result.startsWith(File.separator))
			result = result.substring(1, result.length());
		return result.replace(File.separator, "/");

	}

	public static void writeObject(Object obj, String filePath) throws IOException {
		FileOutputStream fileOut = new FileOutputStream(new File(filePath));
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(obj);
		out.close();
		fileOut.close();
	}

	public static Object readObject(String filePath) throws IOException, ClassNotFoundException {
		FileInputStream fileIn = new FileInputStream(new File(filePath));
		ObjectInputStream in = new ObjectInputStream(fileIn);
		Object result;
		try {
			result = in.readObject();

		} finally {
			in.close();
			fileIn.close();
		}
		return result;
	}

	public static String concat(String filename, int num) {
		int extIndex = filename.lastIndexOf('.');
		if (extIndex == -1)
			return filename + " (" + num + ")";
		else {
			String ext = filename.substring(extIndex);
			return filename.substring(0, filename.length() - ext.length()) + " (" + num + ")" + ext;
		}
	}

	public static String getValidFilename(String filename) {
		filename = filename.trim();

		String invalidChars = "\\/:*?\"<>|";
		char[] chars = filename.toCharArray();
		for (int i = 0; i < chars.length; i++)
			if ((invalidChars.indexOf(chars[i]) >= 0) || (chars[i] < '\u0020'))
				filename = filename.replace(chars[i] + "", "");

		String[] invalidNames = { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8",
				"LPT9" };
		for (String name : invalidNames)
			if (filename.equalsIgnoreCase(name) || filename.toUpperCase().startsWith(name + "."))
				filename = "'" + filename;

		int MAX_LENGTH = 128;
		if (filename.length() > MAX_LENGTH) {
			int extIndex = filename.lastIndexOf('.');
			if (extIndex == -1)
				filename = filename.substring(0, MAX_LENGTH);
			else {
				String ext = filename.substring(extIndex);
				if (ext.length() > MAX_LENGTH / 2)
					ext = filename.substring(extIndex, extIndex + MAX_LENGTH / 2);
				filename = filename.substring(0, MAX_LENGTH - ext.length()) + ext;
			}
		}

		return filename;
	}

	public static void changeEncoding(File file) throws IOException {
		if (file.isDirectory()) {
			String[] names = file.list();
			for (int i = 0; i < names.length; i++) {
				File subFile = new File(file, names[i]);
				changeEncoding(subFile);
			}
		} else {
			Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "windows-1252"));
			String contents = "";
			char[] buf = new char[(int) file.length()];
			int count;
			while ((count = reader.read(buf)) != -1)
				contents += new String(buf, 0, count);

			reader.close();
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
			writer.write(contents);
			writer.close();
		}

	}

	public static void readFile(File origem) throws IOException {
		InputStream in = new BufferedInputStream(new FileInputStream(origem));
		byte[] buf = new byte[1024*1024];
		while (in.read(buf) != -1)
			;
		in.close();
	}

	
	public static ArrayList<String> loadKeywords(String filePath, String encoding) throws IOException {
		ArrayList<String> array = new ArrayList<String>();
		File file = new File(filePath);
		if (!file.exists())
			return array;
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
		String line;
		while ((line = reader.readLine()) != null)
			if (!line.trim().isEmpty())
				array.add(line.trim());
		reader.close();
		return array;
	}

	public static void saveKeywords(ArrayList<String> keywords, String filePath, String encoding) throws IOException {
		File file = new File(filePath);
		file.delete();
		file.createNewFile();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
		for (int i = 0; i < keywords.size(); i++)
			writer.write(keywords.get(i) + "\r\n");
		writer.close();
	}

	public static TreeSet<String> loadKeywordSet(String filePath, String encoding) throws IOException {
		TreeSet<String> set = new TreeSet<String>();
		File file = new File(filePath);
		if (!file.exists())
			return set;
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
		String line;
		while ((line = reader.readLine()) != null)
			if (!line.trim().isEmpty())
				set.add(line.trim());
		reader.close();
		return set;
	}

	public static void saveKeywordSet(TreeSet<String> keywords, String filePath, String encoding) throws IOException {
		File file = new File(filePath);
		file.delete();
		file.createNewFile();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
		for (String keyword : keywords)
			writer.write(keyword + "\r\n");
		writer.close();
	}

	/*
	 * public static String readFileAsString(String filePath) throws
	 * java.io.IOException{ File file = new File(filePath); InputStreamReader
	 * reader = new InputStreamReader(new FileInputStream(filePath),
	 * "windows-1252"); int length =
	 * Integer.valueOf(Long.toString(file.length())); char[] buf = new
	 * char[length]; reader.read(buf); reader.close(); return new String(buf); }
	 */

	/*
	 * public static String descompactarArquivo(File file, int maxSize) throws
	 * Exception { BufferedInputStream stream = new BufferedInputStream( new
	 * FileInputStream(file), 1000000); int size = file.length() > maxSize ?
	 * maxSize : (int) file.length(); byte[] compressedData = new byte[size];
	 * stream.read(compressedData); stream.close();
	 * 
	 * // Decompress the bytes Inflater decompressor = new Inflater();
	 * decompressor.setInput(compressedData); ByteArrayOutputStream bos = new
	 * ByteArrayOutputStream( compressedData.length); byte[] buf = new
	 * byte[1000000]; while (!decompressor.finished()) { int count =
	 * decompressor.inflate(buf); bos.write(buf, 0, count); maxSize -= count; if
	 * (maxSize <= 0) break; } bos.close();
	 * 
	 * // return bos.toString("UTF-8"); return bos.toString("windows-1252"); }
	 */

	/*
	 * public static void compactarArquivo(String contents, String
	 * filePath)throws Exception{ File file = new File(filePath+".compressed");
	 * if(file.exists()) return; StringBuffer strbuf = new
	 * StringBuffer(contents); strbuf byte[] input = contents.getBytes("UTF-8");
	 * Deflater compressor = new Deflater();
	 * compressor.setLevel(Deflater.BEST_COMPRESSION);
	 * compressor.setInput(input); compressor.finish(); ByteArrayOutputStream
	 * bos = new ByteArrayOutputStream(input.length); byte[] buf = new
	 * byte[1000000]; while (!compressor.finished()) { int count =
	 * compressor.deflate(buf); //System.out.println(count); bos.write(buf, 0,
	 * count); } bos.close(); FileOutputStream stream = new
	 * FileOutputStream(file); stream.write(bos.toByteArray()); stream.close();
	 * }
	 */

	/*
	 * public static void compactarArquivo(String contents, String exportPath)
	 * throws Exception { String textPath = exportPath.replaceFirst("Export",
	 * "Text") .replaceFirst("files", "Text"); File file = new File(textPath +
	 * ".compressed"); if (file.exists()) file.delete(); FileOutputStream stream
	 * = new FileOutputStream(file);
	 * 
	 * Deflater compressor = new Deflater();
	 * compressor.setLevel(Deflater.BEST_COMPRESSION); int offset = 0, bufLen =
	 * 1000000, len = bufLen; byte[] input, buf = new byte[bufLen]; while
	 * (!compressor.finished()) { if (compressor.needsInput()) { if (offset +
	 * len > contents.length()) len = contents.length() - offset; input =
	 * contents.substring(offset, offset + len).getBytes( "windows-1252");
	 * compressor.setInput(input); offset += len; if (offset ==
	 * contents.length()) compressor.finish(); } int count =
	 * compressor.deflate(buf); stream.write(buf, 0, count); } stream.close(); }
	 */

	public static void compactarArquivo(String filePath) throws Exception {
		File file = new File(filePath + ".compressed");
		if (file.exists())
			return;
		BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file), 1000000);
		BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(new File(filePath + ".extracted_text")), 1000000);
		Deflater compressor = new Deflater();
		compressor.setLevel(Deflater.BEST_COMPRESSION);
		int bufLen = 1000000;
		byte[] input = new byte[bufLen], output = new byte[bufLen];
		while (!compressor.finished()) {
			if (compressor.needsInput()) {
				int len = inStream.read(input);
				if (len != -1)
					compressor.setInput(input, 0, len);
				else
					compressor.finish();
			}
			int count = compressor.deflate(output);
			outStream.write(output, 0, count);
		}
		outStream.close();
		inStream.close();
	}

	public static void decompress(File input, File output) {

	}

	public static String tryDecodeUnknowCharset(byte[] data) {

		try {

			int count0 = 0, max = 100;
			if (data.length < max)
				max = data.length;

			for (int i = 0; i < max; i++)
				if (data[i] == 0)
					count0++;
			if (count0 * 2 >= max - 3)
				return new String(data, "UTF-16LE");

			boolean hasUtf8 = false;
			for (int i = 0; i < max - 1; i++)
				if (data[i] == (byte) 0xC3 && data[i + 1] >= (byte) 0x80 && data[i + 1] <= (byte) 0xBC) {
					hasUtf8 = true;
					break;
				}
			if (hasUtf8)
				return new String(data, "UTF-8");

			return new String(data, "windows-1252");

		} catch (UnsupportedEncodingException e) {
			return new String(data);
		}

	}

	public static String tryDecodeMixedCharset(byte[] data) {
		ToTextContentHandler handler = new ToTextContentHandler();
		try {
			new RawStringParser().parse(new ByteArrayInputStream(data), handler, new Metadata(), null);
			return handler.toString();

		} catch (Exception e) {
			return new String(data);
		}
	}
	
	public static File getReadOnlyFile(File file, Document doc) throws IOException{
		String offsetS = doc.get("offset");
		if(offsetS != null)
			return getFile(file, doc);
		if (file.canWrite()) {
			File tmp = File.createTempFile("tmp_", file.getName());
			tmp.deleteOnExit();
			IOUtil.copiaArquivo(file, tmp);
			file = tmp;
		}
		return file;
	}
	
	public static File getFile(File file, Document doc){
		String offsetS = doc.get("offset");
		String lenS = doc.get("tamanho");
		if(offsetS != null && lenS != null){
			long offset = Long.parseLong(offsetS);
			long len = Long.parseLong(lenS);
			String ext = doc.get("tipo");
			if (!ext.isEmpty())
				ext = "." + Util.getValidFilename(ext);
			try {
				return getSubFile(file, offset, len, ext);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return file;
	}
	
	public static InputStream getStream(File file, Document doc) throws IOException{
		String offsetS = doc.get("offset");
		String lenS = doc.get("tamanho");
		if(offsetS != null && lenS != null){
			long offset = Long.parseLong(offsetS);
			long len = Long.parseLong(lenS);
			return getSubStream(file, offset, len);
		}
		return new BufferedInputStream(new FileInputStream(file));
	}
	
	public static File getSubFile(File file, Long offset, long len, String ext) throws IOException{
		File tmp = File.createTempFile("tmp_", ext);
		tmp.deleteOnExit();
		InputStream in = getSubStream(file, offset, len);
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp));
		IOUtil.copiaArquivo(in, out);
		in.close();
		out.close();
		return tmp;
	}
	
	public static InputStream getSubStream(File file, Long offset, long len) throws IOException{
		InputStream in = new BufferedInputStream(new FileInputStream(file));
		if(offset != null && offset != -1)
			in = getSubStream(in, offset, len);
		
		return in;
	}
	
	public static InputStream getSubStream(InputStream in, long offset, long len) throws IOException{
		long skiped = 0;
		do
			skiped += in.skip(offset - skiped);
		while(skiped < offset);
		in = new LimitedInputStream(in, len);
		return in;
	}

	public static InputStream getSleuthStream(SleuthkitCase sleuthCase, Document doc) {
		String sleuthId = doc.get("sleuthId");
		try {
			Content sleuthFile = sleuthCase.getContentById(Long.valueOf(sleuthId));
			InputStream in = new ReadContentInputStream(sleuthFile);
			
			String offStr = doc.get("offset");
			if(offStr != null){
				long offset = Long.parseLong(offStr);
				long len = Long.parseLong(doc.get("tamanho"));
				in = getSubStream(in, offset, len);
			}
			in = new BufferedInputStream(in);
			return in;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static File extractSleuthFile(final SleuthkitCase sleuthCase, final Document doc) {

		class SleuthFileExtractor extends CancelableWorker<File, Object> {
			ProgressDialog progressDialog;
			long progress = 0;

			@Override
			protected File doInBackground() throws Exception {

				final SleuthFileExtractor extractor = this;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						progressDialog = new ProgressDialog(App.get(), extractor);
						if(!doc.get("tamanho").isEmpty())
							progressDialog.setMaximum(Long.valueOf(doc.get("tamanho")));
						progressDialog.setNote("Extraindo p/ arquivo temporário...");
					}
				});

				BufferedOutputStream out = null;
				InputStream in = null;
				File file = null;
				try {
					String ext = doc.get("tipo");
					if (!ext.isEmpty())
						ext = "." + Util.getValidFilename(ext);
					file = File.createTempFile("indexador", ext);
					file.deleteOnExit();

					out = new BufferedOutputStream(new FileOutputStream(file));
					in = getSleuthStream(sleuthCase, doc);

					byte[] buf = new byte[1000000];
					int len = 0;
					while ((len = in.read(buf)) >= 0 && !this.isCancelled()) {
						out.write(buf, 0, len);
						progress += len;

						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								progressDialog.setProgress(progress);
							}
						});
					}

				} finally {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							progressDialog.close();
						}
					});
					if (in != null)
						in.close();
					if (out != null)
						out.close();
				}

				return file;
			}
		}

		SleuthFileExtractor extractor = new SleuthFileExtractor();
		extractor.execute();

		try {
			return extractor.get();

		} catch (InterruptedException e) {
			extractor.cancel(false);

		} catch (ExecutionException e) {
			// e.printStackTrace();

		} catch (CancellationException e) {
			// e.printStackTrace();
		}
		return null;

	}

	public static void loadNatLibs(String path) {
		String arch = System.getProperty("os.arch") + File.separator;

		if (System.getProperty("os.name").startsWith("Linux")) {
			path += File.separator + "Linux" + File.separator + arch;
			// System.load(path + "libtsk_jni.so");

		} else {
			path += File.separator + "Win" + File.separator + arch;
			System.load(new File(path + "zlib.dll").getAbsolutePath());
			System.load(new File(path + "libewf.dll").getAbsolutePath());
		}
	}

	public static void loadLibs(File libDir) {
		File[] subFiles = libDir.listFiles();
		for (File subFile : subFiles)
			if (subFile.isFile())
				System.load(subFile.getAbsolutePath());
	}

    /**
     * Cria caminho completo a partir da pasta base, hash e extensao, no formato:
     * "base/0/1/01hhhhhhh.ext".
     */
    public static File getFileFromHash(File baseDir, String hash, String ext) {
        StringBuilder path = new StringBuilder();
        path.append(hash.charAt(0)).append('/');
        path.append(hash.charAt(1)).append('/');
        path.append(hash).append('.').append(ext);
        File result = new File(baseDir, path.toString());
        return result;
    }
    
    public static File findFileFromHash(File baseDir, String hash){
		if(hash == null)
			return null;
		hash = hash.toLowerCase();
		File hashDir = new File(baseDir, hash.charAt(0) + "/" + hash.charAt(1));
		if(hashDir.exists())
			for(File file : hashDir.listFiles())
				if(file.getName().toLowerCase().startsWith(hash))
					return file;
		return null;
	}
}
