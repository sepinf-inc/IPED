package iped.utils.fsw;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

public class FileAttributes implements BasicFileAttributes {

    PathWrapper path;

    public FileAttributes(PathWrapper path) {
        this.path = path;
    }

    @Override
    public FileTime lastModifiedTime() {
        return null;
    }

    @Override
    public FileTime lastAccessTime() {
        return null;
    }

    @Override
    public FileTime creationTime() {
        return null;
    }

    @Override
    public boolean isRegularFile() {
        return path.toFile().isFile();
    }

    @Override
    public boolean isDirectory() {
        try (DirectoryStream ds = Files.newDirectoryStream(path)) {
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
