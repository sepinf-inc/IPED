package sef.mg.laud.ad1extractor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
*
* @author guilherme.dutra
*/
public class FileHeader{

    public long endereco_prox_objeto = 0L;
    public long endereco_filho_objeto = 0L;
    public long objeto_PC_fim_parcial = 0L;
    public long objeto_PC_ini_parcial = 0L;
    public long objetoTamanhoBytes = 0L ;
    public long objeto_tipo = 0L ;
    public long nome_objeto_tam = 0L ;  
    public String objeto_nome = "";
    public long objeto_pedacos_tam = 0L;
    public String caminho = "";
    public List<Pedaco> pedacosList = null;
    public List<Propriedade> propriedadesList = null;
    
    SimpleDateFormat simpleDateFormat = null; 
    
    public void setObjetoTamanhoBytes(long tam){    
        this.objetoTamanhoBytes = tam;
    }
    
    public FileHeader (){
        pedacosList = new ArrayList<>();
        propriedadesList = new ArrayList<>();
        simpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss"); 
    }

    public Date getATime(){
    
        if (propriedadesList != null && propriedadesList.size() > 2){
            try {
                return simpleDateFormat.parse(propriedadesList.get(2).getValor());
                
            } catch (ParseException e) {
                System.out.println("Error reading ATime from " + getFilePath() + " " + e.toString());
            }
        }
    
        return null;
    }
    
    public Date getCTime(){
    
        if (propriedadesList != null && propriedadesList.size() > 3){
            try {
                return simpleDateFormat.parse(propriedadesList.get(3).getValor());
                
            } catch (ParseException e) {
                System.out.println("Error reading CTime from " + getFilePath() + " " + e.toString());
            }
        }
    
        return null;
    }

    public Date getMTime(){
    
        if (propriedadesList != null && propriedadesList.size() > 4){
            try {
                return simpleDateFormat.parse(propriedadesList.get(4).getValor());
            } catch (ParseException e) {
                System.out.println("Error reading MTime from " + getFilePath() + " " + e.toString());
            }
        }
    
        return null;
    }   

     public boolean isDirectory(){
     
        boolean retorno = false;
     
        if (this.objeto_tipo == 5 )
            retorno = true;
     
        return retorno;
     
     }
     
     public String getFilePath(){
        if (this.isDirectory()) 
            return caminho;     
        else
            return caminho + "/" + objeto_nome;     
     }
     
     public String getFileName(){
        return objeto_nome;     
     }   
     
     public boolean isEncrypted(){
     
        return false;
     
     }

     public void adicionaPedaco(long ini, long fim){
     
        pedacosList.add(new Pedaco(ini,fim));
     
     }

}
