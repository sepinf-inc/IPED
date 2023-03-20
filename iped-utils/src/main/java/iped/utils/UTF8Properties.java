package iped.utils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    boolean cumulative = false;
    String cumulativeSeparator=";";
    
    LinkedHashSet<Object> insertionOrder = new LinkedHashSet<Object>();
    HashMap<Object, String> comments = new HashMap<Object,String>();

    public synchronized void load(File file) throws IOException {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")); //$NON-NLS-1$
            String str = null;
            String lastComment="";
            boolean lastCommentSaved=false;
            while ((str = in.readLine()) != null) {
                if (str.trim().isEmpty() || str.trim().charAt(0) == '#') {
                    if(!str.trim().isEmpty()) {
                        if(lastCommentSaved) {
                            lastComment="";
                            lastCommentSaved=false;
                        }
                        lastComment+=str.trim().substring(1)+"\n";
                    }
                    continue;
                }
                int pos = str.indexOf('=');
                while (pos > 0 && str.charAt(pos - 1) == '\\') {
                    pos = str.indexOf('=', pos + 1);
                }
                if (pos > 0) {
                    String key = str.substring(0, pos).replace("\\=", "=").replace("\\:", ":").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    String val = str.substring(pos + 1).replace("\\=", "=").replace("\\:", ":").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    if(isCumulative()) {
                        String valant = super.getProperty(key);
                        if(valant!=null) {
                            val = valant + cumulativeSeparator + val;
                        }
                        super.put(key, val);
                    }else {
                        super.put(key, val);
                        comments.put(key, lastComment);
                        lastCommentSaved=true;
                    }

                    insertionOrder.add(key);
                }
            }
            in.close();
        }catch (FileNotFoundException e) {
            //ignores
        }
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

    public synchronized void storeAndPreserve(File file) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); //$NON-NLS-1$
        Object[] keys = this.keySet().toArray();
        Arrays.sort(keys);
        for (Object key : keys) {
            writer.write(key.toString().replace("=", "\\=") + " = " + this.get(key).toString().replace("=", "\\=")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            writer.write("\r\n"); //$NON-NLS-1$
        }
        writer.close();
    }

    public synchronized void saveOnFile(File fileToBeChanged) throws IOException {
        BufferedReader reader = null;
        FileWriter writer = null;
        Charset UTF8 = Charset.forName("UTF-8");

        try{
            reader = new BufferedReader(new FileReader(fileToBeChanged, UTF8));

            //Read all file lines and put on a StringBuffer
            String line = reader.readLine();
            StringBuffer fileContent = new StringBuffer();
            while (line != null){
                fileContent.append(line).append(System.lineSeparator());
                line = reader.readLine();
            }

            //Iterate over all properties key ant set value on filecontent
            String changedContent = fileContent.toString();
            for(Object key : this.keySet()) {
                //Try to find the propertie Key
                Matcher matcher = getPropertieKeyRegexPattern(key.toString()).matcher(changedContent);
                if (!matcher.find())
                    continue;
                //If propertie key exists, change the propertie value
                //Whe need to use Matcher.quoteReplacement to prevent
                final String subst = "$1= " + Matcher.quoteReplacement( String.valueOf(this.get(key)) );
                changedContent = matcher.replaceAll(subst);
            }

            //save the file content
            writer = new FileWriter(fileToBeChanged, UTF8);
            writer.write(changedContent);
        }finally{
            try{
                //Close all resources
                if(reader != null)
                    reader.close();
                if(writer != null){
                    writer.flush();
                    writer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public synchronized void enableOrDisablePropertie(File fileToBeChanged, String propertieName, Boolean isDisable){
        String fileContent = "";
        BufferedReader reader = null;
        FileWriter writer = null;

        try{
            reader = new BufferedReader(new FileReader(fileToBeChanged, StandardCharsets.UTF_8));

            //Read all file content and set on fileContent var
            String line = reader.readLine();
            while (line != null){
                fileContent = fileContent + line + System.lineSeparator();
                line = reader.readLine();
            }

            String conteudoModificado = fileContent ;
            //is option is to disablePropertie
            if (isDisable){
                String chaveComentada = "#" + propertieName;
                //analisa se a chave ja esta comentada para que não adicione mais um #
                if( ! fileContent.contains(chaveComentada) )
                    conteudoModificado = fileContent.replaceAll(propertieName, chaveComentada);
            } else {
                conteudoModificado = fileContent.replaceAll("#"+propertieName, propertieName);
            }

            //Save modification on file
            writer = new FileWriter(fileToBeChanged, StandardCharsets.UTF_8);
            writer.write(conteudoModificado);
        }catch (IOException e){
            e.printStackTrace();
        }finally{
            try{
                //Fechando os recursos
                if(reader != null)
                    reader.close();
                if(writer != null){
                    writer.flush();
                    writer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private Pattern getPropertieKeyRegexPattern(String key){
        //Regex to find a propertie key on a String
        final String regex = "(^\\s*#*\\s*"+ key +"\\s*)=(.*)";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    }

    public boolean isCumulative() {
        return cumulative;
    }

    public void setCumulative(boolean cumulative) {
        this.cumulative = cumulative;
    }

    public String getCumulativeSeparator() {
        return cumulativeSeparator;
    }

    public void setCumulativeSeparator(String cumulativeSeparator) {
        this.cumulativeSeparator = cumulativeSeparator;
    }

    public Set<Object> orderedKeySet() {
        if(insertionOrder.size()!=this.size()) {
            return super.keySet();
        }
        return insertionOrder;
    }
    
    public String getComments(Object key) {
        return comments.get(key);
    }

}
