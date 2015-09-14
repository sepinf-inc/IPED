/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search.viewer;

import java.io.File;
import java.util.Set;

import javax.swing.JLabel;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class NoJavaFXViewer extends AbstractViewer {

	final static String NO_JAVAFX_MSG = "<html>Visualização não suportada. Atualize o Java para a versão 7u06 ou superior.</html>";

	public NoJavaFXViewer() {
		this.getPanel().add(new JLabel(NO_JAVAFX_MSG));
	}

	@Override
	public String getName() {
		return "NoJavaFX";
	}

	@Override
	public boolean isSupportedType(String contentType) {
		return contentType.equals("text/html") || contentType.equals("application/xhtml+xml") || contentType.equals("text/asp") || contentType.equals("text/aspdotnet")
				|| contentType.equals("message/rfc822") || contentType.equals("message/x-emlx") || contentType.equals("message/outlook-pst") || contentType.equals("application/messenger-plus");
	}

	@Override
	public void init() {

	}

	@Override
	public void dispose() {
	}

	@Override
	public void loadFile(StreamSource content, Set<String> highlightTerms) {
	}

	@Override
	public void scrollToNextHit(boolean forward) {
		// TODO Auto-generated method stub

	}

}
