package dpf.mt.gpinf.registro.model;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.PrimitiveIterator.OfInt;

import org.apache.commons.lang.ArrayUtils;

public class KeyValue extends CellContent {
	int dataOffset;

	public static final int REG_NONE = 0x00000000;
	public static final int REG_SZ = 0x00000001;
	public static final int REG_EXPAND_SZ = 0x00000002;
	public static final int REG_BINARY = 0x00000003;
	public static final int REG_DWORD = 0x00000004;
	public static final int REG_DWORD_BIGENDIAN = 0x00000005;
	public static final int REG_LINK = 0x00000006;
	public static final int REG_MULTI_SZ = 0x00000007;
	public static final int REG_RESOURCE_LIST = 0x00000008;
	public static final int REG_FULL_RESOURCE_DESCRIPTOR = 0x00000009;
	public static final int REG_RESOURCE_REQUIREMENTS_LIST = 0x0000000a;
	public static final int REG_QWORD = 0x0000000b;

	public KeyValue(RegistryFile reg, byte data[]){
		super(reg, data);
	}
	
	public String getValueName(){
		byte buffer[] = Arrays.copyOfRange(data, 2, 4);
		int nameLength = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8;
		return new String(Arrays.copyOfRange(data, 20, 20+nameLength));
	}

	public int getValueDatatype(){
		byte buffer[] = Arrays.copyOfRange(data, 12, 16);
		return (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
	}

	public byte[] getValueData(){
		byte buffer[] = Arrays.copyOfRange(data, 4, 8);
		int dataLength = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
		
		if(dataLength<0){//when most significant bit is set to 1 the data is less than 4 bytes in length and is stored in the dataoffset field
			dataLength = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0x7F) << 24; //ignora o bit mais significante
			buffer = Arrays.copyOfRange(data, 8, 8+dataLength);
			return buffer;
		}else{
			buffer = Arrays.copyOfRange(data, 8, 12);
			this.dataOffset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
			buffer = null;

			HiveCell cell = reg.getCell(this.dataOffset);
			if(cell.getCellContent() instanceof DataCell){
				DataCell data = (DataCell) cell.getCellContent();
				return Arrays.copyOf(data.data,dataLength);
			}else{
				if(cell.getCellContent() instanceof BigData) {
					BigData bd = (BigData) cell.getCellContent();

					buffer = bd.getData();
					//System.out.println("Erro: apontando para celula que nao contem dados:"+cell.getCellContent().getClass().toString());
				}
			}

			return buffer;
		}
	}

	public Date getValueDataAsPosixDate() throws RegistryFileException{
		int t;
		byte[] buffer = getValueData();
		t = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;

		return new Date( ((long)t)*((long)1000) );
		
	}
	
	public Date getValueDataAsDate() throws RegistryFileException{
		int type = getValueDatatype();
		if(type != REG_BINARY){
			throw new RegistryFileException("O tipo representado pelo valor não é do tipo binário");
		}

		byte [] buffer=getValueData();
		
		if(buffer.length != 16){
			throw new RegistryFileException("O dado representado pelo valor possui mais de 16 bytes, não sendo a representação válida de uma data.");
		}

		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, ((int) buffer[0] & 0xFF) + buffer[1] * 256);
		cal.set(Calendar.MONTH, buffer[2]);
		cal.set(Calendar.DAY_OF_MONTH, buffer[6]);
		cal.set(Calendar.HOUR, buffer[8]);
		cal.set(Calendar.MINUTE, buffer[10]);
		cal.set(Calendar.SECOND, buffer[12]);

		return cal.getTime();
	}

	public String getValueBDataAsString() {
		String result;
		byte[] buffer = getValueData();
		
		try {
			if(buffer.length==0){
				result="";
			}else{
				if((buffer.length>=2)&&(buffer[buffer.length-1]==0)&&(buffer[buffer.length-2]==0))
					buffer=Arrays.copyOf(buffer, buffer.length-2);
				result = new String(buffer, "UTF-16LE");
			}
		} catch (UnsupportedEncodingException e) {
			result = new String(buffer);
		}
		return result;
	}
	
	public static boolean containsIgnoreCase(String src, String what) {
	    final int length = what.length();
	    if (length == 0)
	        return true; // Empty string is contained

	    final char firstLo = Character.toLowerCase(what.charAt(0));
	    final char firstUp = Character.toUpperCase(what.charAt(0));

	    for (int i = src.length() - length; i >= 0; i--) {
	        // Quick check before calling the more expensive regionMatches() method:
	        final char ch = src.charAt(i);
	        if (ch != firstLo && ch != firstUp)
	            continue;

	        if (src.regionMatches(true, i, what, 0, length))
	            return true;
	    }

	    return false;
	}

	/** Formatador de datas. */
    private static final ThreadLocal<DateFormat> dateFormat =
        new ThreadLocal<DateFormat>() {
            protected DateFormat initialValue() {
            	return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            }
    	};
	
    public String getValueDataAsString() {
		int type = getValueDatatype();
    	return getValueDataAsString(type);
    }
    	
	public String getValueDataAsString(int type) {
		String result=null;
		byte[] buffer;
		int t;
		
		switch (type) {
		case REG_EXPAND_SZ:
		case REG_SZ:
			buffer = getValueData();
			try {
				if(buffer.length==0){
					result="";
				}else{
					if((buffer.length>=2)&&(buffer[buffer.length-1]==0)&&(buffer[buffer.length-2]==0))
						buffer=Arrays.copyOf(buffer, buffer.length-2);
					result = new String(buffer, "UTF-16LE");
				}
			} catch (UnsupportedEncodingException e) {
				result = new String(buffer);
			}
			break;
		case REG_MULTI_SZ:
			buffer = getValueData();
			result="";
			try {
				if(buffer.length>=0){
					if((buffer.length>=4)&&(buffer[buffer.length-1]==0)&&(buffer[buffer.length-2]==0)&&(buffer[buffer.length-3]==0)) {
						//provavelmente unicode
						int i = 0;
						do {
							int j = i;
							boolean achou=false;
							while((!achou)&&(j<buffer.length)) {
								j = ArrayUtils.indexOf(buffer, (byte) '\0', j+1);
								if(buffer[j+1]==0) {
									achou=true;
								}
							}
							
							result += new String(Arrays.copyOfRange(buffer, i, j+1), "UTF-16LE");
							result +="\n";
							i=j+3;
						}while(i<buffer.length-2);
					}else {
						//provavelmente ascii
						int i = 0;
						do {
							int j = i;
							j = ArrayUtils.indexOf(buffer, (byte) '\0', j+1);
							result += new String(Arrays.copyOfRange(buffer, i, j+1));
							result +="\n";
							i=j+3;
						}while(i<buffer.length);
					}
				}
			} catch (UnsupportedEncodingException e) {
				result = new String(buffer);
			}
			break;
		case REG_DWORD:
			buffer = getValueData();
			if(buffer.length>=4) {
				t = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
				result = Integer.toString(t);
				if(getValueName().toLowerCase().contains("time")||getValueName().toLowerCase().contains("last")) {
					try {
						result += " ("+dateFormat.get().format(getValueDataAsPosixDate())+")";				
					}catch(Exception e) {
						//igonra					
					}
				}
			}
			break;
		case REG_DWORD_BIGENDIAN:
			buffer = getValueData();
			t = (buffer[3] & 0xFF) | (buffer[2] & 0xFF) << 8 | (buffer[1] & 0xFF) << 16 | (buffer[0] & 0xFF) << 24;
			result = Integer.toString(t);
			break;
		case REG_QWORD:
			buffer = getValueData();
			if(buffer.length>=8) {
				long l = (long) (buffer[0] & 0xFF) | (long)(buffer[1] & 0xFF) << 8 | (long)(buffer[2] & 0xFF) << 16 | (long)(buffer[3] & 0xFF) << 24 | (long)(buffer[4] & 0xFF) << 32 | (long)(buffer[5] & 0xFF) << 40 | (long)(buffer[6] & 0xFF) << 48 | (long)(buffer[7] & 0xFF) << 56;
				result = Long.toString(l);
				if(getValueName().toLowerCase().contains("time")||getValueName().toLowerCase().contains("last")) {
					try {
						result += " ("+dateFormat.get().format(new Date((l)/(long)10000 - 11644473600000l))+")";				
					}catch(Exception e) {
						//igonra					
					}
				}
			}
			break;
			
		case REG_RESOURCE_LIST:
		case REG_RESOURCE_REQUIREMENTS_LIST:
		case REG_BINARY:
		case REG_NONE:
			buffer=getValueData();
			if(buffer!=null) {
				result="";			
				try {
					//verifica se o tamanho dos dados são de 16 bytes indicando que pode ser do tipo data 
					if(buffer.length==16) {
						//verifica o nome da chave para ver se ela tem alguma indicação que seu conteúdo seja uma data 
						if(containsIgnoreCase(getValueName(), "date")||containsIgnoreCase(getValueName(), "last")) {
							SimpleDateFormat sdf = new SimpleDateFormat();
							result = "(Data:"+sdf.format(getValueDataAsDate()+")");
						}
					}
					
				}catch(RegistryFileException e){
				}catch(IllegalArgumentException e){
					//ignora exceção
				}finally {
					StringBuffer res = new StringBuffer();
					
					for(int i=0; i<buffer.length; i++){
						res.append(" 0x"+String.format("%02X", buffer[i]));
					}
					
					result=result+" - "+res.toString();
				}			
			}

			if(buffer.length==12) {
				if(getValueName().toLowerCase().contains("time")||getValueName().toLowerCase().contains("last")) {
					try {
						result += "("+dateFormat.get().format(getValueDataAsDate())+")";
					}catch(Exception e) {
						//ignore
					}
				}
			}
			
		default:
			break;
		}
		
		return result;
	} 
	
}