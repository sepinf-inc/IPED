package dpf.mt.gpinf.security.parsers.capi;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CapiBlob {
    int version;
    int nameLen;
    int pubKeyLen;
    int privKeyLen;
    int exPubKeyLen;
    int exPrivKeyLen;
    int hashLen;
    int siExportFlagLen;
    int exExportFlagLen;
    String name;

    byte[] hash;
    byte[] pubKey;
    DPAPIBlob privKeyBlob;
    DPAPIBlob siExportFlagBlob;
    byte[] exPubKey;
    DPAPIBlob exPrivKeyBlob;
    DPAPIBlob exExportFlagBlob;

    public CapiBlob(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(is);

        byte[] inteiro = new byte[4];
        dis.read(inteiro);
        this.version = toInt(inteiro);
        dis.read(inteiro);// ignora
        dis.read(inteiro);
        this.nameLen = toInt(inteiro);
        dis.read(inteiro);
        this.pubKeyLen = toInt(inteiro);
        dis.read(inteiro);
        this.privKeyLen = toInt(inteiro);
        dis.read(inteiro);
        this.exPubKeyLen = toInt(inteiro);
        dis.read(inteiro);
        this.exPrivKeyLen = toInt(inteiro);
        dis.read(inteiro);
        this.hashLen = toInt(inteiro);
        dis.read(inteiro);
        this.siExportFlagLen = toInt(inteiro);
        dis.read(inteiro);
        this.exExportFlagLen = toInt(inteiro);

        byte[] str = new byte[this.nameLen];
        dis.read(str);
        this.name = new String(str);
        this.hash = new byte[this.hashLen];
        dis.read(this.hash);

        if (this.pubKeyLen > 0) {
            this.pubKey = new byte[this.pubKeyLen];
            dis.read(this.pubKey);
        }

        if (this.privKeyLen > 0) {
            this.privKeyBlob = createDpapiBlob(dis);
        }

        if (this.siExportFlagLen > 0) {
            this.siExportFlagBlob = createDpapiBlob(dis);
        }

        if (this.exPubKeyLen > 0) {
            this.exPubKey = new byte[this.exPubKeyLen];
            dis.read(this.exPubKey);
        }

        if (this.exPrivKeyLen > 0) {
            this.exPrivKeyBlob = createDpapiBlob(dis);
        }

        if (this.exExportFlagLen > 0) {
            this.exExportFlagBlob = createDpapiBlob(dis);
        }
    }

    static public int toInt(byte[] buf) {
        return (buf[0] & 0xff) << 0 | (buf[1] & 0xff) << 8 | (buf[2] & 0xff) << 16 | (buf[3] & 0xff) << 24;
    }

    static public DPAPIBlob createDpapiBlob(DataInputStream dis) throws IOException {
        DPAPIBlob db = new DPAPIBlob();

        byte[] inteiro = new byte[4];
        dis.read(inteiro);
        db.version = toInt(inteiro);

        db.guidProvider = new GUID();
        dis.read(db.guidProvider.Data1);
        dis.read(db.guidProvider.Data2);
        dis.read(db.guidProvider.Data3);
        dis.read(db.guidProvider.Data4);

        dis.read(inteiro);
        db.masterKeyVersion = toInt(inteiro);

        db.guidMasterKey = new GUID();
        dis.read(db.guidMasterKey.Data1);
        dis.read(db.guidMasterKey.Data2);
        dis.read(db.guidMasterKey.Data3);
        dis.read(db.guidMasterKey.Data4);

        dis.read(db.flags);

        dis.read(inteiro);
        db.descriptionLen = toInt(inteiro);
        byte[] str = new byte[db.descriptionLen];
        dis.read(str);
        db.description = new String(str);

        dis.read(inteiro);
        db.algCrypt = toInt(inteiro);
        dis.read(inteiro);
        db.algCryptLen = toInt(inteiro);

        dis.read(inteiro);
        db.saltLen = toInt(inteiro);
        db.salt = new byte[db.saltLen];
        dis.read(db.salt);

        dis.read(inteiro);
        db.hmacKeyLen = toInt(inteiro);
        db.hmacKey = new byte[db.hmacKeyLen];
        dis.read(db.hmacKey);

        dis.read(inteiro);
        db.algHash = toInt(inteiro);
        dis.read(inteiro);
        db.algHashLen = toInt(inteiro);

        dis.read(inteiro);
        db.hmac2KeyLen = toInt(inteiro);
        db.hmac2Key = new byte[db.hmac2KeyLen];
        dis.read(db.hmac2Key);

        dis.read(inteiro);
        db.dataLen = toInt(inteiro);
        db.data = new byte[db.dataLen];
        dis.read(db.data);

        dis.read(inteiro);
        db.signLen = toInt(inteiro);
        db.sign = new byte[db.signLen];
        dis.read(db.sign);

        return db;
    }

    public int getVersion() {
        return version;
    }

    public int getNameLen() {
        return nameLen;
    }

    public int getPubKeyLen() {
        return pubKeyLen;
    }

    public int getPrivKeyLen() {
        return privKeyLen;
    }

    public int getExPubKeyLen() {
        return exPubKeyLen;
    }

    public int getExPrivKeyLen() {
        return exPrivKeyLen;
    }

    public int getHashLen() {
        return hashLen;
    }

    public int getSiExportFlagLen() {
        return siExportFlagLen;
    }

    public int getExExportFlagLen() {
        return exExportFlagLen;
    }

    public String getName() {
        return name;
    }

    public byte[] getHash() {
        return hash;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public DPAPIBlob getPrivKeyBlob() {
        return privKeyBlob;
    }

    public DPAPIBlob getSiExportFlagBlob() {
        return siExportFlagBlob;
    }

    public byte[] getExPubKey() {
        return exPubKey;
    }

    public DPAPIBlob getExPrivKeyBlob() {
        return exPrivKeyBlob;
    }

    public DPAPIBlob getExExportFlagBlob() {
        return exExportFlagBlob;
    }

}
