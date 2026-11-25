package com.project.korea_investment_batch.operations;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import javax.management.ObjectName;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.observability.jfr.events.job.JobExecutionEvent;
import org.springframework.batch.core.observability.jfr.events.step.chunk.ChunkTransactionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static com.project.korea_investment_batch.support.KisFixtures.domestic;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jmx.enabled=true")
class BatchObservabilityIntegrationTest extends BatchIntegrationSupport {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private final ApplicationContext context;
    private final KisModule kis;
    private final DartModule dart;

    @Autowired
    BatchObservabilityIntegrationTest(
            ApplicationContext context,
            KisModule kis,
            DartModule dart
    ) {
        this.context = context;
        this.kis = kis;
        this.dart = dart;
    }

    @Test
    void collectsBatchTimersAndExposesMetricsThroughLocalJmx() throws Exception {
        var registry = context.getBean(MeterRegistry.class);
        archive(Market.KOSPI, domestic(Market.KOSPI, "000001", "종목", "ST", ' ', 'N'));
        assertThat(kis.resume(run(Market.KOSPI, DATE), false).status()).isEqualTo(BatchStatus.COMPLETED);

        responses.put("/dart", request -> page(request, 1, 101));
        assertThat(dart.scan(Market.KOSPI, DATE).status()).isEqualTo(BatchStatus.COMPLETED);
        responses.clear();
        assertThat(dart.scan(Market.KOSPI, DATE).status()).isEqualTo(BatchStatus.FAILED);

        assertThat(registry.get("spring.batch.job").tags("spring.batch.job.name", "marketMasterImportJob",
                "spring.batch.job.status", ExitStatus.COMPLETED.getExitCode()).timer().count()).isPositive();
        assertThat(registry.get("spring.batch.job").tags("spring.batch.job.name", "dartDisclosureImportJob",
                "spring.batch.job.status", ExitStatus.COMPLETED.getExitCode()).timer().count()).isEqualTo(1);
        assertThat(registry.get("spring.batch.job").tags("spring.batch.job.name", "dartDisclosureImportJob",
                "spring.batch.job.status", ExitStatus.FAILED.getExitCode()).timer().count()).isEqualTo(1);
        assertThat(registry.get("spring.batch.step").tag("spring.batch.step.name", "stageInstruments")
                .timer().count()).isPositive();
        assertThat(registry.get("spring.batch.chunk.write").tag("spring.batch.chunk.write.step.name", "stageInstruments")
                .timer().count()).isPositive();
        var metrics = ObjectName.getInstance("org.springframework.boot:type=Endpoint,name=Metrics");
        var result = ManagementFactory.getPlatformMBeanServer().invoke(metrics, "listNames", new Object[0], new String[0]);
        assertThat(result).isInstanceOfSatisfying(Map.class, response -> {
            assertThat(response.get("names")).asString().contains("spring.batch.job", "spring.batch.step", "spring.batch.chunk.write");
        });
    }

    @Test
    void recordsJobExecutionAndCommittedChunkInJfr(@TempDir Path temporary) throws Exception {
        archive(Market.KOSPI, domestic(Market.KOSPI, "000001", "종목", "ST", ' ', 'N'));
        Path file = temporary.resolve("batch.jfr");
        try (var recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.start();
            assertThat(kis.resume(run(Market.KOSPI, DATE), false).status()).isEqualTo(BatchStatus.COMPLETED);
            recording.stop();
            recording.dump(file);
        }

        var events = RecordingFile.readAllEvents(file);
        assertThat(events).filteredOn(event -> event.getEventType().getName().equals(JobExecutionEvent.class.getName()))
                .anySatisfy(event -> {
                    assertThat(event.getString("jobName")).isEqualTo("marketMasterImportJob");
                    assertThat(event.getString("exitStatus")).isEqualTo(ExitStatus.COMPLETED.getExitCode());
                });
        assertThat(events).filteredOn(event -> event.getEventType().getName().equals(ChunkTransactionEvent.class.getName()))
                .anySatisfy(event -> {
                    assertThat(event.getString("stepName")).isEqualTo("stageInstruments");
                    assertThat(event.getString("transactionStatus")).isEqualTo("COMMITTED");
                });
    }
}
