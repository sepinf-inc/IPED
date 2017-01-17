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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
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

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.process.IndexItem;

public class Util {
	
  public static boolean isPhysicalDrive(File file) {
	return file.getName().toLowerCase().contains("physicaldrive")
	        || file.getAbsolutePath().toLowerCase().contains("/dev/");
  }

  public static File getRelativeFile(String basePath, String export) {
    File file;
    export = export.replace("\\", File.separator);
    if ((export.length() > 1 && export.charAt(1) == ':') || export.startsWith("/")) {
      file = new File(export);
    } else {
      file = new File(basePath + File.separator + export);
    }

    return file;
  }

  public static String getRelativePath(File baseFile, File file) throws IOException {
	  Path base = baseFile.getParentFile().toPath().normalize();
	  Path path = file.toPath().normalize();
	  if(!base.getRoot().equals(path.getRoot()))
	  	return file.getAbsolutePath();
	  return base.relativize(path).toString();
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
    if (extIndex == -1) {
      return filename + " (" + num + ")";
    } else {
      String ext = filename.substring(extIndex);
      return filename.substring(0, filename.length() - ext.length()) + " (" + num + ")" + ext;
    }
  }
  
  public static String removeNonLatin1Chars(String filename){
	  StringBuilder str = new StringBuilder();
	  for(char c : filename.toCharArray())
		  if((c >= '\u0020' && c <= '\u007E') || (c >= '\u00A0' && c <= '\u00FF'))
			  str.append(c);
	  return str.toString();
  }

  public static String getValidFilename(String filename) {
    filename = filename.trim();

    String invalidChars = "\\/:;*?\"<>|";
    char[] chars = filename.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      if ((invalidChars.indexOf(chars[i]) >= 0) || (chars[i] < '\u0020')) {
        filename = filename.replace(chars[i] + "", "");
      }
    }

    String[] invalidNames = {"CON", "PRN", "AUX", "NUL",
      "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
      "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};

    for (String name : invalidNames) {
      if (filename.equalsIgnoreCase(name) || filename.toUpperCase().startsWith(name + ".")) {
        filename = "1" + filename;
      }
    }

    //Limite máximo do Joliet
    int MAX_LENGTH = 64;

    if (filename.length() > MAX_LENGTH) {
      int extIndex = filename.lastIndexOf('.');
      if (extIndex == -1) {
        filename = filename.substring(0, MAX_LENGTH);
      } else {
        String ext = filename.substring(extIndex);
        int MAX_EXT_LEN = 20;
        if (ext.length() > MAX_EXT_LEN) {
          ext = filename.substring(extIndex, extIndex + MAX_EXT_LEN);
        }
        filename = filename.substring(0, MAX_LENGTH - ext.length()) + ext;
      }
    }

    char c;
    while (filename.length() > 0 && ((c = filename.charAt(filename.length() - 1)) == ' ' || c == '.')) {
      filename = filename.substring(0, filename.length() - 1);
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
      while ((count = reader.read(buf)) != -1) {
        contents += new String(buf, 0, count);
      }

      reader.close();
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
      writer.write(contents);
      writer.close();
    }

  }

  public static void readFile(File origem) throws IOException {
    InputStream in = new BufferedInputStream(new FileInputStream(origem));
    byte[] buf = new byte[1024 * 1024];
    while (in.read(buf) != -1)
			;
    in.close();
  }

  public static ArrayList<String> loadKeywords(String filePath, String encoding) throws IOException {
    ArrayList<String> array = new ArrayList<String>();
    File file = new File(filePath);
    if (!file.exists()) {
      return array;
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.trim().isEmpty()) {
        array.add(line.trim());
      }
    }
    reader.close();
    return array;
  }

  public static void saveKeywords(ArrayList<String> keywords, String filePath, String encoding) throws IOException {
    File file = new File(filePath);
    file.delete();
    file.createNewFile();
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
    for (int i = 0; i < keywords.size(); i++) {
      writer.write(keywords.get(i) + "\r\n");
    }
    writer.close();
  }

  public static TreeSet<String> loadKeywordSet(String filePath, String encoding) throws IOException {
    TreeSet<String> set = new TreeSet<String>();
    File file = new File(filePath);
    if (!file.exists()) {
      return set;
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.trim().isEmpty()) {
        set.add(line.trim());
      }
    }
    reader.close();
    return set;
  }

  public static void saveKeywordSet(TreeSet<String> keywords, String filePath, String encoding) throws IOException {
    File file = new File(filePath);
    file.delete();
    file.createNewFile();
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
    for (String keyword : keywords) {
      writer.write(keyword + "\r\n");
    }
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
    if (file.exists()) {
      return;
    }
    BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file), 1000000);
    BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(new File(filePath + ".extracted_text")), 1000000);
    Deflater compressor = new Deflater();
    compressor.setLevel(Deflater.BEST_COMPRESSION);
    int bufLen = 1000000;
    byte[] input = new byte[bufLen], output = new byte[bufLen];
    while (!compressor.finished()) {
      if (compressor.needsInput()) {
        int len = inStream.read(input);
        if (len != -1) {
          compressor.setInput(input, 0, len);
        } else {
          compressor.finish();
        }
      }
      int count = compressor.deflate(output);
      outStream.write(output, 0, count);
    }
    outStream.close();
    inStream.close();
  }

  public static void decompress(File input, File output) {

  }
  
  /**
   * Carrega bibliotecas nativas de uma pasta, tentando adivinhar a ordem correta
   * 
   * @param libDir
   */
  public static void loadNatLibs(File libDir) {
	  
	  if (System.getProperty("os.name").startsWith("Windows")) {
	      LinkedList<File> libList = new LinkedList<File>(); 
	      for(File file : libDir.listFiles())
	    	  if(file.getName().endsWith(".dll"))
	    		  libList.addFirst(file);
	      
	      int fail = 0;
	      while(!libList.isEmpty()){
	    	  File lib = libList.removeLast();
	    	  try{
	    		  System.load(lib.getAbsolutePath());
	    		  fail = 0;
	    		  
	    	  }catch(Throwable t){
	    		  libList.addFirst(lib);
	    		  fail++;
	    		  if(fail == libList.size())
	    			  throw t;
	    	  }
	      }
	  }
  }

  public static void loadLibs(File libDir) {
    File[] subFiles = libDir.listFiles();
    for (File subFile : subFiles) {
      if (subFile.isFile()) {
        System.load(subFile.getAbsolutePath());
      }
    }
  }

  /**
   * Cria caminho completo a partir da pasta base, hash e extensao, no formato:
   * "base/0/1/01hhhhhhh.ext".
   */
  public static File getFileFromHash(File baseDir, String hash, String ext) {
    StringBuilder path = new StringBuilder();
    hash = hash.toUpperCase();
    path.append(hash.charAt(0)).append('/');
    path.append(hash.charAt(1)).append('/');
    path.append(hash).append('.').append(ext);
    File result = new File(baseDir, path.toString());
    return result;
  }

  public static File findFileFromHash(File baseDir, String hash) {
    if (hash == null) {
      return null;
    }
    hash = hash.toUpperCase();
    File hashDir = new File(baseDir, hash.charAt(0) + "/" + hash.charAt(1));
    if (hashDir.exists()) {
      for (File file : hashDir.listFiles()) {
        if (file.getName().startsWith(hash)) {
          return file;
        }
      }
    }
    return null;
  }

}
