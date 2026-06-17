package iped.engine.task.yara;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers YARA-X source rules ({@code .yar}, {@code .yara}) in the
 * directories configured in {@link iped.engine.config.YaraConfig#getRuleDirectories()}.
 *
 * <p>Recursive walks follow symlinks (same semantics as {@code Files.walk} with
 * {@link FileVisitOption#FOLLOW_LINKS}). Non-existent directories produce a WARN and
 * are skipped — they do not fail the case. Pre-compiled formats ({@code .yarc}
 * or YARA-X's own serialization) are out of scope in v1 — see
 * {@code research.md} §R-01 and the revised Clarifications Q3.</p>
 */
public final class YaraRulesetLoader {

    private static final Logger logger = LoggerFactory.getLogger(YaraRulesetLoader.class);

    private YaraRulesetLoader() {
        // Static utility — no instantiation.
    }

    /**
     * Lists all {@code .yar}/{@code .yara} files found, in deterministic order
     * (lexicographic by absolute path) — aids forensic auditability
     * (same input + same catalog → same namespace order).
     */
    public static List<File> discover(List<File> directories) {
        if (directories == null || directories.isEmpty()) {
            return Collections.emptyList();
        }
        List<File> out = new ArrayList<>();
        for (File dir : directories) {
            if (dir == null) {
                continue;
            }
            if (!dir.exists()) {
                logger.warn("YARA rule directory does not exist: {}", dir.getAbsolutePath());
                continue;
            }
            if (!dir.isDirectory()) {
                logger.warn("YARA rule path is not a directory: {}", dir.getAbsolutePath());
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir.toPath(), FileVisitOption.FOLLOW_LINKS)) {
                walk.filter(Files::isRegularFile)
                        .filter(YaraRulesetLoader::isYaraSource)
                        .map(Path::toFile)
                        .forEach(out::add);
            } catch (IOException | UncheckedIOException e) {
                logger.warn("Failed to walk YARA rule directory {}: {}", dir.getAbsolutePath(), e.getMessage());
            }
        }
        out.sort(Comparator.comparing(File::getAbsolutePath));
        return out;
    }

    private static boolean isYaraSource(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yar") || name.endsWith(".yara");
    }
}
