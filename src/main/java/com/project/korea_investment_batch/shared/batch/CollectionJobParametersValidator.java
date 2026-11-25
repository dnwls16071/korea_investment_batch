package com.project.korea_investment_batch.shared.batch;

import com.project.korea_investment_batch.shared.Market;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

@RequiredArgsConstructor
public final class CollectionJobParametersValidator implements JobParametersValidator {

    private final String dateKey;
    private final String revisionKey;
    private final List<Market> supportedMarkets;

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        String marketName = required(parameters, "market", String.class);
        required(parameters, dateKey, LocalDate.class);
        long revision = required(parameters, revisionKey, Long.class);

        if (supportedMarkets.stream().noneMatch(market -> market.name().equals(marketName))) {
            throw new InvalidJobParametersException("market must be one of " + supportedMarkets);
        }
        if (revision < 1) {
            throw new InvalidJobParametersException(revisionKey + " must be positive");
        }
    }

    private static <T> T required(JobParameters parameters, String key, Class<T> type) throws InvalidJobParametersException {
        var parameter = parameters.getParameter(key);
        if (parameter == null) {
            throw new InvalidJobParametersException("Missing required job parameter " + key);
        }
        if (!type.equals(parameter.type())) {
            throw new InvalidJobParametersException(key + " must be of type " + type.getSimpleName());
        }
        return type.cast(parameter.value());
    }
}
