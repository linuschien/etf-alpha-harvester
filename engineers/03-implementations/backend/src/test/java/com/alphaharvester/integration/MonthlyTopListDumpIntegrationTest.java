package com.alphaharvester.integration;

import com.alphaharvester.adapter.out.persistence.DataFeedSyncWatermarkRepository;
import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetPairwiseMatrixRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.domain.entity.GlobalAssetPairwiseMatrix;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.model.CandidateAssetClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class MonthlyTopListDumpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MonthlyTopListDumpIntegrationTest.class);
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private GlobalAssetScoreEvaluationUseCase scoreEvaluationUseCase;

    @Autowired
    private GlobalAssetQueryService queryService;

    @Autowired
    private GlobalAssetScoreRepository scoreRepository;

    @Autowired
    private DcaPopularityRankRepository dcaRankRepository;

    @Autowired
    private GlobalAssetMetadataRepository metadataRepository;

    @Autowired
    private GlobalAssetPairwiseMatrixRepository pairwiseMatrixRepository;

    @Autowired
    private DataFeedSyncWatermarkRepository watermarkRepository;

    @Test
    @DisplayName("Evaluate 2026-09 Monthly Top List and dump into Flyway V8 script")
    void shouldEvaluateAndDumpSeptember2026TopListToFlywayV8() throws IOException {
        log.info("=== Starting Evaluation and Flyway V8 Dump for 2026-09 Monthly Top List ===");

        // 1. Verify prerequisite metadata (V3 seed data) is populated
        long totalMetadata = metadataRepository.count().block();
        log.info("Current universe has {} ETFs in metadataRepository.", totalMetadata);
        assertThat(totalMetadata).isGreaterThanOrEqualTo(300);

        // 2. Clear existing scores & matrices for 2026-09-01 to ensure clean generation
        LocalDateTime evalDate = LocalDateTime.of(2026, 9, 1, 0, 0);
        scoreRepository.deleteByEvaluationDate(evalDate).block();
        pairwiseMatrixRepository.deleteByEvaluationDate(evalDate).block();

        // 3. Trigger Top List evaluation for 2026-09 via Service layer
        GlobalAssetScoreEvaluationResponse response = scoreEvaluationUseCase.evaluateGlobalAssetScores("2026-09", true).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUCCESS");
        log.info("Evaluation completed: status={}, total={}, core={}, satellite={}, defensive={}",
                response.status(), response.evaluatedCandidatesCount(),
                response.coreCount(), response.satelliteCount(), response.defensiveCount());

        // 4. Retrieve evaluated scores and pairwise matrices from DB
        List<GlobalAssetScore> allScores = scoreRepository.findByEvaluationDateOrderByClassRankAsc(evalDate)
                .collectList().block();
        assertThat(allScores).isNotNull();
        assertThat(allScores).hasSize(35);

        List<GlobalAssetPairwiseMatrix> allMatrices = pairwiseMatrixRepository.findAll()
                .filter(m -> evalDate.equals(m.getEvaluationDate()))
                .collectList().block();
        assertThat(allMatrices).isNotNull();
        assertThat(allMatrices).hasSize(235); // 45 Core pairs + 190 Satellite pairs

        // Sort scores deterministically: AssetClass, then ClassRank
        allScores.sort(Comparator.comparing(GlobalAssetScore::getAssetClass)
                .thenComparing(GlobalAssetScore::getClassRank));

        // Sort matrices deterministically: AssetClass, BaseTicker, TargetTicker
        allMatrices.sort(Comparator.comparing(GlobalAssetPairwiseMatrix::getAssetClass)
                .thenComparing(GlobalAssetPairwiseMatrix::getBaseTicker)
                .thenComparing(GlobalAssetPairwiseMatrix::getTargetTicker));

        // 5. Resolve target migration directory
        Path targetDir = Paths.get("src/main/resources/db/migration");
        if (!Files.exists(targetDir)) {
            targetDir = Paths.get("engineers/03-implementations/backend/src/main/resources/db/migration");
        }

        String fileName = "V8__seed_monthly_top_list.sql";
        Path filePath = targetDir.resolve(fileName);

        StringBuilder sql = new StringBuilder();
        sql.append("-- ").append(fileName).append("\n");
        sql.append("-- Seed official 2026-09 Monthly Candidate Screening, Multi-Factor Top List & Pairwise Matrix\n");
        sql.append("-- Evaluation Date: 2026-09-01 (Cutoff: 2026-08-31)\n");
        sql.append("-- Total Scores: ").append(allScores.size()).append(" (10 Core, 20 Satellite, 5 Defensive)\n");
        sql.append("-- Total Pairwise Matrices: ").append(allMatrices.size()).append(" (45 Core pairs, 190 Satellite pairs)\n\n");

        // 6. Generate GlobalAssetScore INSERT
        sql.append("INSERT INTO global_asset_score (id, asset_id, ticker, evaluation_date, asset_class, class_rank, composite_score, fund_size_twd, r_squared, momentum_12_1, kaufman_er, sharpe_ratio, volatility_90d, ytm, dca_rank)\nVALUES\n");
        for (int i = 0; i < allScores.size(); i++) {
            GlobalAssetScore s = allScores.get(i);
            String isLast = (i == allScores.size() - 1) ? ";" : ",";
            sql.append(String.format("    ('%s', '%s', '%s', TIMESTAMP '%s', '%s', %d, %s, %s, %s, %s, %s, %s, %s, %s, %s)%s\n",
                    s.getId(),
                    s.getAssetId(),
                    s.getTicker(),
                    s.getEvaluationDate().format(TS_FORMATTER),
                    s.getAssetClass().name(),
                    s.getClassRank(),
                    formatDecimal(s.getCompositeScore()),
                    formatDecimal(s.getFundSizeTwd()),
                    formatDecimal(s.getRSquared()),
                    formatDecimal(s.getMomentum121()),
                    formatDecimal(s.getKaufmanEr()),
                    formatDecimal(s.getSharpeRatio()),
                    formatDecimal(s.getVolatility90d()),
                    formatDecimal(s.getYtm()),
                    s.getDcaRank() != null ? s.getDcaRank().toString() : "NULL",
                    isLast
            ));
        }
        sql.append("\n");

        // 7. Generate GlobalAssetPairwiseMatrix INSERT in chunks of 50 rows
        int chunkSize = 50;
        for (int i = 0; i < allMatrices.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, allMatrices.size());
            List<GlobalAssetPairwiseMatrix> chunk = allMatrices.subList(i, end);

            sql.append("INSERT INTO global_asset_pairwise_matrix (id, evaluation_date, asset_class, base_ticker, target_ticker, r_squared, correlation_coefficient)\nVALUES\n");
            for (int j = 0; j < chunk.size(); j++) {
                GlobalAssetPairwiseMatrix m = chunk.get(j);
                String isLast = (j == chunk.size() - 1) ? ";\n\n" : ",\n";
                sql.append(String.format("    ('%s', TIMESTAMP '%s', '%s', '%s', '%s', %s, %s)%s",
                        m.getId(),
                        m.getEvaluationDate().format(TS_FORMATTER),
                        m.getAssetClass().name(),
                        m.getBaseTicker(),
                        m.getTargetTicker(),
                        formatDecimal(m.getRSquared()),
                        formatDecimal(m.getCorrelationCoefficient()),
                        isLast
                ));
            }
        }

        // 8. Update Watermark for MONTHLY_TOP_LIST
        sql.append("-- Update watermark for MONTHLY_TOP_LIST\n");
        sql.append(String.format(
                "UPDATE data_feed_sync_watermark\n" +
                "SET latest_record_date = TIMESTAMP '%s',\n" +
                "    records_synced_count = %d,\n" +
                "    status = 'PASS',\n" +
                "    updated_at = CURRENT_TIMESTAMP\n" +
                "WHERE feed_name = 'MONTHLY_TOP_LIST';\n",
                evalDate.format(TS_FORMATTER),
                allScores.size()
        ));

        Files.writeString(filePath, sql.toString(), StandardCharsets.UTF_8);
        log.info("Successfully generated Flyway V8 migration file: {} ({} scores, {} pairwise matrices)",
                filePath.toAbsolutePath(), allScores.size(), allMatrices.size());

        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.size(filePath)).isGreaterThan(1000L);
    }

    private String formatDecimal(BigDecimal val) {
        return val != null ? val.toPlainString() : "NULL";
    }
}
