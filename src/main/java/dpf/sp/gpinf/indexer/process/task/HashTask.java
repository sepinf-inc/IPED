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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Properties;

import javax.xml.bind.DatatypeConverter;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IOUtil;
import gpinf.dev.data.EvidenceFile;

/**
 * Classe para calcular e manipular hashes.
 */
public class HashTask extends AbstractTask{

	private static Logger LOGGER = LoggerFactory.getLogger(HashTask.class);
	
	public static String EDONKEY = "edonkey";
	public static String MD5 = "md5";
	public static String SHA1 = "sha-1";
	public static String SHA256 = "sha-256";
	public static String SHA512 = "sha-512";
	
	private HashMap<String, MessageDigest> digestMap = new LinkedHashMap<String, MessageDigest>();
	
	public HashTask(Worker worker){
		super(worker);
	}
	
	@Override
	public void init(Properties confProps, File confDir) throws Exception {
		String value = confProps.getProperty("hash");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty()){
			for(String algorithm : value.split(";")){
				algorithm = algorithm.trim();
				MessageDigest digest = null;
				if(!algorithm.equalsIgnoreCase(EDONKEY))
					digest = MessageDigest.getInstance(algorithm.toUpperCase());
				else
					digest = MessageDigest.getInstance("MD4", new BouncyCastleProvider());
				digestMap.put(algorithm, digest);
			}
			
		}
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

	public void process(EvidenceFile evidence) {
		
		if(evidence.isQueueEnd())
			return;
		
		boolean defaultHash = true;
		for(String algo : digestMap.keySet()){
			
			if(evidence.getExtraAttribute(algo) != null)
				continue;
			
			InputStream in = null;
			try {
				in = evidence.getBufferedStream();
				byte[] hash;
				if(!algo.equals(EDONKEY))
					hash = compute(digestMap.get(algo), in);
				else
					hash = computeEd2k(in);
				
				String hashString = getHashString(hash);
				
				evidence.setExtraAttribute(algo, hashString);
				
				if(defaultHash)
					evidence.setHash(hashString);

			} catch (Exception e) {
				LOGGER.warn("{} Erro ao calcular hash {}\t{}", Thread.currentThread().getName(), evidence.getPath(), e.toString());
				//e.printStackTrace();
				
			} finally {
				IOUtil.closeQuietly(in);
			}
			
			defaultHash = false;
		}

	}
	
	public byte[] compute(MessageDigest digest, InputStream in) throws IOException {
		byte[] buf = new byte[1024 * 1024];
		int len;
		while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted())
			digest.update(buf, 0, len);

		byte[] hash = digest.digest();
		return hash;
	}
	
	private byte[] computeEd2k(InputStream in) throws IOException{
		MessageDigest md4 = digestMap.get(EDONKEY);
		int CHUNK_SIZE = 9500 * 1024;
	    byte[] buffer = new byte[8192];
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    int len = 0, chunk = 0, total = 0;
	    while ((len = in.read(buffer)) != -1) {
	        if (chunk + len >= CHUNK_SIZE) {
	            int offset = CHUNK_SIZE - chunk;
	            md4.update(buffer, 0, offset);
	            out.write(md4.digest());
	            chunk = len - offset;
	            md4.update(buffer, offset, chunk);
	        } else {
	            md4.update(buffer, 0, len);
	            chunk += len;
	        }
	        total += len;
	    }
	    if(total == 0 || total % CHUNK_SIZE != 0)
	    	out.write(md4.digest());

	    if (out.size() > md4.getDigestLength()) {
	        md4.update(out.toByteArray());
	        out.reset();
	        out.write(md4.digest());
	    }
	    
	    return out.toByteArray();
	}

	public static String getHashString(byte[] hash) {
		StringBuilder result = new StringBuilder();
		for (byte b : hash)
			result.append(String.format("%1$02X", b));

		return result.toString();
	}
	
	public static class HashValue implements Comparable<HashValue>, Serializable{

		private static final long serialVersionUID = 1L;
		
		byte[] bytes;
		
		public HashValue(String hash) {
			bytes = DatatypeConverter.parseHexBinary(hash);
		}
		
		public String toString(){
			return DatatypeConverter.printHexBinary(bytes);
		}

		@Override
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

}
