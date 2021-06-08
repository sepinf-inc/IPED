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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.FileSystemConfig;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.Item;
import iped3.ICaseData;
import iped3.IItem;

public class FolderTreeReader extends DataSourceReader {

    private static Logger LOGGER = LoggerFactory.getLogger(FolderTreeReader.class);

    public static final String FS_OWNER = "fileSystemOwner"; //$NON-NLS-1$

    private Pattern excludePattern;

    private File rootFile;
    private String evidenceName;
    private CmdLineArgs args;

    public FolderTreeReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    public int read(File file) throws Exception {
        evidenceName = getEvidenceName(file);
        if (evidenceName == null) {
            evidenceName = file.getName();
        }
        dataSource = new DataSource(file);
        dataSource.setName(evidenceName);
        read(file, null);
        return 0;

    }

    private boolean isRoot(File file) {
        for (File f : File.listRoots()) {
            if (f.equals(file))
                return true;
        }
        return false;
    }

    @Override
    public void read(File file, Item parent) throws Exception {
        args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        evidenceName = getEvidenceName(file);
        if (evidenceName == null) {
            evidenceName = file.getName();
        }
        if (evidenceName.isEmpty() && isRoot(file)) {
            evidenceName = file.getAbsolutePath().substring(0, 2);
        }

        FileSystemConfig fsConfig = ConfigurationManager.findObject(FileSystemConfig.class);
        if (!fsConfig.getSkipFolderRegex().isEmpty()) {
            excludePattern = Pattern.compile(fsConfig.getSkipFolderRegex(), Pattern.CASE_INSENSITIVE);
        }

        rootFile = file;

        List<Integer> parents;
        List<String> paths;
        if (parent != null) {
            parents = parent.getParentIds();
            parents.add(parent.getId());

            StringTokenizer stringTokenizer = new StringTokenizer(parent.getPath(), File.separator);
            paths = stringTokenizer.getTokenList();
        } else {
            parents = Collections.emptyList();
            paths = Collections.emptyList();
        }
        transverse(file, parents, paths);
    }

    private void transverse(File file, List<Integer> parents, List<String> paths) throws IOException {
        new FolderVisitor(parents, paths).walk(file);
    }

    private IItem getEvidence(Path path, BasicFileAttributes attr) {
        if (listOnly) {
            caseData.incDiscoveredEvidences(1);
            caseData.incDiscoveredVolume(attr.size());
            return null;

        } else {
            File file = path.toFile();
            IItem item = new Item();
            item.setName(file.getName());
            if (file.equals(rootFile)) {
                item.setName(evidenceName);
            }
            item.setDataSource(dataSource);
            try {
                String relativePath = Util.getRelativePath(output, file);
                item.setExportedFile(relativePath);
                item.setFile(file);
            } catch (InvalidPathException e) {
                LOGGER.error("File content will not be processed " + e.toString()); //$NON-NLS-1$
            }

            if (args.isAddowner())
                try {
                    UserPrincipal owner = Files.getOwner(path);
                    if (owner != null)
                        item.setExtraAttribute(FS_OWNER, owner.toString());

                } catch (IOException e) {
                    e.printStackTrace();
                }

            return item;
        }
    }

    class FolderVisitor implements FileVisitor<Path> {

        private LinkedList<Integer> parentIds;
        private LinkedList<String> paths;

        public FolderVisitor(List<Integer> parentIds, List<String> paths) {
            super();
            this.parentIds = new LinkedList<>(parentIds);
            this.paths = new LinkedList<>(paths);
        }

        public void walk(File file) throws IOException {
            Path startingDir = file.toPath();
            Files.walkFileTree(startingDir, this);
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attr) {
            if (Thread.interrupted()) {
                return FileVisitResult.TERMINATE;
            }

            IItem item = getEvidence(path, attr);
            if (item != null) {
                if (!parentIds.isEmpty()) {
                    item.setParentId(parentIds.getLast());
                    item.addParentIds(parentIds);

                    if (parentIds.size() == 2) {
                        item.setExtraAttribute("X-Reader", this.getClass().getSimpleName());
                    }
                } else {
                    item.setRoot(true);
                }

                String fileName = item.getName();
                String evidencePath = paths.isEmpty() ? fileName
                        : paths.stream().collect(Collectors.joining(File.separator)) + File.separator + fileName;
                item.setPath(evidencePath);

                if (attr.isDirectory()) {
                    item.setIsDir(true);
                }

                item.setAccessDate(new Date(attr.lastAccessTime().toMillis()));
                item.setCreationDate(new Date(attr.creationTime().toMillis()));
                item.setModificationDate(new Date(attr.lastModifiedTime().toMillis()));
                item.setLength(attr.size());

                try {
                    caseData.addItem(item);

                } catch (InterruptedException e) {
                    return FileVisitResult.TERMINATE;
                }

                if (attr.isDirectory()) {
                    // must getId() after caseData.addItem(), it could set item id to previous id
                    // with --continue
                    parentIds.addLast(item.getId());
                    paths.addLast(fileName);
                }
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attr) throws IOException {

            if (excludePattern != null && excludePattern.matcher(path.toString()).find()) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            if (this.visitFile(path, attr).equals(FileVisitResult.TERMINATE)) {
                return FileVisitResult.TERMINATE;
            }

            if (attr.isSymbolicLink() || attr.isOther()) { // pula links simbólicos e NTFS junctions
                parentIds.pollLast();
                paths.pollLast();
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException {

            parentIds.pollLast();
            paths.pollLast();

            if (exception != null) {
                System.err.println(
                        new Date() + "\t[WARN]\t" + "Directory ignored: " + path.toFile().getAbsolutePath() + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                + exception.toString());
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path path, IOException exception) throws IOException {

            if (exception != null) {
                System.err.println(new Date() + "\t[WARN]\t" + "File ignored: " + path.toFile().getAbsolutePath() + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + exception.toString());
            }

            return FileVisitResult.CONTINUE;
        }

    }

    @Override
    public boolean isSupported(File datasource) {
        return true;
    }

}
