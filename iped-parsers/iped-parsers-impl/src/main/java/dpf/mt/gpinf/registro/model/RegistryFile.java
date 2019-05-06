package dpf.mt.gpinf.registro.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/*
 * Esta biblioteca foi implementada seguindo a especificação publicada em:
 * 
 * https://github.com/msuhanov/regf/blob/master/Windows%20registry%20file%20format%20specification.md
 * 
 */

public class RegistryFile {
	File file;
	//StreamSource ss;
	int rootCellOffset;
	HashMap <Integer, HiveCell> readCells;
	
	public RegistryFile(File file){
		this.file = file;
	}
	
	public File getFile() {
		return file;
	}
	
	public void load() throws IOException{
		FileInputStream fis = new FileInputStream(file);
		int pos=0;

		readCells = new HashMap<Integer, HiveCell>(); 

		Registry reg = new Registry();
		fis.read(reg.fileHeader);
		
		byte[] buffer = Arrays.copyOfRange(reg.fileHeader, 36, 40);
		rootCellOffset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
		buffer = null;

		boolean hbexite=true;
		while(hbexite){
			HiveBin hb = new HiveBin();
			pos+=fis.read(hb.header);//le o cabecalho do hivebean
			String sig = new String(Arrays.copyOf(hb.header, 4));
			if(!sig.equals("hbin")){
				hbexite=false; //fim do arquivo, e consequentemente do loop
				continue;
			}

			buffer = Arrays.copyOfRange(hb.header, 4, 8);
			hb.offset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
			buffer = null;
			buffer = Arrays.copyOfRange(hb.header, 8, 12);
			hb.size = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
			buffer = null;

			int hiveDataReadCount = 32; //inicia com o tamanho do cabecalho ja lido + offset
			while(hiveDataReadCount<hb.size){
				HiveCell cell = readCell(fis);
				readCells.put(new Integer(pos), cell);

				hiveDataReadCount+=cell.getSize();
				pos+=cell.getSize();
			}
		}
		
		fis.close();
	}

	public HiveCell readCell(InputStream fis) throws IOException{
		HiveCell cell = new HiveCell();

		byte[] buffer = new byte[4];
		fis.read(buffer);
		cell.setSize(buffer);
		buffer = new byte[cell.getSize()-4];
		fis.read(buffer);

		String celltype = new String(Arrays.copyOf(buffer, 2));
		switch (celltype) {
		case "nk":
			cell.cellContent = new KeyNode(this, buffer);
			break;
		case "vk":
			cell.cellContent = new KeyValue(this, buffer);
			break;
		case "db":
			cell.cellContent = new BigData(this, buffer);
			break;
		case "lf":
		case "lh":
			cell.cellContent = new SubKeysList(this, buffer);
			break;
		case "ri":
			cell.cellContent = new IndexRoot(this, buffer);
			break;
		default:
			cell.cellContent = new DataCell(this, buffer);
			break;
		}		

		return cell;
	}
	
	public HiveCell getCell(int offset){
		return readCells.get(new Integer(offset));		
	}
	
	public HiveCell getRootCell(){
		return getCell(rootCellOffset);		
	}
	
	public KeyNode findKeyNode(String path){
		KeyNode k = (KeyNode) getRootCell().getCellContent();
		StringTokenizer st = new StringTokenizer(path, "/\\");
		boolean achou = true;
		
		try{
			while(true){
				String tok=st.nextToken();
				ArrayList<KeyNode> ks = k.getSubKeys();
				achou=false;
				for (int i = 0; i < ks.size(); i++) {
					KeyNode sub = ks.get(i);
					
					if(tok.equals(sub.getKeyName())){
						achou = true;
						k = ks.get(i);
						break;
					}
				}
				if(!achou) break;
			}
		}catch(NoSuchElementException e){
		}
		
		if(achou){
			return k;
		}else{
			return null;
		}
	}

}
