package com.project.korea_investment_batch.support;

import com.project.korea_investment_batch.shared.Market;

public final class KisFixtures {
    private KisFixtures() {}

    static String archiveEntry(Market market) {
        return switch (market) {
            case KOSPI -> "kospi_code.mst";
            case KOSDAQ -> "kosdaq_code.mst";
            case NASDAQ -> "NASMST.COD";
            case NYSE -> "NYSMST.COD";
            case AMEX -> "AMSMST.COD";
        };
    }

    public static String domestic(Market market, String symbol, String name, String group, char product, char halt) {
        boolean kospi = market == Market.KOSPI;
        char[] suffix = " ".repeat(kospi ? 227 : 221).toCharArray();
        suffix[0] = group.charAt(0);
        suffix[1] = group.charAt(1);
        suffix[kospi ? 22 : 18] = product;
        suffix[kospi ? 60 : 55] = halt;
        return "%-9s%s%s%s".formatted(symbol, "KR7000000001", name, new String(suffix));
    }

    public static String american(Market market, String symbol, String security, String product) {
        String[] columns = new String[24];
        java.util.Arrays.fill(columns, "");
        columns[0] = "US";
        columns[2] = switch (market) {
            case AMEX -> "AMS";
            case NASDAQ -> "NAS";
            case NYSE -> "NYS";
            default -> throw new IllegalArgumentException();
        };
        columns[4] = symbol;
        columns[6] = "한글 " + symbol;
        columns[7] = "English " + symbol;
        columns[8] = security;
        columns[9] = "USD";
        columns[22] = product;
        return String.join("\t", columns);
    }
}
