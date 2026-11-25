package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.kis.internal.KisProperties;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DownloadArchiveTasklet {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate archiveMetadataTransaction;
    private final BatchInputStore store;
    private final HttpClient http;
    private final KisProperties settings;

    DownloadArchiveTasklet(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            BatchInputStore store,
            HttpClient http,
            KisProperties settings
    ) {
        this.jdbc = jdbc;
        this.archiveMetadataTransaction = new TransactionTemplate(transactionManager);
        this.archiveMetadataTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.store = store;
        this.http = http;
        this.settings = settings;
    }

    public void download(CollectionRun run, KisModule.Input input, CollectionCheckpoint checkpoint)
            throws IOException, InterruptedException {
        if (input.archivePath() != null) {
            return;
        }
        var format = KisMasterFormats.forMarket(run.market());
        var path = store.archivePath(run);
        BatchInputStore.Archive archive;
        if (Files.exists(path)) {
            // The worker may have exited between freezing the archive and committing its DB reference.
            archive = store.inspect(path, format.entryName());
        } else {
            var archiveUrl = UriComponentsBuilder.fromUri(settings.archiveBaseUrl())
                    .pathSegment(format.zipFileName()).build(true).toUri();
            var request = HttpRequest.newBuilder(archiveUrl).timeout(settings.timeout()).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("KIS HTTP " + response.statusCode());
            }
            LocalDate effectiveDate = response.headers().firstValue("X-Source-Effective-Date")
                    .map(LocalDate::parse).orElse(null);
            archive = store.save(run, response.body(), effectiveDate, format.entryName());
            checkpoint.reached("kis.archive.after-freeze");
        }
        archiveMetadataTransaction.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE kis_input SET archive_path = ?, archive_sha256 = ?, collected_at = ?, effective_date = ?,
                                         parser_version = ?, state = 'ARCHIVED' WHERE id = ?
                    """, archive.path().toString(), archive.sha256(), Timestamp.from(archive.collectedAt()),
                    archive.effectiveDate(), KisMasterFormats.PARSER_VERSION, input.id());
            checkpoint.reached("kis.archive.before-metadata-commit");
        });
    }
}
