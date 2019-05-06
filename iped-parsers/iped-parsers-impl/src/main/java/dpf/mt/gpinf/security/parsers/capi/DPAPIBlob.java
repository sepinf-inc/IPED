package dpf.mt.gpinf.security.parsers.capi;

public class DPAPIBlob {
	int version;
	GUID guidProvider;
	int masterKeyVersion;
	GUID guidMasterKey;
	byte[] flags = new byte[4];//DWORD

	int descriptionLen;
	String description;

	int algCrypt;
	int algCryptLen;

	int saltLen;
	byte[] salt;

	int hmacKeyLen;
	byte[] hmacKey;

	int algHash;
	int algHashLen;

	int hmac2KeyLen;
	byte[] hmac2Key;
	
	int dataLen;
	byte[] data;
	
	int signLen;
	byte[] sign;

}
