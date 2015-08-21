package gpinf.dev.data;

import java.io.IOException;
import java.io.InputStream;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

public class SleuthEvidenceFile extends EvidenceFile {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7724121953628012018L;
	private AbstractFile sleuthFile;
	private String sleuthId;

	@Override
	protected InputStream createInputStream() throws IOException {
		if (sleuthFile != null)
			return new ReadContentInputStream(sleuthFile);
		return super.createInputStream();
	}

	/**
	 * Cria um EvidenceFile para ser usado como subitem deste EvidenceFile.
	 * 
	 * @param useSameData
	 *            caso verdadeiro, utiliza a mesma fonte de dados do pai.
	 * @return
	 */
	public EvidenceFile createSubitem(boolean useSameData) {
		SleuthEvidenceFile subitem = new SleuthEvidenceFile();

		int parentId = getId();
		subitem.setParentId(Integer.toString(parentId));
		subitem.addParentIds(getParentIds());
		subitem.addParentId(parentId);

		if (useSameData) {
			subitem.setSleuthFile(getSleuthFile());
			subitem.setSleuthId(getSleuthId());
		}

		return subitem;
	}

	/**
	 * 
	 * @return o objeto do Sleuthkit que representa o item
	 */
	public AbstractFile getSleuthFile() {
		return sleuthFile;
	}

	/**
	 * 
	 * @return o id do item no Sleuthkit
	 */
	public String getSleuthId() {
		return sleuthId;
	}

	/**
	 * @param sleuthFile
	 *            objeto que representa o item no sleuthkit
	 */
	public void setSleuthFile(AbstractFile sleuthFile) {
		this.sleuthFile = sleuthFile;
	}

	/**
	 * @param sleuthId
	 *            id do item no sleuthkit
	 */
	public void setSleuthId(String sleuthId) {
		this.sleuthId = sleuthId;
	}

}
