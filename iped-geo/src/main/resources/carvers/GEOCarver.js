function getLengthFromHeader(parentEvidence, header) {
	  is = parentEvidence.getStream();

	  is.seek(header.getOffset()+1);

	  var ByteArray = Java.type("byte[]");
	  var lenlenb = new ByteArray(1);

	  is.read(lenlenb);

	  lenlen = lenlenb[0] & 0x7F;

	  var lenb = new ByteArray(lenlen);
	  is.read(lenb);

	  len = 0;
	  valor = 0;
	  for (j = 0; j < lenlen; j++) {
	   	valor = lenb[j] & 0xff;
	   	valor = valor << (8 * (lenlen-j-1));
	   	len |= valor;
      }

	  return len+4;// soma o tamanho do cabeçalho
}

/*
function validateCarvedObject(parentEvidence, header, length){
	var is = null;

	try{
		var is = parentEvidence.getStream();
		
		var ByteArray = Java.type("byte[]");
		var buf = new ByteArray(length);
		
		is.seek(header.getOffset());
		is.read(buf);
    	return parse(buf); //tenta interpretar o certificado com o tamanho do cabecalho incluso
	}catch(e){
		var InvalidCarvedObjectException = Java.type("dpf.mt.gpinf.carving.InvalidCarvedObjectException")
    	var ex = new InvalidCarvedObjectException(e);
		throw ex;		
    }finally{
    	if(is != null){
    		is.close();
    	}
    }

    return null;
}

function parse(buff){
	try{
		var CertificateFactory = Java.type("java.security.cert.CertificateFactory");
		cf = CertificateFactory.getInstance("X.509");

		var ByteArrayInputStream = Java.type("java.io.ByteArrayInputStream");
		certStream = new ByteArrayInputStream(buff);
		cert = cf.generateCertificate(certStream);
		
		return cert;
	}catch(e){
		try{
			//se não for um certificado válido tenta verificar se é um keystore
			var KeyStore = Java.type("java.security.KeyStore");
			var p12 = KeyStore.getInstance("PKCS12");
			var ByteArrayInputStream = Java.type("java.io.ByteArrayInputStream");
			keystoreStream = new ByteArrayInputStream(buff);
			p12.load(keystoreStream,"123");
			return p12;
		}catch(e){
			if(e.message.includes("password")){
				//o erro foi de senha invalida mas a stream foi aparentemente recuperada.
				return p12;
			}else{
				var IOException = Java.type("java.io.IOException");
				throw new IOException(e);
			}
		}
	}
}
*/
