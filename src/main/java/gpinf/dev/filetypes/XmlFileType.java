package gpinf.dev.filetypes;

import gpinf.dev.DEVConstants;
import gpinf.dev.data.EvidenceFile;
import gpinf.util.FileUtil;
import gpinf.util.ProcessUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Implementação da classe utilizada para arquivos XML. Tratamento especial dado
 * a arquivos que utilizem o arquivo MessageLog.XSL.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class XmlFileType extends EvidenceFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = -740076497360602198L;

	/**
	 * Tipo de visualização. * private ViewType viewType = ViewType.NONE;
	 * 
	 * /** Descrição.
	 */
	private String descr = "Arquivo XML";

	/**
	 * Ícone associado ao subtipo "registro de conversas". * private static
	 * final Icon iconMsg = IconUtil.createIcon("ft-chat");
	 * 
	 * /** Ícone XML padrão. * private static final Icon iconXml =
	 * IconUtil.createIcon("ft-xml");
	 * 
	 * /** Ícon correspondente ao tipo. * private Icon icon = iconXml;
	 * 
	 * /**
	 * 
	 * @return o tipo de visualização
	 * 
	 *         public ViewType getViewType() { return viewType; }
	 * 
	 *         /**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return descr;
	}

	/**
	 * Processa arquivos deste tipo. Verifica se o arquivo utiliza o
	 * MessageLog.xsl e, se for o caso, aplica a transformação do arquivo para
	 * HTML e na sequencia para PDF.
	 * 
	 * @param baseDir
	 *            diretório base onde arquivo de evidência exportados estão
	 *            armazenados
	 * @param evidenceFiles
	 *            lista de arquivos a ser processada
	 */
	@Override
	public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles) {
		List<EvidenceFile> lMsgLog = new ArrayList<EvidenceFile>();
		for (int i = 0; i < evidenceFiles.size(); i++) {
			EvidenceFile evidenceFile = evidenceFiles.get(i);
			File file = new File(baseDir, evidenceFile.getExportedFile());
			try {
				String text = FileUtil.read(file, true, 10000).toLowerCase();
				int p1 = text.indexOf("xml-stylesheet");
				if (p1 < 0)
					continue;
				int p2 = text.indexOf("?>", p1);
				if (p2 < 0)
					continue;
				int p3 = text.indexOf("messagelog.xsl", p1);
				if (p3 < 0 || p3 > p2)
					continue;
				lMsgLog.add(evidenceFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (lMsgLog.size() == 0)
			return;

		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = null;
		try {
			transformer = tFactory.newTransformer(new StreamSource(DEVConstants.MESSAGE_LOG));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		StringBuilder sb = new StringBuilder();
		String lineSep = System.getProperty("line.separator");
		sb.append(">>NoProps<<").append(lineSep);
		sb.append(">>NoMargins<<").append(lineSep);
		boolean isDone = true;
		for (int i = 0; i < lMsgLog.size(); i++) {
			EvidenceFile evidenceFile = lMsgLog.get(i);
			String exp = evidenceFile.getExportedFile();

			String view1 = getViewFile(exp, "html");
			File fview1 = new File(baseDir, view1);

			String view2 = getViewFile(exp, "pdf");
			File fview2 = new File(baseDir, view2);
			if (fview2.exists()) {
				evidenceFile.setViewFile(view2);
				continue;
			}

			try {
				fview1.getParentFile().mkdirs();
				transformer.transform(new StreamSource(new File(baseDir, exp)), new StreamResult(new FileOutputStream(fview1)));
			} catch (Exception e) {
				System.err.println("Erro no Tratamento do Arquivo XML: " + exp);
				continue;
			}

			sb.append(lineSep);
			sb.append(i).append(';');
			sb.append(fview1.getAbsolutePath()).append(';');
			sb.append(fview2.getAbsolutePath());
			evidenceFile.setViewFile(view2);

			isDone = false;
		}
		if (isDone)
			return;

		// Converte para PDF
		String result = ProcessUtil.run(DEVConstants.DOC2PDF, sb.toString());
		if (result != null) {
			// Trata resultado
			String[] lines = result.split("\n");
			EvidenceFile evidenceFile = null;
			for (String line : lines) {
				if (line.length() == 0)
					continue;
				if (line.charAt(0) != ' ') {
					int idx = Integer.parseInt(line.split(";")[0]);
					evidenceFile = lMsgLog.get(idx);
					if (!(new File(baseDir, evidenceFile.getViewFile()).exists())) {
						evidenceFile.setViewFile(null);
					} else {
						XmlFileType ft = (XmlFileType) evidenceFile.getType();
						ft.descr = "Arquivo XML (Registro de Mensagens)";
						// ft.viewType = ViewType.PDF;
						// ft.icon = iconMsg;
					}
					continue;
				}
			}
		}

		// Remove HTML's intermediários
		for (int i = 0; i < lMsgLog.size(); i++) {
			EvidenceFile evidenceFile = lMsgLog.get(i);
			String exp = evidenceFile.getExportedFile();
			String view1 = getViewFile(exp, "html");
			File fview1 = new File(baseDir, view1);
			if (fview1.exists())
				fview1.delete();
		}
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
