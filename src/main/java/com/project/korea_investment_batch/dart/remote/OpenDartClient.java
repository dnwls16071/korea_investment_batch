package com.project.korea_investment_batch.dart.remote;

import com.project.korea_investment_batch.dart.internal.DartMarkets;
import com.project.korea_investment_batch.dart.internal.DartProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.korea_investment_batch.shared.CollectionRun;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class OpenDartClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    @Qualifier("openDartEndpoint")
    private final URI endpoint;
    private final DartProperties settings;

    public Page page(CollectionRun run, int number) throws IOException, InterruptedException {
        if (settings.apiKey().isBlank()) {
            throw new IllegalStateException("DART_API_KEY is required for OpenDART");
        }
        // OpenDART 공시검색 specifies the date format, request options, and paging fields.
        // https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001
        String date = run.date().format(DateTimeFormatter.BASIC_ISO_DATE);
        URI url = UriComponentsBuilder.fromUri(endpoint)
                .queryParam("crtfc_key", "{key}")
                .queryParam("bgn_de", date).queryParam("end_de", date)
                .queryParam("corp_cls", DartMarkets.companyClass(run.market()))
                .queryParam("pblntf_ty", "B").queryParam("last_reprt_at", "N")
                .queryParam("page_no", number).queryParam("page_count", settings.pageSize())
                .queryParam("sort", "date").queryParam("sort_mth", "desc")
                .encode().buildAndExpand(settings.apiKey()).toUri();
        var request = HttpRequest.newBuilder(url).timeout(settings.timeout()).GET().build();
        for (int attempt = 0; ; attempt++) {
            try {
                var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 200) {
                    return decode(response.body());
                }
                if (attempt >= settings.retries() || (status != 500 && status != 502 && status != 503 && status != 504)) {
                    throw new IOException("OpenDART HTTP " + status + " on page " + number);
                }
            } catch (HttpTimeoutException e) {
                if (attempt >= settings.retries()) {
                    throw new IOException("OpenDART timed out on page " + number);
                }
            } catch (IOException e) {
                // Do not attach transport exceptions because their messages may include the request URI.
                throw new IOException("OpenDART request failed on page " + number + (e.getMessage() != null && e.getMessage().startsWith("OpenDART HTTP") ? " (" + e.getMessage() + ")" : ""));
            }
            Thread.sleep(settings.retryDelay());
        }
    }

    private Page decode(byte[] body) {
        try {
            return mapper.readValue(body, Page.class);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("Invalid OpenDART JSON response");
        }
    }

    public record Page(
            String status,
            @JsonProperty("page_no") Integer number,
            @JsonProperty("page_count") Integer pageSize,
            @JsonProperty("total_page") Integer totalPages,
            @JsonProperty("total_count") Integer totalCount,
            @JsonProperty("list") List<Receipt> receipts
    ) {}

    public record Receipt(
            @JsonProperty("rcept_no") String receiptNumber,
            @JsonProperty("rcept_dt") String receiptDate,
            @JsonProperty("corp_cls") String market,
            @JsonProperty("stock_code") String stockCode,
            @JsonProperty("corp_name") String company,
            @JsonProperty("report_nm") String title
    ) {}
}
