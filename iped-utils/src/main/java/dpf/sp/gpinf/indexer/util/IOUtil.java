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
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

import org.slf4j.LoggerFactory;

public class IOUtil {
	
	public static final boolean isDiskFull(IOException e) {
		if(e == null)
			return false;
		
		String msg = e.getMessage();
		if(msg != null) {
			msg = msg.toLowerCase();
			if( msg.startsWith("espaço insuficiente no disco") || //$NON-NLS-1$
			    msg.startsWith("não há espaço disponível no dispositivo") || //$NON-NLS-1$
				msg.startsWith("there is not enough space") || //$NON-NLS-1$
				msg.startsWith("not enough space") || //$NON-NLS-1$
				msg.startsWith("no space left on")) //$NON-NLS-1$
				return true;
		}
		return false;
	}
	
	public static boolean canCreateFile(File dir){
		
		try {
			File test = File.createTempFile("writeTest", null, dir); //$NON-NLS-1$
			test.deleteOnExit();
			test.delete();
			return true;
			
		} catch (IOException e) {
		    return false;
		}
	}
	
	public static void closeQuietly(Closeable in){
		try {
			if(in != null)
				in.close();
		} catch (Exception e) {
			//e.printStackTrace();
		}
	}

	public static void lerListaDeArquivos(File file, List<File> lista) {

		String[] subFileName = file.list();
		if (subFileName != null)
			for (int i = 0; i < subFileName.length; i++) {
				File subFile = new File(file, subFileName[i]);

				if (subFile.isDirectory())
					lerListaDeArquivos(subFile, lista);
				else
					lista.add(subFile);
			}
	}

	public static int countSubFiles(File file) {
		int result = 0;
		String[] subFileName = file.list();
		if (subFileName != null)
			for (int i = 0; i < subFileName.length; i++) {
				File subFile = new File(file, subFileName[i]);
				if (subFile.isDirectory())
					result += countSubFiles(subFile);
				else
					result++;
			}
		return result;
	}

	public static void deletarDiretorio(File file) {
		if (file.isDirectory()) {
			String[] names = file.list();
			if (names != null)
				for (int i = 0; i < names.length; i++) {
					File subFile = new File(file, names[i]);
					deletarDiretorio(subFile);
				}
		}
		try {
		  Files.delete(file.toPath());
    } catch (Exception e) {
        LoggerFactory.getLogger(IOUtil.class).info("Delete failed on '" + file.getPath() + "' " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

	}

	public static void copiaArquivo(File origem, File destino) throws IOException {
		copiaArquivo(origem, destino, false);
	}

	public static void copiaArquivo(File origem, File destino, boolean append) throws IOException {
		InputStream in = new BufferedInputStream(new FileInputStream(origem));
		OutputStream out = new BufferedOutputStream(new FileOutputStream(destino, append));
		if (append)
			out.write(0x0A);
		byte[] buf = new byte[1024*1024];
		int len;
		while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted()) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
		if (len != -1)
			if (!destino.delete())
				throw new IOException("Fail to delete " + destino.getPath()); //$NON-NLS-1$
	}

	public static void copiaArquivo(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[1024*1024];
		int len;
		while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted()) {
			out.write(buf, 0, len);
		}
	}

	public static void copiaDiretorio(File origem, File destino, boolean recursive) throws IOException {
		if (!destino.exists())
			if (!destino.mkdirs())
				throw new IOException("Fail to create folder " + destino.getAbsolutePath()); //$NON-NLS-1$
		String[] subdir = origem.list();
		for (int i = 0; i < subdir.length; i++) {
			File subFile = new File(origem, subdir[i]);
			if (subFile.isDirectory()) {
				if (recursive)
					copiaDiretorio(subFile, new File(destino, subdir[i]));
			} else {
				File subDestino = new File(destino, subdir[i]);
				copiaArquivo(subFile, subDestino);
			}
		}
	}

	public static void copiaDiretorio(File origem, File destino) throws IOException {
		copiaDiretorio(origem, destino, true);
	}
	
	public static byte[] loadInputStream(InputStream is) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int len = 0;
		while((len = is.read(buf)) != -1)
			bos.write(buf, 0, len);
		
		return bos.toByteArray();
	}

}
