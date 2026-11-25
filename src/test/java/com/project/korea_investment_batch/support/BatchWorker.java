package com.project.korea_investment_batch.support;

import com.project.korea_investment_batch.KoreaInvestmentBatchApplication;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.kis.KisModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class BatchWorker {
    public static void main(String[] args) {
        try (var application = new SpringApplicationBuilder(KoreaInvestmentBatchApplication.class, LocalCollectionConfiguration.class)
                .profiles("test").initializers(context -> context.getBeanFactory().registerSingleton("workerCheckpoint",
                        (CollectionCheckpoint) name -> {
                            if (name.equals(args[0])) {
                                try {
                                    Files.createFile(Path.of(args[1]));
                                    new CountDownLatch(1).await();
                                } catch (Exception e) {
                                    throw new IllegalStateException(e);
                                }
                            }
                        })).run(Arrays.copyOfRange(args, 6, args.length))) {
            var run = new CollectionRun(Market.valueOf(args[3]), LocalDate.parse(args[4]), Long.parseLong(args[5]));
            if (args[2].equals("kis")) {
                application.getBean(KisModule.class).resume(run, false);
            } else {
                application.getBean(DartModule.class).resume(run, false);
            }
        }
    }
}
