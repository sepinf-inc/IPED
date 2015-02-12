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
package dpf.sp.gpinf.indexer.datasource;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.LinkedList;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;
import dpf.sp.gpinf.indexer.util.Util;

public class FolderTreeProcessor {

	private CaseData caseData;
	private boolean listOnly;
	private File baseFile, rootFile;
	private String category;

	public FolderTreeProcessor(CaseData caseData, File basePath, boolean listOnly) {
		this.caseData = caseData;
		this.listOnly = listOnly;
		this.baseFile = basePath;
	}

	public void process(File file) throws Exception {

		rootFile = file;
		
		if (!listOnly && !IndexFiles.getInstance().fromCmdLine && caseData.containsReport()) {
			category = file.getName();
			caseData.addBookmark(new FileGroup(category, "", ""));
		}

		new FolderVisitor().walk(file);
			

	}

	private EvidenceFile getEvidence(File file) throws IOException {
		if (listOnly) {
			caseData.incDiscoveredEvidences(1);
			//IndexFiles.getInstance().firePropertyChange("discovered", 0, caseData.getDiscoveredEvidences());
			caseData.incDiscoveredVolume(file.length());
			return null;
		} else {
			EvidenceFile evidenceFile = new EvidenceFile();

			String name = file.getName();
			evidenceFile.setName(name);

			String relativePath = Util.getRelativePath(baseFile, file);
			evidenceFile.setExportedFile(relativePath);
			evidenceFile.setPath(relativePath);
			// evidenceFile.setType(new UnknownFileType(evidenceFile.getExt()));

			if (!IndexFiles.getInstance().fromCmdLine && caseData.containsReport())
				evidenceFile.addCategory(category);

			return evidenceFile;
		}
	}

	class FolderVisitor implements FileVisitor<Path> {
		
		private LinkedList<Integer> parentIds = new LinkedList<Integer>(); 

		public void walk(File file) throws IOException {
			Path startingDir = file.toPath();
			Files.walkFileTree(startingDir, this);
		}

		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
			if (Thread.interrupted())
				return FileVisitResult.TERMINATE;

				try {
					EvidenceFile evidenceFile = getEvidence(path.toFile());
					if (evidenceFile != null) {
						evidenceFile.setAccessDate(new Date(attr.lastAccessTime().toMillis()));
						evidenceFile.setCreationDate(new Date(attr.creationTime().toMillis()));
						evidenceFile.setModificationDate(new Date(attr.lastModifiedTime().toMillis()));
						evidenceFile.setLength(attr.size());
						if(!parentIds.isEmpty()){
							evidenceFile.setParentId(parentIds.getLast().toString());
							evidenceFile.addParentIds(parentIds);
						}else
							evidenceFile.setRoot(true);
						
						if (attr.isDirectory()){
							evidenceFile.setIsDir(true);
							evidenceFile.setCategory(SetCategoryTask.FOLDER_CATEGORY);
							parentIds.addLast(evidenceFile.getId());
						}
							
						caseData.addEvidenceFile(evidenceFile);
					}
				} catch (InterruptedException e) {
					return FileVisitResult.TERMINATE;

				} catch (IOException e) {
					System.err.println(new Date() + "\t[ALERTA]\t" + "Indexação ignorada: " + path.toFile().getAbsolutePath() + " " + e.toString());
					return FileVisitResult.CONTINUE;
				}

			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attr) throws IOException {

			if(this.visitFile(path, attr).equals(FileVisitResult.TERMINATE))
				return FileVisitResult.TERMINATE;
			
			if (attr.isSymbolicLink() || attr.isOther()) // pula links simbólicos e NTFS junctions
				return FileVisitResult.SKIP_SUBTREE;

			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException {

			parentIds.pollLast();
			
			if (exception != null)
				System.err.println(new Date() + "\t[ALERTA]\t" + "Indexação ignorada: " + path.toFile().getAbsolutePath() + " " + exception.toString());

			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path path, IOException exception) throws IOException {

			if (exception != null)
				System.err.println(new Date() + "\t[ALERTA]\t" + "Indexação ignorada: " + path.toFile().getAbsolutePath() + " " + exception.toString());

			return FileVisitResult.CONTINUE;
		}

	}

}
