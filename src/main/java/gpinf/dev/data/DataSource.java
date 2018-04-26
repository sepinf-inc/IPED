package gpinf.dev.data;

import java.io.File;
import java.util.UUID;

/**
 * Representa uma fonte de dados, como imagem de dispositivo, disco físico, pasta ou relatório de outra ferramenta. 
 * 
 * @author Nassif
 *
 */
public class DataSource {
	
	private File sourceFile;
	private String name;
	private UUID uuid;
	
	public DataSource() {
	}
	
	public DataSource(File source){
		this.sourceFile = source;
		this.uuid = UUID.randomUUID();
	}
		
	public String getUUID(){
		return uuid.toString();
	}
	
	public void setUUID(String uuid){
		this.uuid = UUID.fromString(uuid);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public File getSourceFile() {
		return sourceFile;
	}

}
