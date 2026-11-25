package com.project.korea_investment_batch;

import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class KoreaInvestmentBatchApplicationTests extends BatchIntegrationSupport {
    @Autowired JobRepository repository;

    @Test
    void loadsBatchRepositoryAgainstMysql() {
        assertThat(repository.getJobNames()).isEmpty();
    }
}
