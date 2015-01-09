package gpinf.dev.filetypes;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.List;

public class AttachmentFileType extends EvidenceFileType {

	/**
	 * Implementação da classe base utilizada para anexos de email.
	 * 
	 * @author Nassif (GPINF/SP)
	 */
	private static final long serialVersionUID = 1839778399524523300L;

	@Override
	public String getLongDescr() {
		return "Anexo de Email";
	}

	@Override
	public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles) {
		// TODO Auto-generated method stub

	}
	/*
	 * @Override public Icon getIcon() { // TODO Auto-generated method stub
	 * return null; }
	 */

}
