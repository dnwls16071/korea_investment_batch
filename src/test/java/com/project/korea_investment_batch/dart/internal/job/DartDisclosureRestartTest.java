package com.project.korea_investment_batch.dart.internal.job;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DartDisclosureRestartTest extends BatchIntegrationSupport {
    private static final CollectionRun RUN = run(Market.KOSPI, LocalDate.of(2026, 9, 1));
    private final DartModule dart;
    private final DataSource dataSource;

    @Autowired
    DartDisclosureRestartTest(
            DartModule dart,
            DataSource dataSource
    ) {
        this.dart = dart;
        this.dataSource = dataSource;
    }

    @Test
    void readsAndWritesOnePagePerChunkWithoutHoldingAChunkConnectionDuringHttp() throws Exception {
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        var activeConnections = new AtomicInteger();
        responses.put("/dart", request -> {
            activeConnections.accumulateAndGet(pool.getActiveConnections(), Math::max);
            return request.query().get("page_no").equals("1")
                    ? page(request, 4, 9, 3) : page(request, 4, 4, 5);
        });

        var execution = dart.resume(RUN, false);
        assertThat(execution.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.steps()).filteredOn(step -> step.name().equals("fetchDisclosurePages"))
                .singleElement().satisfies(step -> {
                    assertThat(step.reads()).isEqualTo(2);
                    assertThat(step.writes()).isEqualTo(2);
                    assertThat(step.commits()).isEqualTo(2);
                });
        assertThat(activeConnections).hasValue(1);
        assertCompleted(1);
    }

    @Test
    void reusesTheLastPageWhenTheWorkerExitsAfterCommittingTheCompleteAttempt() throws Exception {
        responses.put("/dart", request -> page(request, 1, 101));
        long instance;
        try (var worker = worker("dart", RUN, "dart.fetch.after-page")) {
            worker.awaitCheckpoint();
            instance = dart.execution(RUN).orElseThrow().instanceId();
            assertThat(dart.scanResult(RUN).orElseThrow().completeAttempt()).isEqualTo(1);
            assertThat(dart.disclosures(RUN.market(), RUN.date())).isEmpty();
        }

        requests.clear();
        responses.remove("/dart");
        var recovered = dart.resume(RUN, true);
        assertThat(recovered.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recovered.instanceId()).isEqualTo(instance);
        assertThat(requestedPages()).isEmpty();
        assertThat(dart.disclosures(RUN.market(), RUN.date())).extracting(DartModule.Disclosure::receiptNumber)
                .containsExactly(id(101));
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "count", "empty", "wrong-page"})
    void detectsIncompletePagesAndRestartsFromOneInSameRevision(String failure) {
        responses.put("/dart", request -> {
            if (request.query().get("page_no").equals("1")) {
                return page(request, 4, 1, 2);
            }
            return switch (failure) {
                case "duplicate" -> page(request, 4, 2, 3);
                case "count" -> page(request, 4, 3);
                case "wrong-page" -> page(request, 1, 4, new int[]{3, 4});
                default -> json("{\"status\":\"013\"}");
            };
        });
        var first = dart.resume(RUN, false);
        assertThat(first.status()).isEqualTo(BatchStatus.FAILED);
        var incomplete = dart.scanResult(RUN).orElseThrow();
        assertThat(incomplete.completeAttempt()).isNull();
        assertThat(incomplete.state()).isEqualTo("INCOMPLETE");
        assertThat(incomplete.failureStep()).isEqualTo("fetchDisclosurePages");
        assertThat(dart.disclosures(RUN.market(), RUN.date())).isEmpty();
        assertThat(requestedPages()).containsExactly(1, 2);

        requests.clear();
        normalPages();
        var second = dart.resume(RUN, false);
        assertThat(second.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.instanceId()).isEqualTo(first.instanceId());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(requestedPages()).containsExactly(1, 2);
        assertCompleted(2);
    }

    @Test
    void emptyAndRequestLimitAreDistinctAndTransientServerErrorsAreRetriedOnce() {
        AtomicInteger kospiCalls = new AtomicInteger();
        responses.put("/dart", request -> {
            if (request.query().get("corp_cls").equals("K")) {
                return json("{\"status\":\"013\"}");
            }
            return kospiCalls.incrementAndGet() == 1 ? Reply.error(503) : json("{\"status\":\"020\"}");
        });
        var empty = run(Market.KOSDAQ, RUN.date());
        assertThat(dart.resume(empty, false).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(dart.scanResult(empty).orElseThrow().newCount()).isZero();
        assertThat(dart.resume(RUN, false).status()).isEqualTo(BatchStatus.FAILED);
        assertThat(kospiCalls.get()).isEqualTo(2);
        assertThat(dart.scanResult(RUN).orElseThrow().failureReason()).contains("020");

        requests.clear();
        responses.put("/dart", ignored -> Reply.error(503));
        assertThat(dart.resume(RUN, false).status()).isEqualTo(BatchStatus.FAILED);
        assertThat(requestedPages()).containsExactly(1, 1);
        assertThat(dart.scanResult(RUN).orElseThrow().failureReason()).contains("503");

        requests.clear();
        normalPages();
        assertThat(dart.resume(RUN, false).status()).isEqualTo(BatchStatus.COMPLETED);
        assertCompleted(3);
    }

    @Test
    void reusesCompletePagesAfterRollbackBeforePublicationCommit() {
        normalPages();
        fault.failAt("dart.publish.before-commit");
        assertThat(dart.resume(RUN, false).status()).isEqualTo(BatchStatus.FAILED);
        var scan = dart.scanResult(RUN).orElseThrow();
        assertThat(scan.completeAttempt()).isEqualTo(1);
        assertThat(scan.newCount()).isNull();
        assertThat(scan.failureStep()).isEqualTo("publishDisclosures");
        assertThat(dart.disclosures(RUN.market(), RUN.date())).isEmpty();

        requests.clear();
        assertThat(dart.resume(RUN, false).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(requestedPages()).isEmpty();
        assertCompleted(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recoversKilledWorkerInSameOldRevisionAndPreservesCommittedResult(boolean committed) throws Exception {
        if (committed) {
            normalPages();
        } else {
            responses.put("/dart", request -> page(request, 4, 1, 2));
        }
        long instance;
        try (var worker = worker("dart", RUN, committed ? "dart.publish.after-commit" : "dart.fetch.after-page")) {
            worker.awaitCheckpoint();
            var active = dart.execution(RUN).orElseThrow();
            assertThat(active.status()).isEqualTo(BatchStatus.STARTED);
            instance = active.instanceId();
            assertThatThrownBy(() -> dart.resume(RUN, true)).hasMessageContaining("still active");
            if (committed) {
                assertCompleted(1);
            } else {
                assertThat(dart.scanResult(RUN).orElseThrow().completeAttempt()).isNull();
                assertThat(dart.disclosures(RUN.market(), RUN.date())).isEmpty();
                assertThat(requestedPages()).containsExactly(1);
            }
        }

        requests.clear();
        normalPages();
        assertThatThrownBy(() -> dart.resume(RUN, false)).hasMessageContaining("previous process");
        var recovered = dart.resume(RUN, true);
        assertThat(recovered.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recovered.instanceId()).isEqualTo(instance);
        if (committed) {
            assertThat(requestedPages()).isEmpty();
            assertCompleted(1);
        } else {
            assertThat(requestedPages()).containsExactly(1, 2);
            assertCompleted(2);
        }
    }

    private void normalPages() {
        responses.put("/dart", request -> request.query().get("page_no").equals("1")
                ? page(request, 4, 9, 3) : page(request, 4, 4, 5));
    }

    private void assertCompleted(int attempt) {
        var scan = dart.scanResult(RUN).orElseThrow();
        assertThat(scan.scanRevision()).isEqualTo(1);
        assertThat(scan.completeAttempt()).isEqualTo(attempt);
        assertThat(scan.newCount()).isEqualTo(4);
        assertThat(scan.state()).isEqualTo("COMPLETED");
        assertThat(dart.disclosures(RUN.market(), RUN.date())).extracting(DartModule.Disclosure::receiptNumber)
                .containsExactly(id(3), id(4), id(5), id(9));
    }
}
