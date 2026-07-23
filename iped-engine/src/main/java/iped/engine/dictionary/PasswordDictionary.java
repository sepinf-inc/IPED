package iped.engine.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Password dictionary used to crack passwords (e.g. DPAPI master keys).
 *
 * The dictionary is composed of two phases:
 * <ol>
 * <li>Phase 1 - in-memory passwords already known from the case
 * (prependedPasswords), tried first;</li>
 * <li>Phase 2 - a single wordlist file provided by the examiner through the
 * command line, read incrementally (one candidate password per line).</li>
 * </ol>
 */
public class PasswordDictionary implements Iterable<String> {

    private static final Logger logger = LoggerFactory.getLogger(PasswordDictionary.class);

    private final Path wordlistFile;
    private final List<String> prependedPasswords;

    public PasswordDictionary(Path wordlistFile, List<String> prependedPasswords) {
        this(wordlistFile, prependedPasswords, 0);
    }

    /**
     * @param wordlistFile
     *            the wordlist file (one candidate password per line). May be
     *            {@code null} if only the prepended passwords should be used.
     * @param prependedPasswords
     *            known passwords tried before the wordlist. May be {@code null}.
     * @param warnSizeBytes
     *            if greater than zero and the wordlist file is at least this
     *            size, a warning is logged as cracking may take a long time.
     */
    public PasswordDictionary(Path wordlistFile, List<String> prependedPasswords, long warnSizeBytes) {
        this.wordlistFile = wordlistFile;
        this.prependedPasswords = prependedPasswords;
        warnIfHuge(warnSizeBytes);
    }

    private void warnIfHuge(long warnSizeBytes) {
        if (wordlistFile == null || warnSizeBytes <= 0) {
            return;
        }
        try {
            if (Files.isRegularFile(wordlistFile)) {
                long size = Files.size(wordlistFile);
                if (size >= warnSizeBytes) {
                    logger.error(
                            "Password dictionary '{}' is large ({} MB). Cracking may take a long time; "
                                    + "consider adjusting the crack time limit in PasswordCrackingConfig.txt.",
                            wordlistFile, size / (1024 * 1024));
                }
            }
        } catch (IOException e) {
            // ignore, this is only a best-effort warning
        }
    }

    @Override
    public Iterator<String> iterator() {
        return new PasswordDictionaryIterator(wordlistFile, prependedPasswords);
    }

    private static class PasswordDictionaryIterator implements Iterator<String> {

        // Phase 1: In-memory targeted passwords
        private final Iterator<String> prependedIterator;

        // Phase 2: Disk-based passwords, read incrementally from a single wordlist file
        private BufferedReader currentReader;
        private String nextLine;

        public PasswordDictionaryIterator(Path wordlistFile, List<String> prependedPasswords) {
            this.prependedIterator = prependedPasswords != null ? prependedPasswords.iterator()
                    : Collections.emptyIterator();

            // Only open the wordlist if an existing file was actually provided
            if (wordlistFile != null && Files.isRegularFile(wordlistFile)) {
                try {
                    this.currentReader = Files.newBufferedReader(wordlistFile);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to open dictionary file: " + wordlistFile, e);
                }
            }
        }

        private void advanceToNextLine() {
            if (nextLine != null) {
                return;
            }
            if (currentReader == null) {
                return; // No wordlist or already exhausted
            }
            try {
                nextLine = currentReader.readLine();
                if (nextLine == null) {
                    currentReader.close();
                    currentReader = null;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error reading from dictionary file", e);
            }
        }

        @Override
        public boolean hasNext() {
            // Phase 1: Always exhaust the prepended list first
            if (prependedIterator.hasNext()) {
                return true;
            }

            // Phase 2: Move on to the wordlist file
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
