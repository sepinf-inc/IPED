package iped.engine.task;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.io.TikaInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EmbeddedOfficeContainerTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void reopensSummaryMetadataAfterExtractionContainerCloses() throws Exception {
        Path file = temporary.newFile("synthetic.doc").toPath();
        try (POIFSFileSystem created = new POIFSFileSystem()) {
            SummaryInformation summary = PropertySetFactory.newSummaryInformation();
            summary.setAuthor("Synthetic author");
            summary.setTitle("Synthetic title");
            summary.write(created.getRoot(), SummaryInformation.DEFAULT_STREAM_NAME);
            try (java.io.OutputStream out = Files.newOutputStream(file)) {
                created.writeFilesystem(out);
            }
        }
        Object transferred;
        try (TikaInputStream extraction = TikaInputStream.get(file)) {
            POIFSFileSystem owned = new POIFSFileSystem(file.toFile(), true);
            extraction.setOpenContainer(owned);
            transferred = ParsingTask.getEmbeddedOpenContainer(extraction);
            // Model the parser closing its file-backed container before queued parsing.
            owned.close();
        }
        try (TikaInputStream child = TikaInputStream.get(file)) {
            child.setOpenContainer(transferred);
            Object cached = child.getOpenContainer();
            try (POIFSFileSystem reopened = cached instanceof POIFSFileSystem
                    ? (POIFSFileSystem) cached : new POIFSFileSystem(child)) {
                SummaryInformation summary = (SummaryInformation) PropertySetFactory.create(
                        reopened.getRoot(), SummaryInformation.DEFAULT_STREAM_NAME);
                assertEquals("Synthetic author", summary.getAuthor());
                assertEquals("Synthetic title", summary.getTitle());
            }
        }
    }

    @Test
    public void preservesOtherParserContainers() throws Exception {
        Object custom = new Object();
        try (TikaInputStream stream = TikaInputStream.get(new ByteArrayInputStream(new byte[0]))) {
            stream.setOpenContainer(custom);
            assertSame(custom, ParsingTask.getEmbeddedOpenContainer(stream));
        }
    }

    @Test
    public void handlesMissingContainer() throws Exception {
        try (TikaInputStream stream = TikaInputStream.get(new ByteArrayInputStream(new byte[0]))) {
            assertNull(ParsingTask.getEmbeddedOpenContainer(stream));
        }
    }
}
