package com.project.korea_investment_batch.operations;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.CollectionExecution;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class CollectionOperationsCommand implements ApplicationRunner {
    private final KisModule kis;
    private final DartModule dart;

    @Override
    public void run(ApplicationArguments arguments) {
        List<String> args = arguments.getNonOptionArgs();
        if (args.isEmpty()) {
            return;
        }
        if (args.size() < 3 || args.size() > 5 || !List.of("kis", "dart").contains(args.getFirst())) {
            throw usage();
        }
        boolean isKis = args.getFirst().equals("kis");
        String action = args.get(1);
        Market market = Market.valueOf(args.get(2));
        if (isKis && action.equals("latest") && args.size() == 3) {
            System.out.println(kis.latest(market));
            System.out.println(kis.lastAttempt(market));
            kis.instruments(market).forEach(System.out::println);
            return;
        }
        if (args.size() < 4) {
            throw usage();
        }
        LocalDate date = LocalDate.parse(args.get(3));
        if (args.size() == 4) {
            switch (action) {
                case "scan" -> {
                    if (!isKis) {
                        requireSuccess(dart.scan(market, date));
                        return;
                    }
                }
                case "list" -> {
                    if (!isKis) {
                        dart.disclosures(market, date)
                                .forEach(row -> System.out.println(row + " " + row.originalLink()));
                        return;
                    }
                }
                case "changes" -> {
                    if (isKis) {
                        kis.changes(market, date).forEach(System.out::println);
                        return;
                    }
                }
                default -> { }
            }
            throw usage();
        }

        CollectionRun run = new CollectionRun(market, date, Long.parseLong(args.get(4)));
        switch (action) {
            case "status" -> {
                if (isKis) {
                    printExecution(kis.execution(run));
                    System.out.println(kis.input(run));
                } else {
                    printExecution(dart.execution(run));
                    System.out.println(dart.scanResult(run));
                }
            }
            case "resume" -> {
                boolean confirmed = arguments.containsOption("confirm-previous-process-exited");
                requireSuccess(isKis ? kis.resume(run, confirmed) : dart.resume(run, confirmed));
            }
            default -> throw usage();
        }
    }

    private static void printExecution(Optional<CollectionExecution> execution) {
        execution.ifPresentOrElse(job -> {
            System.out.printf("JobInstance=%d JobExecution=%d status=%s%n",
                    job.instanceId(), job.id(), job.status());
            job.steps()
                    .forEach(step -> System.out.printf("%s %s commits=%d reads=%d writes=%d%n", step.name(),
                            step.status(), step.commits(), step.reads(), step.writes()));
        }, () -> System.out.println("No job execution"));
    }

    private static void requireSuccess(CollectionExecution job) {
        printExecution(Optional.of(job));
        if (!job.completed()) {
            throw new IllegalStateException("Collection execution " + job.id() + " finished with " + job.status());
        }
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException("""
                kis|dart status|resume MARKET YYYY-MM-DD REVISION [--confirm-previous-process-exited]
                kis latest MARKET
                kis changes MARKET YYYY-MM-DD
                dart scan|list MARKET YYYY-MM-DD
                """);
    }
}
