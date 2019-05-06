package dpf.mt.gpinf.registro.model;

public class HiveCell {
	
	private int size;
	CellContent cellContent;
	
	public boolean isAllocated(){
		return size < 0;
	}
	
	public int getSize(){
		if (size<0){
			return size*(-1);
		}else{
			return size;
		}
	}

	public CellContent getCellContent() {
		return cellContent;
	}

	public void setSize(byte[] buffer) {
		size = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
	}

}
