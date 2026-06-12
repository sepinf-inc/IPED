package iped.engine.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class PasswordDictionary implements Iterable<String> {

    private final Path folderPath;
    private final List<String> prependedPasswords;

    public PasswordDictionary(Path folderPath, List<String> prependedPasswords) {
        this.folderPath = folderPath;
        this.prependedPasswords = prependedPasswords;
    }

    @Override
    public Iterator<String> iterator() {
        return new PasswordDictionaryIterator(folderPath, prependedPasswords);
    }

    private static class PasswordDictionaryIterator implements Iterator<String> {

        // Phase 1: In-memory targeted passwords
        private final Iterator<String> prependedIterator;

        // Phase 2: Disk-based passwords
        private DirectoryStream<Path> directoryStream;
        private Iterator<Path> fileIterator;
        private BufferedReader currentReader;
        private String nextLine;

        public PasswordDictionaryIterator(Path folderPath, List<String> prependedPasswords) {
            this.prependedIterator = prependedPasswords != null ? prependedPasswords.iterator() : Collections.emptyIterator();

            // Only initialize the directory stream if a folder was actually provided
            if (folderPath != null && Files.isDirectory(folderPath)) {
                try {
                    this.directoryStream = Files.newDirectoryStream(folderPath, "*.txt");
                    this.fileIterator = directoryStream.iterator();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read dictionary directory: " + folderPath, e);
                }
            }
        }

        private void advanceToNextLine() {
            if (nextLine != null) {
                return;
            }

            while (true) {
                if (currentReader != null) {
                    try {
                        nextLine = currentReader.readLine();
                        if (nextLine != null) {
                            return;
                        }
                        currentReader.close();
                        currentReader = null;
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading from password file", e);
                    }
                } else if (fileIterator != null && fileIterator.hasNext()) {
                    try {
                        currentReader = Files.newBufferedReader(fileIterator.next());
                    } catch (IOException e) {
                        throw new RuntimeException("Error opening password file", e);
                    }
                } else {
                    if (directoryStream != null) {
                        try {
                            directoryStream.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                        directoryStream = null;
                    }
                    return; // No more lines
                }
            }
        }

        @Override
        public boolean hasNext() {
            // Phase 1: Always exhaust the prepended list first
            if (prependedIterator.hasNext()) {
                return true;
            }

            // Phase 2: Move on to the file dictionaries
            advanceToNextLine();
            return nextLine != null;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more passwords available.");
            }

            // Serve from Phase 1 if available
            if (prependedIterator.hasNext()) {
                return prependedIterator.next();
            }

            // Serve from Phase 2
            String line = nextLine;
            nextLine = null;
            return line;
        }
    }
}
