package dpf.sp.gpinf.indexer.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * Classe auxiliar que realiza leitura de propriedades em arquivos codificados
 * em UTF-8. A implementação padrão do Java trata apenas ISO 8859-1 para
 * arquivos simples ("Chave = Valor").
 *
 * @author Wladimir
 *
 */
public class UTF8Properties extends Properties {

    private static final long serialVersionUID = -8198271272010610933L;

    public synchronized void load(File file) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")); //$NON-NLS-1$
        String str = null;
        while ((str = in.readLine()) != null) {
            if (str.isEmpty() || str.charAt(0) == '#') {
                continue;
            }
            int pos = str.indexOf('=');
            while (pos > 0 && str.charAt(pos - 1) == '\\') {
                pos = str.indexOf('=', pos + 1);
            }
            if (pos > 0) {
                String key = str.substring(0, pos).replace("\\=", "=").replace("\\:", ":").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                String val = str.substring(pos + 1).replace("\\=", "=").replace("\\:", ":").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                super.put(key, val);
            }
        }
        in.close();
    }

    public synchronized void store(File file) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); //$NON-NLS-1$
        Object[] keys = this.keySet().toArray();
        Arrays.sort(keys);
        for (Object key : keys) {
            writer.write(key.toString().replace("=", "\\=") + " = " + this.get(key).toString().replace("=", "\\=")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            writer.write("\r\n"); //$NON-NLS-1$
        }
        writer.close();
    }
}
