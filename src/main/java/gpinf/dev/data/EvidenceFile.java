package gpinf.dev.data;

import gpinf.dev.filetypes.EvidenceFileType;
import gpinf.util.FormatUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.util.LimitedInputStream;

/**
 * Classe que define um arquivo de evidência, que é um arquivo do caso,
 * acompanhado de todas sua propriedades disponíveis. Algumas propriedades,
 * consideradas essenciais, tais como nome, tipo do arquivo e o link para onde
 * foi exportado, são consideradas atributos da classe. As outras "básicas" (que
 * já vem prontas para o pré-processamento) são guardades em uma lista de
 * propriedades.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class EvidenceFile implements Serializable {
	/** Identificador utilizado para serialização da classe. */
	private static final long serialVersionUID = 98653214753695125L;

	/** Nome do arquivo. */
	private String name;

	/** Extensão arquivo. */
	private String extension;

	/**
	 * Caminho completo do arquivo, armazenado em um nó de uma estrutura
	 * navegável de arquivos e pastas do caso.
	 */
	private PathNode path;

	private String pathString;

	/** Tipo do arquivo. */
	private EvidenceFileType type;

	private MediaType mediaType;

	public MediaType getMediaType() {
		return mediaType;
	}

	public void setMediaType(MediaType mediaType) {
		this.mediaType = mediaType;
	}

	private boolean deleted = false;

	private int ID = -1;

	private String ftkID;

	private String parentId;

	private List<Integer> parentIds = new ArrayList<Integer>();
	
	private HashMap<String, Object> extraAttributes = new HashMap<String, Object>();

	/** Data de criação do arquivo. */
	private Date creationDate;

	/** Data da última alteração do arquivo. */
	private Date modificationDate;

	/** Data do último acesso do arquivo. */
	private Date accessDate;

	/** Tamanho do arquivo em bytes. */
	private Long length;

	/** Nome e caminho relativo que o arquivo foi exportado. */
	private String exportedFile;

	/**
	 * Nome e caminho relativo do arquivo alternativo. Este arquivo é utilizado
	 * por exemplo quando no próprio relatório há uma outra forma de
	 * visualização do arquivo de evidência.
	 */
	private String alternativeFile;

	/** Nome e caminho relativo que o arquivo para visualização. */
	private String viewFile;

	private EvidenceFile emailPai;

	private List<EvidenceFile> attachments = new ArrayList<EvidenceFile>();

	private HashSet<String> categories = new HashSet<String>();

	private String labels;

	private boolean timeOut = false;

	private boolean duplicate = false;

	private boolean isSubItem = false, isToExtract = false, extracted = false,
			parsed = false, hasChildren = false;

	private boolean isDir = false, isRoot = false, toIgnore = false,
			toIndex = true;

	private boolean carved = false, encrypted = false;
	
	private boolean isQueueEnd = false;

	private String parsedTextCache;

	private String hash;

	private File file;

	public void setId(int id) {
		ID = id;
	}

	public int getId() {
		if (ID == -1)
			ID = Counter.getNextId();

		return ID;
	}

	public static void setStartID(int start) {
		Counter.nextId = start;
	}

	private static class Counter {

		public static int nextId = 0;

		public static synchronized int getNextId() {
			return nextId++;
		}

	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public void addCategory(String category) {
		this.categories.add(category);
	}

	public void setCategory(String category) {
		categories = new HashSet<String>();
		categories.add(category);
	}

	public String getCategories() {
		String names = "";
		int i = 0;
		for (String bookmark : categories) {
			names += bookmark;
			if (++i < categories.size())
				names += CategoryTokenizer.SEPARATOR;
		}
		return names;
	}

	public HashSet<String> getCategorySet() {
		return categories;
	}

	public void removeCategory(String category) {
		categories.remove(category);
	}

	public void setLabels(String labels) {
		this.labels = labels;
	}

	public String getLabels() {
		return labels;
	}

	public String getFileToIndex() {
		if (alternativeFile != null)
			return alternativeFile.trim();
		else if (exportedFile != null)
			return exportedFile.trim();
		else
			return "";
	}

	@Override
	public EvidenceFile clone() {
		return this.clone();
	}

	public void setEmailPai(EvidenceFile pai) {
		emailPai = pai;
	}

	public EvidenceFile getEmailPai() {
		return emailPai;
	}

	public void addAttachment(EvidenceFile attachment) {
		this.attachments.add(attachment);
	}

	public List<EvidenceFile> getAttachments() {
		return this.attachments;
	}

	/**
	 * @return data do último acesso
	 */
	public Date getAccessDate() {
		return accessDate;
	}

	/**
	 * @param accessDate
	 *            nova data de último acesso
	 */
	public void setAccessDate(Date accessDate) {
		this.accessDate = accessDate;
	}

	/**
	 * @return data de criação do arquivo
	 */
	public Date getCreationDate() {
		return creationDate;
	}

	/**
	 * @param creationDate
	 *            nova data de criação do arquivo
	 */
	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	/**
	 * @return nome e caminho relativo ao caso com que o arquivo de evidência em
	 *         si foi exportado
	 */
	public String getExportedFile() {
		return exportedFile;
	}

	/**
	 * @param exportedFile
	 *            nome e caminho que o arquivo de evidência foi exportado
	 */
	public void setExportedFile(String exportedFile) {
		this.exportedFile = exportedFile;
	}

	/**
	 * @return nome e caminho do arquivo alternativo
	 */
	public String getAlternativeFile() {
		return alternativeFile;
	}

	/**
	 * @param alternativeFile
	 *            nome e caminho do arquivo alternativo
	 */
	public void setAlternativeFile(String alternativeFile) {
		this.alternativeFile = alternativeFile;
	}

	/**
	 * Obtém o arquivo de visualização. Se for nulo, retorna o arquivo
	 * exportado.
	 * 
	 * @return nome e caminho relativo ao caso do arquivo de para visualização
	 */
	public String getViewFile() {
		return (viewFile == null) ? exportedFile : viewFile;
	}

	/**
	 * @param viewFile
	 *            nome e caminho do arquivo para visualização
	 */
	public void setViewFile(String viewFile) {
		this.viewFile = viewFile;
	}

	/**
	 * @return data da última modificação do arquivo
	 */
	public Date getModDate() {
		return modificationDate;
	}

	/**
	 * @param modificationDate
	 *            nova data da última modificação do arquivo
	 */
	public void setModificationDate(Date modificationDate) {
		this.modificationDate = modificationDate;
	}

	/**
	 * @return nome do arquivo
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            nome do arquivo
	 */
	public void setName(String name) {
		this.name = name;
		int p = name.lastIndexOf(".");
		extension = (p < 0) ? "" : name.substring(p + 1).toLowerCase();
	}

	public void setExtension(String ext) {
		extension = ext;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isDeleted() {
		return deleted;
	}

	/**
	 * @return caminho do arquivo, obtido na estrutura de árvore de arquivos do
	 *         caso
	 */
	public PathNode getPathNode() {
		return path;
	}

	/**
	 * @return String com o caminho completo do
	 */
	public String getPath() {
		if (pathString != null)
			return pathString;
		else if (path != null)
			return path.getFullPath();
		else
			return null;
	}

	/**
	 * @param path
	 *            novo caminho do arquivo
	 */
	public void setPath(PathNode path) {
		this.path = path;
	}

	public void setPath(String path) {
		this.pathString = path;
	}

	/**
	 * @return tamanho do arquivo em bytes
	 */
	public Long getLength() {
		return length;
	}

	/**
	 * @param length
	 *            novo tamanho do arquivo
	 */
	public void setLength(Long length) {
		this.length = length;
	}

	/**
	 * @return o tipo de arquivo
	 */
	public EvidenceFileType getType() {
		return type;
	}

	/**
	 * @param type
	 *            o novo tipo de arquivo
	 */
	public void setType(EvidenceFileType type) {
		this.type = type;
	}

	/**
	 * @return Pasta de armazenamento do arquivo, sem repetir o próprio nome do
	 *         arquivo, convertido para String.
	 */
	public String getFolder() {
		if (path == null)
			return "";
		PathNode parent = path.getParent();
		return parent == null ? "" : parent.getFullPath();
	}

	public String getExt() {
		return extension;
	}

	/**
	 * Retorna String com os dados contidos no objeto.
	 * 
	 * @return String listando as propriedades do arquivo.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Arquivo: " + name);
		sb.append("\n\t\tCaminho: ").append(getFolder());
		if (type != null)
			sb.append("\n\t\t").append("Tipo de Arquivo: ")
					.append(type.getLongDescr());
		if (creationDate != null)
			sb.append("\n\t\tData de Criação: ").append(
					FormatUtil.format(creationDate));
		if (modificationDate != null)
			sb.append("\n\t\tData de Modificação: ").append(
					FormatUtil.format(modificationDate));
		if (accessDate != null)
			sb.append("\n\t\tData de Último Acesso: ").append(
					FormatUtil.format(accessDate));
		if (length != null)
			sb.append("\n\t\tTamanho do Arquivo: ").append(length);
		return sb.toString();
	}

	public String getFtkID() {
		return ftkID;
	}

	public void setFtkID(String ftkID) {
		this.ftkID = ftkID;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public File getFile() {
		return file;
	}

	private AbstractFile sleuthFile;
	private long startOffset = -1;
	private String sleuthId;

	public void setSleuthFile(AbstractFile sleuthFile) {
		this.sleuthFile = sleuthFile;
	}

	public AbstractFile getSleuthFile() {
		return sleuthFile;
	}

	static final int BUF_LEN = 8 * 1024 * 1024;

	public InputStream getBufferedStream() throws IOException {

		int len = 8192;
		if (length != null && length > len)
			if (length < BUF_LEN)
				len = length.intValue();
			else
				len = BUF_LEN;

		return new BufferedInputStream(getStream(), len);
	}

	/*
	 * OBS: skip usando ReadContentInputStream é eficiente pois utiliza seek
	 * TODO implementar skip para File usando RandomAccessFile
	 */
	private InputStream getStream() throws IOException {
		InputStream stream;
		if (file != null && !this.isDir)
			stream = new FileInputStream(file);

		else if (sleuthFile != null)
			stream = new ReadContentInputStream(sleuthFile);

		else
			stream = new ByteArrayInputStream(new byte[0]);

		if (startOffset != -1) {
			long skiped = 0;
			do {
				skiped += stream.skip(startOffset - skiped);
			} while (skiped < startOffset);

			stream = new LimitedInputStream(stream, length);
		}
		return stream;
	}

	public TikaInputStream getTikaStream() throws IOException {
		if (startOffset == -1 && file != null && !this.isDir)
			return TikaInputStream.get(file);
		else
			return TikaInputStream.get(getBufferedStream());
	}

	public boolean isSubItem() {
		return isSubItem;
	}

	public void setSubItem(boolean isSubItem) {
		this.isSubItem = isSubItem;
	}

	public boolean isToExtract() {
		return isToExtract;
	}

	public void setToExtract(boolean isToExtract) {
		this.isToExtract = isToExtract;
	}

	public String getSleuthId() {
		return sleuthId;
	}

	public void setSleuthId(String sleuthId) {
		this.sleuthId = sleuthId;
	}

	public boolean isExtracted() {
		return extracted;
	}

	public void setExtracted(boolean extracted) {
		this.extracted = extracted;
	}

	public void setCarved(boolean carved) {
		this.carved = carved;
	}

	public boolean isCarved() {
		return carved;
	}

	public void setEncrypted(boolean encrypted) {
		this.encrypted = encrypted;
	}

	public boolean isEncrypted() {
		return encrypted;
	}

	public void setFileOffset(long fileOffset) {
		this.startOffset = fileOffset;
	}

	public long getFileOffset() {
		return startOffset;
	}

	public boolean hasChildren() {
		return hasChildren;
	}

	public void setHasChildren(boolean hasChildren) {
		this.hasChildren = hasChildren;
	}

	public boolean isDir() {
		return isDir;
	}

	public void setIsDir(boolean isDir) {
		this.isDir = isDir;
	}

	public boolean isRoot() {
		return isRoot;
	}

	public void setRoot(boolean isRoot) {
		this.isRoot = isRoot;
	}

	public String getParentIdsString() {
		String parents = "";
		for (Integer id : parentIds)
			parents += id + " ";

		return parents;
	}

	public void addParentId(int parentId) {
		this.parentIds.add(parentId);
	}

	public List<Integer> getParentIds() {
		return parentIds;
	}

	public void addParentIds(List<Integer> parentIds) {
		this.parentIds.addAll(parentIds);
	}

	public String getParsedTextCache() {
		return parsedTextCache;
	}

	public void setParsedTextCache(String parsedTextCache) {
		this.parsedTextCache = parsedTextCache;
	}

	public boolean isToIndex() {
		return toIndex;
	}

	public void setToIndex(boolean toIndex) {
		this.toIndex = toIndex;
	}

	public boolean isToIgnore() {
		return toIgnore;
	}

	public void setToIgnore(boolean toIgnore) {
		this.toIgnore = toIgnore;
	}

	public boolean isParsed() {
		return parsed;
	}

	public void setParsed(boolean parsed) {
		this.parsed = parsed;
	}

	public boolean isTimedOut() {
		return timeOut;
	}

	public void setTimeOut(boolean timeOut) {
		this.timeOut = timeOut;
	}

	public boolean isDuplicate() {
		return duplicate;
	}

	public void setDuplicate(boolean duplicate) {
		this.duplicate = duplicate;
	}

	public boolean isQueueEnd() {
		return isQueueEnd;
	}

	public void setQueueEnd(boolean isQueueEnd) {
		this.isQueueEnd = isQueueEnd;
	}

	public Object getExtraAttribute(String key) {
		return extraAttributes.get(key);
	}

	public void setExtraAttribute(String key, Object value) {
		this.extraAttributes.put(key, value);
	}

}
