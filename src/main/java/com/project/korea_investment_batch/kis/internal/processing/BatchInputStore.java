package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.kis.internal.KisProperties;
import com.project.korea_investment_batch.shared.CollectionRun;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

@Component
public class BatchInputStore {

    // KIS decodes both master formats as CP949.
    // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py#L35-L37
    // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py#L33-L37
    private static final Charset CP949 = Charset.forName("x-windows-949");
    private static final String ZIP = "archive.zip";
    private static final String EFFECTIVE_DATE = "effective-date";
    private final Path root;

    BatchInputStore(KisProperties properties) {
        root = properties.archiveRoot().toAbsolutePath().normalize();
    }

    public Path archivePath(CollectionRun run) {
        return root.resolve(run.market().name()).resolve(run.date().toString())
                .resolve(Long.toString(run.revision())).resolve(ZIP);
    }

    public Archive save(CollectionRun run, byte[] body, LocalDate effectiveDate, String entryName) throws IOException {
        Path archive = archivePath(run);
        Path directory = archive.getParent();
        Files.createDirectories(directory.getParent());
        Path temporary = Files.createTempDirectory(directory.getParent(), "download-");
        try {
            Files.write(temporary.resolve(ZIP), body);
            if (effectiveDate != null) {
                Files.writeString(temporary.resolve(EFFECTIVE_DATE), effectiveDate.toString());
            }
            Archive prepared = inspect(temporary.resolve(ZIP), entryName);
            Files.move(temporary, directory, StandardCopyOption.ATOMIC_MOVE);
            return new Archive(archive, prepared.sha256(), prepared.collectedAt(), prepared.effectiveDate());
        } finally {
            Files.deleteIfExists(temporary.resolve(EFFECTIVE_DATE));
            Files.deleteIfExists(temporary.resolve(ZIP));
            Files.deleteIfExists(temporary);
        }
    }

    public Archive inspect(Path path, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = expectedEntry(zip, entryName);
            try (var bytes = new CheckedInputStream(zip.getInputStream(entry), new CRC32())) {
                long size = bytes.transferTo(OutputStream.nullOutputStream());
                if (size != entry.getSize() || bytes.getChecksum().getValue() != entry.getCrc()) {
                    throw new IOException("KIS ZIP entry failed size or CRC validation");
                }
            }
        }
        Path dateFile = path.resolveSibling(EFFECTIVE_DATE);
        LocalDate effectiveDate = Files.exists(dateFile) ? LocalDate.parse(Files.readString(dateFile)) : null;
        return new Archive(path, checksum(path), Files.getLastModifiedTime(path).toInstant(), effectiveDate);
    }

    public BufferedReader open(Path path, String entryName, String expectedSha256) throws IOException {
        if (!checksum(path).equals(expectedSha256)) {
            throw new IOException("Stored KIS archive SHA-256 mismatch");
        }
        ZipFile zip = new ZipFile(path.toFile());
        try {
            var bytes = new FilterInputStream(zip.getInputStream(expectedEntry(zip, entryName))) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        zip.close();
                    }
                }
            };
            return new BufferedReader(new InputStreamReader(bytes, CP949.newDecoder()));
        } catch (IOException | RuntimeException e) {
            zip.close();
            throw e;
        }
    }

    private static ZipEntry expectedEntry(ZipFile zip, String entryName) throws IOException {
        var entries = zip.stream().filter(entry -> !entry.isDirectory() && entry.getName().equalsIgnoreCase(entryName))
                .toList();
        if (zip.size() != 1 || entries.size() != 1) {
            throw new IOException("Expected one ZIP entry named " + entryName);
        }
        return entries.getFirst();
    }

    private static String checksum(Path path) throws IOException {
        try (var input = new DigestInputStream(Files.newInputStream(path), MessageDigest.getInstance("SHA-256"))) {
            input.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(input.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Archive(Path path, String sha256, Instant collectedAt, LocalDate effectiveDate) {}
}
