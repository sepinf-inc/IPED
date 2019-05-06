package dpf.mt.gpinf.registro.model;

import java.util.ArrayList;
import java.util.Arrays;

public class BigData extends CellContent{
	
	public BigData(RegistryFile reg, byte[] data) {
		super(reg, data);
	}

	int[] offsets;
	
	public byte[] getData(){
		byte buffer[] = null;
		ArrayList<byte[]> saida = new ArrayList<byte[]>();
		byte result[] = null;
		int size=0;

		buffer = Arrays.copyOfRange(data, 2, 4);
		int numSegments = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8;
		buffer = null;
		offsets = new int[numSegments];
		buffer = Arrays.copyOfRange(data, 4, 8);
		int offset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
		buffer = null;

		HiveCell cellList = reg.getCell(offset);

		if(cellList.getCellContent() instanceof DataCell){
			DataCell listSegments = (DataCell) cellList.getCellContent();
			for (int i = 0; i < numSegments; i++) {
				buffer = Arrays.copyOfRange(listSegments.data, i*4, i*4 + 4);
				offset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
				buffer = null;

				HiveCell cellData = reg.getCell(offset);
				saida.add(cellData.getCellContent().data);
				size+=cellData.getCellContent().data.length;
			}
			result = new byte[size];
			int pos=0;
			for(int i=0; i<saida.size();i++){
				byte[] b = saida.get(i);
				for(int j=0;j<b.length;j++){
					result[pos]=b[j];
					pos++;
				}
			}
			
		}else{
			System.out.println("Erro: not a data cell.");
		}

		return result;
	}
	
}
