package com.project.korea_investment_batch.dart.internal.processing;

import com.project.korea_investment_batch.dart.remote.OpenDartClient;

public record DisclosurePage(long scanId, int attempt, int number, OpenDartClient.Page response) {

    public record Validated(DisclosurePage page, boolean lastPage) {}
}
