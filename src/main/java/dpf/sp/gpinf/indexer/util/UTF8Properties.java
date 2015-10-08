package dpf.sp.gpinf.indexer.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Properties;

/**
 * Classe auxiliar que realiza leitura de propriedades em arquivos codificados em UTF-8.
 * A implementação padrão do Java trata apenas ISO 8859-1 para arquivos simples ("Chave = Valor").
 * @author Wladimir
 *
 */
public class UTF8Properties extends Properties {
    private static final long serialVersionUID = -8198271272010610933L;

    public synchronized void load(File file) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String str = null;
        while ((str = in.readLine()) != null) {
            if (str.isEmpty() || str.charAt(0) == '#') continue;
            int pos = str.indexOf('=');
            if (pos > 0) super.put(str.substring(0, pos).trim(), str.substring(pos + 1).trim());
        }
        in.close();
    }
    
    public synchronized void store(File file) throws IOException {
    	OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        for(Map.Entry<Object,Object> entry : this.entrySet()){
        	writer.write(entry.getKey() + " = " + entry.getValue());
        	writer.write("\r\n");
        }
        writer.close();
    }
}
