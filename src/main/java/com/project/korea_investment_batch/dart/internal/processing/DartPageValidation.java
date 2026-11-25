package com.project.korea_investment_batch.dart.internal.processing;

import com.project.korea_investment_batch.dart.internal.DartMarkets;
import com.project.korea_investment_batch.dart.remote.OpenDartClient;
import com.project.korea_investment_batch.shared.CollectionRun;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import org.springframework.batch.infrastructure.item.ItemProcessor;

public class DartPageValidation implements ItemProcessor<DisclosurePage, DisclosurePage.Validated> {

    private final String date;
    private final String market;
    private final int pageSize;
    private final Set<String> receiptNumbers = new HashSet<>();
    private int pageCount;
    private int totalCount;
    private int acceptedPages;

    public DartPageValidation(
            CollectionRun run,
            int pageSize
    ) {
        this.date = run.date().format(DateTimeFormatter.BASIC_ISO_DATE);
        this.market = DartMarkets.companyClass(run.market());
        this.pageSize = pageSize;
    }

    @Override
    public DisclosurePage.Validated process(DisclosurePage page) {
        accept(page.number(), page.response());
        boolean lastPage = page.number() == pageCount;
        if (lastPage) {
            verifyComplete();
        }
        return new DisclosurePage.Validated(page, lastPage);
    }

    void accept(int number, OpenDartClient.Page page) {
        require(number == acceptedPages + 1, "Missing or out-of-order OpenDART page");
        // OpenDART defines 013 as no data, 000 as success, and the response fields used for completeness checks.
        // https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001
        if (number == 1 && "013".equals(page.status())) {
            require((page.receipts() == null || page.receipts().isEmpty())
                    && (page.totalCount() == null || page.totalCount() == 0)
                    && (page.number() == null || page.number() == 1), "Inconsistent empty OpenDART response");
            pageCount = 1;
            acceptedPages = 1;
            return;
        }
        require("000".equals(page.status()), "OpenDART status " + safeStatus(page.status()) + " on page " + number);
        require(page.number() != null && page.number() == number
                && page.pageSize() != null && page.pageSize() == pageSize, "Unexpected OpenDART page number or size");
        if (number == 1) {
            require(page.totalCount() != null && page.totalCount() > 0 && page.totalPages() != null
                    && page.totalPages() == (page.totalCount() - 1) / pageSize + 1, "Invalid OpenDART page or receipt count");
            pageCount = page.totalPages();
            totalCount = page.totalCount();
        }
        require(number <= pageCount && page.totalPages() != null && page.totalPages() == pageCount
                && page.totalCount() != null && page.totalCount() == totalCount, "OpenDART page or receipt count changed");
        require(page.receipts() != null && !page.receipts().isEmpty() && page.receipts().size() <= pageSize,
                "Invalid OpenDART page receipt count");
        for (OpenDartClient.Receipt receipt : page.receipts()) {
            require(receipt != null && receipt.receiptNumber() != null && receipt.receiptNumber().matches("[0-9]{14}")
                    && date.equals(receipt.receiptDate()) && market.equals(receipt.market())
                    && (receipt.stockCode() == null || receipt.stockCode().isBlank() || receipt.stockCode().matches("[A-Z0-9]{6}"))
                    && present(receipt.company(), 300) && present(receipt.title(), 500),
                    "Invalid OpenDART receipt on page " + number);
            require(receiptNumbers.add(receipt.receiptNumber()), "Duplicate OpenDART receipt " + receipt.receiptNumber());
        }
        acceptedPages++;
    }

    void verifyComplete() {
        require(acceptedPages == pageCount && acceptedPages > 0 && receiptNumbers.size() == totalCount,
                "OpenDART receipt count does not match reported total");
    }

    private static boolean present(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit;
    }

    private static String safeStatus(String status) {
        return status != null && status.matches("[0-9]{3}") ? status : "unknown";
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
