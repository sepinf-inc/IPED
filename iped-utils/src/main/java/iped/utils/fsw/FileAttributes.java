package iped.utils.fsw;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

public class FileAttributes implements BasicFileAttributes {

    PathWrapper path;
<<<<<<< HEAD
    
    public FileAttributes(PathWrapper path) {
        this.path = path;
    }
    
=======
>>>>>>> branch 'OvercomePathWithTrailingSpaces' of https://github.com/sepinf-inc/IPED.git

<<<<<<< HEAD
    @Override
    public FileTime lastModifiedTime() {
        return null;
=======
    public FileAttributes(PathWrapper path) {
        this.path = path;
>>>>>>> branch 'OvercomePathWithTrailingSpaces' of https://github.com/sepinf-inc/IPED.git
    }

    @Override
<<<<<<< HEAD
    public FileTime lastAccessTime() {
=======
    public FileTime lastModifiedTime() {
>>>>>>> branch 'OvercomePathWithTrailingSpaces' of https://github.com/sepinf-inc/IPED.git
        return null;
    }

    @Override
<<<<<<< HEAD
    public FileTime creationTime() {
=======
    public FileTime lastAccessTime() {
>>>>>>> branch 'OvercomePathWithTrailingSpaces' of https://github.com/sepinf-inc/IPED.git
        return null;
    }

    @Override
<<<<<<< HEAD
    public boolean isRegularFile() {
        return path.toFile().isFile();
=======
    public FileTime creationTime() {
        return null;
>>>>>>> branch 'OvercomePathWithTrailingSpaces' of https://github.com/sepinf-inc/IPED.git
    }

    @Override
<<<<<<< HEAD
=======
    public boolean isRegularFile() {
        return path.toFile().isFile();
    }

    @Override
>>>>>>> branch 'OvercomePathWithTrailingSpaces' of https://github.com/sepinf-inc/IPED.git
    public boolean isDirectory() {
        try (DirectoryStream ds = Files.newDirectoryStream(path)) {
            Files.newDirectoryStream(path);
        } catch (NotDirectoryException ioe) {
            return false;
        } catch (Exception e) {
            return true;
        }
        return true;
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return path.toFile().length();
    }

    @Override
    public Object fileKey() {
        return null;
    }

}
