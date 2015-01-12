package gpinf.dev.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe que define dados pertencentes a uma grupo de arquivos de evidência,
 * que pode ser uma categoria (bookmark) sob a qual os arquivos extraídos são
 * organizados, ou alguma outra forma de agrupamento dos arquivos de evidência
 * do caso.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class FileGroup implements Serializable {
	/** Identificador utilizado para serialização da classe. */
	private static final long serialVersionUID = 1974121400190027L;

	/** Nome do agrupamento. */
	private final String name;

	/** Descrição do agrupamento. */
	private final String descr;

	/**
	 * Nome do arquivo no qual os arquivos pertencentes a este bookmark
	 * (categoria) estão listados.
	 */
	private final String fileName;

	/** Arquivos de evidência associados a este grupo. */
	private final List<EvidenceFile> evidenceFiles = new ArrayList<EvidenceFile>();

	/**
	 * @param name
	 *            nome do bookmark
	 * @param descr
	 *            descrição do bookmark
	 * @param fileName
	 *            nome do arquivo no qual os arquivos de book mark estão
	 *            listados
	 */
	public FileGroup(String name, String descr, String fileName) {
		this.name = name;
		this.descr = descr;
		this.fileName = fileName;
	}

	/**
	 * Adiciona um arquivo de evidência.
	 * 
	 * @param evidenceFile
	 *            arquivo a ser adicionado
	 */
	/*
	 * public void addEvidenceFile(EvidenceFile evidenceFile) {
	 * evidenceFiles.add(evidenceFile); }
	 */

	/**
	 * Obtém lista de arquivos de evidência associados a este agrupamento.
	 * 
	 * @return lista não modificável de arquivos.
	 */
	/*
	 * public List<EvidenceFile> getEvidenceFiles() { return
	 * Collections.unmodifiableList(evidenceFiles); }
	 */

	/**
	 * @return descrição do agrupamento
	 */
	public String getDescr() {
		return descr;
	}

	/**
	 * Obtém o nome do arquivo do agrupamento.
	 * 
	 * @return o nome do arquivo que lista os arquivos pertencentes a este
	 *         agrupamento
	 */
	public String getFileName() {
		return fileName;
	}

	/**
	 * Obtém o nome do agrupamento.
	 * 
	 * @return nome do agrupamento
	 */
	public String getName() {
		return name;
	}

	/**
	 * Retorna representação em texto do agrupamento.
	 */
	@Override
	public String toString() {
		return "<html><b>" + name + " </b>(" + evidenceFiles.size() + ")</html>";
	}
}
