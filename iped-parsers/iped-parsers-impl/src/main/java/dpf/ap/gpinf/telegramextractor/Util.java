package dpf.ap.gpinf.telegramextractor;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

import javax.xml.bind.DatatypeConverter;

import java.nio.ByteBuffer;
public class Util {
	 private static String byteArrayToHex(byte[] a ) {
		 StringBuilder sb = new StringBuilder(a.length * 2);
	        for (byte b: a) {
	            sb.append(String.format("%02x", b ));
	        }
	        return sb.toString();
	    }
	public static String base64ToHex(String str ) {
        //ja é o hash
        if(str.length()==64 && str.matches("^[a-f\\d]+$")){
            return str;
        }
        return byteArrayToHex(DatatypeConverter.parseBase64Binary(str));
    }
	
	
	public static String hashFile(InputStream is) {
        String hash = null;
        
        try {
        	MessageDigest digest = MessageDigest.getInstance("SHA-256");

        	
            
            byte[] buffer=new byte[255];
            while (is.read(buffer) != -1) {
                digest.update(buffer);
            }
            hash = byteArrayToHex( digest.digest());
            is.close();
        } catch (Exception ex ) {
            //log erros
        }

        return hash;
    }

}
