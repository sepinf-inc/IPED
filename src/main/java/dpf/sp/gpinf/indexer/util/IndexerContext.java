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
package dpf.sp.gpinf.indexer.util;

import gpinf.dev.data.EvidenceFile;

import java.util.HashSet;

public class IndexerContext {

	private int child = -1, id;
	private HashSet<String> bookmarks;
	private String path;
	private EvidenceFile evidence;

	public IndexerContext(int id, HashSet<String> bookmarks, String path) {
		updateProps(id, bookmarks, path);
	}
	
	private final void updateProps(int id, HashSet<String> bookmarks, String path) {
		this.id = id;
		this.bookmarks = bookmarks;
		this.path = path;
	}
	
	public IndexerContext(EvidenceFile evidence){
		this.setEvidence(evidence);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public void incChild() {
		this.child++;
	}

	public int getChild() {
		return child;
	}

	public void setBookmarks(HashSet<String> bookmarks) {
		this.bookmarks = bookmarks;
	}

	public HashSet<String> getBookmarks() {
		return bookmarks;
	}

	public EvidenceFile getEvidence() {
		return evidence;
	}

	public void setEvidence(EvidenceFile evidence) {
		this.evidence = evidence;
		updateProps(evidence.getId(), evidence.getCategorySet(), evidence.getPath());
	}

}
