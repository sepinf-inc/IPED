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
package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import javax.xml.bind.DatatypeConverter;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IOUtil;

/*
 * Classe para calcular e manipular hashes.
 */
public class ComputeHashTask extends AbstractTask{

	private MessageDigest digest;
	private String algorithm;

	public static class HashValue {

		byte[] bytes;

		public HashValue(String hash) {
			bytes = DatatypeConverter.parseHexBinary(hash);
		}

		public int compareTo(HashValue hash) {
			for (int i = 0; i < bytes.length; i++) {
				if ((bytes[i] & 0xFF) < (hash.bytes[i] & 0xFF))
					return -1;
				else if ((bytes[i] & 0xFF) > (hash.bytes[i] & 0xFF))
					return 1;
			}
			return 0;
		}

		@Override
		public boolean equals(Object hash) {
			return compareTo((HashValue) hash) == 0;
		}

		@Override
		public int hashCode() {
			return bytes[3] & 0xFF | (bytes[2] & 0xFF) << 8 | (bytes[1] & 0xFF) << 16 | (bytes[0] & 0xFF) << 24;
		}

	}

	public ComputeHashTask(Worker worker) throws NoSuchAlgorithmException, IOException {
		super(worker);
		String algorithm = Configuration.hashAlgorithm;
		if(algorithm != null){
			this.algorithm = algorithm.toUpperCase().replace("-", "");
			this.digest = MessageDigest.getInstance(algorithm);
		}
		
	}

	public void process(EvidenceFile evidence) {

		if (digest != null && evidence.getHash() == null) {
			
			InputStream in = null;
			try {
				in = evidence.getBufferedStream();
				byte[] hash = compute(in);
				evidence.setHash(getHashString(hash));
				
				// save(hash, IOUtil.getRelativePath(output,
				// evidence.getFile()));
				// save(hash, evidence.getPath());

			} catch (Exception e) {
				System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Erro ao calcular hash " + evidence.getPath() + "\t" + e);
				//e.printStackTrace();
				
			} finally {
				IOUtil.closeQuietly(in);
			}
		}
		

	}
	
	

	public byte[] compute(InputStream in) throws IOException {
		byte[] buf = new byte[1024 * 1024];
		int len;
		while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted())
			digest.update(buf, 0, len);

		byte[] hash = digest.digest();
		return hash;
	}

	public static String getHashString(byte[] hash) {
		StringBuilder result = new StringBuilder();
		for (byte b : hash)
			result.append(String.format("%1$02X", b));

		return result.toString();
	}

	public static final byte[] getHashBytes(String hash) {
		return DatatypeConverter.parseHexBinary(hash);
	}

	@Override
	public void init() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}



}
