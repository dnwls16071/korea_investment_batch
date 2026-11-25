package com.project.korea_investment_batch.dart.internal.processing;

import com.project.korea_investment_batch.dart.remote.OpenDartClient;
import com.project.korea_investment_batch.shared.CollectionRun;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

@RequiredArgsConstructor
public class DartPageReader implements ItemStreamReader<DisclosurePage> {
    private final CollectionRun run;
    private final DartDisclosureQueries queries;
    private final DartPageStore store;
    private final OpenDartClient client;
    private long scanId;
    private Integer attempt;
    private int nextPage;
    private int totalPages;

    @Override
    public void open(ExecutionContext executionContext) {
        var scan = queries.scan(run).orElseThrow();
        scanId = scan.id();
        // Resuming at a later page could mix results from different source snapshots.
        attempt = scan.completeAttempt() == null ? store.startAttempt(scanId) : null;
        nextPage = 1;
        totalPages = 1;
    }

    @Override
    public DisclosurePage read() throws IOException, InterruptedException {
        if (attempt == null || nextPage > totalPages) {
            return null;
        }
        var response = client.page(run, nextPage);
        if (nextPage == 1 && "000".equals(response.status()) && response.totalPages() != null) {
            totalPages = response.totalPages();
        }
        return new DisclosurePage(scanId, attempt, nextPage++, response);
    }
}
