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
package dpf.sp.gpinf.indexer.index;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.UnknownFileType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.index.HashClass.HashValue;
import dpf.sp.gpinf.indexer.util.IOUtil;

/*
 * Responsável por extrair/exportar subitens de containers.
 * Também exporta itens ativos em casos de extração automática de dados ou
 * em casos de extração de itens selecionados após análise.
 */
public class FileExtractor {

	public static String EXTRACT_DIR = "Exportados";
	private static HashSet<String> categoriesToExtract = new HashSet<String>();
	public static int subDirCounter = 0, subitensExtracted = 0;
	private static File subDir;
	
	//lock para evitar problemas ao extrair arquivos com mesmo hash simultaneamente
	private static Object hashLock = new Object();

	private TikaConfig config;
	private File extractDir, outputBase;
	private HashClass hasher;
	private HashMap<HashValue, HashValue> hashMap;

	public FileExtractor(TikaConfig config, File outputBase, HashClass hasher, HashMap<HashValue, HashValue> hashMap) {
		this.config = config;
		this.outputBase = outputBase;
		this.hasher = hasher;
		this.hashMap = hashMap;
		if (outputBase != null) {
			this.extractDir = new File(outputBase.getParentFile(), EXTRACT_DIR);
		}

	}

	public static synchronized void incSubitensExtracted() {
		subitensExtracted++;
	}

	/*
	 * public static synchronized void decSubitensExtracted(){
	 * subitensExtracted--; }
	 */

	public static int getSubitensExtracted() {
		return subitensExtracted;
	}

	public static void load(File file) throws FileNotFoundException, IOException {

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "Windows-1252"));
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.trim().startsWith("#") || line.trim().isEmpty())
				continue;
			categoriesToExtract.add(line.trim());
		}
		reader.close();
	}

	private static synchronized File getSubDir(File extractDir) {
		if (subDirCounter % 1000 == 0) {
			subDir = new File(extractDir, Integer.toString(subDirCounter / 1000));
			if (!subDir.exists())
				subDir.mkdirs();
		}
		subDirCounter++;
		return subDir;
	}

	public static boolean hasCategoryToExtract() {
		return categoriesToExtract.size() > 0;
	}

	public static boolean isToBeExtracted(EvidenceFile evidence) {

		boolean result = false;
		for (String category : evidence.getCategorySet()) {
			if (categoriesToExtract.contains(category)) {
				result = true;
				break;
			}
		}

		return result;
	}

	public static String getExtBySig(TikaConfig config, EvidenceFile evidence) {

		String ext = "";
		String ext1 = "." + evidence.getExt();
		MediaType mediaType = evidence.getMediaType();
		if (!mediaType.equals(MediaType.OCTET_STREAM))
			try {
				do {
					boolean first = true;
					for (String ext2 : config.getMimeRepository().forName(mediaType.toString()).getExtensions()) {
						if (first) {
							ext = ext2;
							first = false;
						}
						if (ext2.equals(ext1)) {
							ext = ext1;
							break;
						}
					}

				} while (ext.isEmpty() && !MediaType.OCTET_STREAM.equals((mediaType = config.getMediaTypeRegistry().getSupertype(mediaType))));
			} catch (MimeTypeException e) {
			}

		if (ext.isEmpty())
			ext = ext1;

		return ext;

	}

	public void extractFile(EvidenceFile evidence) {
		InputStream is = null;
		try {

			is = evidence.getBufferedStream();
			extractFile(is, evidence);
			//if(evidence.isCarved())
			evidence.setFileOffset(-1);

		} catch (IOException e) {
			System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Erro ao extrair " + evidence.getPath() + "\t\t" + e.toString());

		} finally {
			IOUtil.closeQuietly(is);
		}
	}

	private File getHashFile(String hash, String ext) {
		String path = hash.charAt(0) + "/" + hash.charAt(1) + "/" + hash + ext;
		File result = new File(extractDir, path);
		File parent = result.getParentFile();
		if (!parent.exists())
			parent.mkdirs();
		return result;
	}

	public void renameToHash(EvidenceFile evidence) {

		String hash = evidence.getHash();
		if (hash != null) {
			File file = evidence.getFile();
			String name = file.getName();
			String ext = "";
			int i = name.lastIndexOf('.');
			if (i != -1)
				ext = name.substring(i);

			
			File hashFile = getHashFile(hash, ext);
			
			HashValue hashVal = new HashValue(hash);
			HashValue hashLock;
			synchronized (hashMap) {
				hashLock = hashMap.get(hashVal);
			}
			
			
			synchronized(hashLock){
				if (!hashFile.exists()) {
					if (!file.renameTo(hashFile)) {
						// falha ao renomear pode ter sido causada por outra thread
						// criando arquivo com mesmo hash entre as 2 chamadas acima
						if (hashFile.exists()) {
							changeTargetFile(evidence, hashFile);
							if (!file.delete())
								System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Falha ao deletar " + file.getAbsolutePath());
						} else
							System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Falha ao renomear para o hash: " + evidence.getFileToIndex());
					} else
						changeTargetFile(evidence, hashFile);
					
				} else {
					changeTargetFile(evidence, hashFile);
					if (!file.delete())
						System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Falha ao deletar " + file.getAbsolutePath());
				}
			}
			

		}

	}

	private void changeTargetFile(EvidenceFile evidence, File file) {
		String relativePath;
		try {
			relativePath = IOUtil.getRelativePath(outputBase, file);
			evidence.setExportedFile(relativePath);
			evidence.setFile(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void extractFile(InputStream inputStream, EvidenceFile evidence) throws IOException {

		String ext = getExtBySig(config, evidence);
		String type = ext;
		ext = IOUtil.getValidFilename(ext);
		if (ext.equals("."))
			ext = "";

		String hash;
		File outputFile = null;
		Object hashLock = new Object();
		
		if (hasher == null)
			outputFile = new File(getSubDir(extractDir), Integer.toString(evidence.getId()) + ext);

		else if ((hash = evidence.getHash()) != null){
			outputFile = getHashFile(hash, ext);
			HashValue hashVal = new HashValue(hash);
			synchronized (hashMap) {
				hashLock = hashMap.get(hashVal);
			}
					
		}else {
			outputFile = new File(extractDir, Integer.toString(evidence.getId()) + ext);
			if (!outputFile.getParentFile().exists())
				outputFile.getParentFile().mkdirs();
		}
		
		
		synchronized(hashLock){
			if (outputFile.createNewFile()) {
				BufferedOutputStream bos = null;
				try {
					bos = new BufferedOutputStream(new FileOutputStream(outputFile));
					IOUtil.copiaArquivo(inputStream, bos);

				} catch (IOException e) {
					//e.printStackTrace();
					System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Erro ao extrair " + evidence.getPath() + "\t" + e.toString());

				} finally {
					if (bos != null)
						bos.close();
				}
			}
		}
		

		if (evidence.getMediaType().toString().equals("message/outlook-pst"))
			type = "";
		if (!type.isEmpty())
			type = type.substring(1);
		evidence.setType(new UnknownFileType(type));

		String relativePath = IOUtil.getRelativePath(outputBase, outputFile);
		evidence.setExportedFile(relativePath);
		evidence.setFile(outputFile);
		if (evidence.isSubItem())
			evidence.setLength(outputFile.length());

	}
	

}
