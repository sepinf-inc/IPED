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
package iped.engine.datasource;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.util.Date;
import java.util.LinkedList;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.ICaseData;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.core.Manager;
import iped.engine.data.DataSource;
import iped.engine.data.Item;
import iped.properties.ExtraProperties;
import iped.utils.FileInputStreamFactory;
import iped.utils.fsw.PathWrapper;

public class FolderTreeReader extends DataSourceReader {

    public static final String FS_OWNER = "fileSystemOwner"; //$NON-NLS-1$

    private static final Logger logger = LoggerFactory.getLogger(FolderTreeReader.class);

    private FileInputStreamFactory inputStreamFactory;

    private Pattern excludePattern;
    private File rootFile;
    private String evidenceName;
    private CmdLineArgs args;

    public FolderTreeReader(ICaseData caseData, File output, boolean listOnly) {
        super(caseData, output, listOnly);
    }

    public void read(File file) throws Exception {
        evidenceName = getEvidenceName(file);
        if (evidenceName == null) {
            evidenceName = file.getName();
        }
        dataSource = new DataSource(file);
        dataSource.setName(evidenceName);
        read(file, null);

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

        FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
        if (!fsConfig.getSkipFolderRegex().isEmpty()) {
            excludePattern = Pattern.compile(fsConfig.getSkipFolderRegex(), Pattern.CASE_INSENSITIVE);
        }

        rootFile = file;
        inputStreamFactory = new FileInputStreamFactory(rootFile.toPath());

        transverse(file, parent);
    }

    private void transverse(File file, Item parent)
            throws IOException {
        new FolderVisitor(parent).walk(file);
    }

    private IItem getEvidence(Path path, BasicFileAttributes attr) {
        if (listOnly) {
            caseData.incDiscoveredEvidences(1);
            caseData.incDiscoveredVolume(attr.size());
            return null;

        } else {
            File file = path.toFile();
            IItem item = new Item();
            item.setDataSource(dataSource);
            String relativePath;
            if (path instanceof PathWrapper) {
                relativePath = rootFile.toPath().relativize(((PathWrapper) path).getWrappedPath()).toString();
            } else {
                relativePath = rootFile.toPath().relativize(path).toString();
            }
            item.setIdInDataSource(relativePath);
            item.setInputStreamFactory(inputStreamFactory);
            if (file.equals(rootFile)) {
                item.setName(evidenceName);
            } else {
                item.setName(file.getName());
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

        private LinkedList<IItem> parents = new LinkedList<>();

        public FolderVisitor(IItem parent) {
            super();
            if (parent != null) {
                this.parents.add(parent);
            }
        }

        public void walk(File file) throws IOException {
            Path startingDir = PathWrapper.create(file.toPath());
            Files.walkFileTree(startingDir, this);
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attr) {
            if (Thread.interrupted()) {
                return FileVisitResult.TERMINATE;
            }

            IItem item = getEvidence(path, attr);
            if (item != null) {
                if (!parents.isEmpty()) {
                    item.setParent(parents.getLast());

                    if (parents.size() == 2) {
                        item.setExtraAttribute(ExtraProperties.DATASOURCE_READER, this.getClass().getSimpleName());
                    }
                } else {
                    item.setRoot(true);
                }

                String fileName = item.getName();
                String evidencePath = parents.isEmpty() ? fileName
                        : parents.getLast().getPath() + File.separator + fileName;
                evidencePath = evidencePath.replace(File.separatorChar, '/');
                item.setPath(evidencePath);

                if (attr.isDirectory()) {
                    item.setIsDir(true);
                }

                FileTime lastAccessTime = attr.lastAccessTime();
                if (lastAccessTime != null) {
                    item.setAccessDate(new Date(lastAccessTime.toMillis()));
                }
                FileTime creationTime = attr.creationTime();
                if (creationTime != null) {
                    item.setCreationDate(new Date(creationTime.toMillis()));
                }
                FileTime lastModifiedTime = attr.lastModifiedTime();
                if (lastModifiedTime != null) {
                    item.setModificationDate(new Date(lastModifiedTime.toMillis()));
                }
                item.setLength(attr.size());

                try {
                    Manager.getInstance().addItemToQueue(item);

                } catch (InterruptedException e) {
                    return FileVisitResult.TERMINATE;
                }

                if (attr.isDirectory()) {
                    // must getId() after Manager.getInstance().addItemToQueue();, it could set item
                    // id to previous id
                    // with --continue
                    parents.addLast(item);
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
                parents.pollLast();
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException {

            parents.pollLast();

            if (exception != null) {
                logger.error("Directory ignored: " + path.toFile().getAbsolutePath() + ": " + exception.toString());
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path path, IOException exception) throws IOException {

            if (exception != null) {
                logger.error("File ignored: " + path.toFile().getAbsolutePath() + ": " + exception.toString());
            }

            return FileVisitResult.CONTINUE;
        }

    }

    @Override
    public boolean isSupported(File datasource) {
        return true;
    }

}
