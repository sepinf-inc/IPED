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

public class FolderTreeReader extends DataSourceReader {

  private File rootFile;
  private String category;
  private String evidenceName;

  public FolderTreeReader(CaseData caseData, File output, boolean listOnly) {
    super(caseData, output, listOnly);
  }

  public int read(File file) throws Exception {

    rootFile = file;
    evidenceName = getEvidenceName(file);
    if (evidenceName == null) {
      evidenceName = file.getName();
    }

    if (!listOnly && !IndexFiles.getInstance().fromCmdLine && caseData.containsReport()) {
      category = file.getName();
      caseData.addBookmark(new FileGroup(category, "", ""));
    }

    new FolderVisitor().walk(file);

    return 0;

  }

  private EvidenceFile getEvidence(File file) {
    if (listOnly) {
      caseData.incDiscoveredEvidences(1);
      caseData.incDiscoveredVolume(file.length());
      return null;

    } else {
      EvidenceFile evidenceFile = new EvidenceFile();
      evidenceFile.setName(file.getName());
      if (file.equals(rootFile)) {
        evidenceFile.setName(evidenceName);
      }

      evidenceFile.setFile(file);
      try {
        String relativePath = Util.getRelativePath(output, file);
        evidenceFile.setExportedFile(relativePath);
      } catch (IOException e) {
      }

      String path = file.getAbsolutePath().replace(rootFile.getAbsolutePath(), evidenceName);
      evidenceFile.setPath(path);

      // evidenceFile.setType(new UnknownFileType(evidenceFile.getExt()));
      if (!IndexFiles.getInstance().fromCmdLine && caseData.containsReport()) {
        evidenceFile.addCategory(category);
      }

      return evidenceFile;
    }
  }

  class FolderVisitor implements FileVisitor<Path> {

    private LinkedList<Integer> parentIds = new LinkedList<Integer>();

    //Proteção caso postVisitDirectory seja chamado após SKIP_SUBTREE em preVisitDirectory?
    private boolean removedInPreVisit = false;

    public void walk(File file) throws IOException {
      Path startingDir = file.toPath();
      Files.walkFileTree(startingDir, this);
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attr) {
      if (Thread.interrupted()) {
        return FileVisitResult.TERMINATE;
      }

      EvidenceFile evidenceFile = getEvidence(path.toFile());
      if (evidenceFile != null) {
        if (!parentIds.isEmpty()) {
          evidenceFile.setParentId(parentIds.getLast().toString());
          evidenceFile.addParentIds(parentIds);
        } else {
          evidenceFile.setRoot(true);
        }

        if (attr.isDirectory()) {
          evidenceFile.setIsDir(true);
          parentIds.addLast(evidenceFile.getId());
        }

        evidenceFile.setAccessDate(new Date(attr.lastAccessTime().toMillis()));
        evidenceFile.setCreationDate(new Date(attr.creationTime().toMillis()));
        evidenceFile.setModificationDate(new Date(attr.lastModifiedTime().toMillis()));
        evidenceFile.setLength(attr.size());

        try {
          caseData.addEvidenceFile(evidenceFile);

        } catch (InterruptedException e) {
          return FileVisitResult.TERMINATE;
        }
      }

      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attr) throws IOException {

      removedInPreVisit = false;

      if (this.visitFile(path, attr).equals(FileVisitResult.TERMINATE)) {
        return FileVisitResult.TERMINATE;
      }

      if (attr.isSymbolicLink() || attr.isOther()) { // pula links simbólicos e NTFS junctions
        parentIds.pollLast();
        removedInPreVisit = true;
        return FileVisitResult.SKIP_SUBTREE;

      }

      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException {

      if (!removedInPreVisit) {
        parentIds.pollLast();
      }

      removedInPreVisit = false;

      if (exception != null) {
        System.err.println(new Date() + "\t[ALERTA]\t" + "Item ignorado: " + path.toFile().getAbsolutePath() + " " + exception.toString());
      }

      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path path, IOException exception) throws IOException {

      if (exception != null) {
        System.err.println(new Date() + "\t[ALERTA]\t" + "Item ignorado: " + path.toFile().getAbsolutePath() + " " + exception.toString());
      }

      return FileVisitResult.CONTINUE;
    }

  }

  @Override
  public boolean isSupported(File datasource) {
    return true;
  }

}
