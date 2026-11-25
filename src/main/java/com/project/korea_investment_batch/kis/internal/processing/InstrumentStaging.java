package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import com.project.korea_investment_batch.shared.CollectionRun;
import java.nio.file.Path;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.jdbc.core.JdbcTemplate;

public final class InstrumentStaging {
    private InstrumentStaging() {}

    public record StagedInstrument(long rowNumber, KisLineParser.Instrument instrument) {
        public boolean supported() {
            return instrument.type() != null;
        }
    }

    public static FlatFileItemReader<StagedInstrument> reader(CollectionRun run, KisModule.Input input, BatchInputStore store) {
        if (!KisMasterFormats.PARSER_VERSION.equals(input.parserVersion())) {
            throw new IllegalStateException("Unsupported stored KIS parser version: " + input.parserVersion());
        }
        var format = KisMasterFormats.forMarket(run.market());
        var parser = format.parser();
        var path = Path.of(input.archivePath());
        var reader = new FlatFileItemReader<StagedInstrument>(new org.springframework.core.io.FileSystemResource(path),
                (line, row) -> new StagedInstrument(row, parser.parse(line)));
        reader.setName("kisArchiveReader");
        reader.setComments(new String[0]);
        reader.setBufferedReaderFactory((resource, encoding) ->
                store.open(path, format.entryName(), input.archiveSha256()));
        return reader;
    }

    public static void write(
            JdbcTemplate jdbc,
            long inputId,
            Iterable<? extends StagedInstrument> chunk,
            CollectionCheckpoint checkpoint
    ) {
        List<Object[]> rows = new ArrayList<>();
        for (StagedInstrument row : chunk) {
            var instrument = row.instrument();
            rows.add(new Object[]{inputId, row.rowNumber(), instrument.symbol(), instrument.standardCode(),
                    instrument.name(), instrument.englishName(), instrument.type().name(), instrument.securityCode(),
                    instrument.productCode(), instrument.suspended()});
        }
        int[] types = {
                Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN
        };
        jdbc.batchUpdate("""
                INSERT INTO kis_stage (input_id, original_row_no, symbol, standard_code, name, english_name,
                                       instrument_type, security_code, product_code, suspended)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE symbol = incoming.symbol, standard_code = incoming.standard_code,
                    name = incoming.name, english_name = incoming.english_name, instrument_type = incoming.instrument_type,
                    security_code = incoming.security_code, product_code = incoming.product_code, suspended = incoming.suspended
                """, rows, types);
        CollectionCheckpoint.afterCommit(checkpoint, "kis.stage.after-commit");
    }
}
