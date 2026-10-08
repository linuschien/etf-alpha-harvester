package com.alphaharvester.integration;

import com.alphaharvester.adapter.out.persistence.DataFeedSyncWatermarkRepository;
import com.alphaharvester.adapter.out.persistence.DcaPopularityRankRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetPairwiseMatrixRepository;
import com.alphaharvester.adapter.out.persistence.GlobalAssetScoreRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.GlobalAssetScoreEvaluationResponse;
import com.alphaharvester.application.port.in.GlobalAssetScoreEvaluationUseCase;
import com.alphaharvester.application.service.GlobalAssetQueryService;
import com.alphaharvester.domain.entity.GlobalAssetPairwiseMatrix;
import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.model.CandidateAssetClass;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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

    public record ScoredSat(String ticker, double score, double mom, double ker, double sharpe, double r2Twii, BigDecimal aum) {}

    @Autowired
    private GlobalAssetScoreEvaluationUseCase scoreEvaluationUseCase;

    @Autowired
    private GlobalAssetQueryService queryService;

    @Autowired
    private GlobalAssetScoreRepository scoreRepository;

    @Autowired
    private MarketDailyQuoteRepository quoteRepository;

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
        int expectedScores = response.coreCount() + response.satelliteCount() + response.defensiveCount();
        int expectedMatrices = (response.coreCount() * (response.coreCount() - 1) / 2)
                + (response.satelliteCount() * (response.satelliteCount() - 1) / 2);
        assertThat(allScores).isNotNull().hasSize(expectedScores);

        List<GlobalAssetPairwiseMatrix> allMatrices = pairwiseMatrixRepository.findAll()
                .filter(m -> evalDate.equals(m.getEvaluationDate()))
                .collectList().block();
        assertThat(allMatrices).isNotNull().hasSize(expectedMatrices);

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
        sql.append("-- Total Scores: ").append(allScores.size()).append(" (")
                .append(response.coreCount()).append(" Core, ")
                .append(response.satelliteCount()).append(" Satellite, ")
                .append(response.defensiveCount()).append(" Defensive)\n");
        sql.append("-- Total Pairwise Matrices: ").append(allMatrices.size()).append("\n\n");

        // 6. Generate GlobalAssetScore INSERT
        sql.append("INSERT INTO global_asset_score (id, asset_id, ticker, evaluation_date, asset_class, class_rank, composite_score, fund_size_twd, r_squared, momentum_12m, kaufman_er, sharpe_ratio, volatility_90d, ytm, dca_rank)\nVALUES\n");
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
                    formatDecimal(s.getMomentum12m()),
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

    @Test
    @DisplayName("Evaluate 2026-10 Monthly Top List and dump into Flyway V9 script")
    void shouldEvaluateAndDumpOctober2026TopListToFlywayV9() throws IOException {
        log.info("=== Starting Evaluation and Flyway V9 Dump for 2026-10 Monthly Top List ===");


        // 1. Verify prerequisite metadata (V3 seed data) is populated
        long totalMetadata = metadataRepository.count().block();
        log.info("Current universe has {} ETFs in metadataRepository.", totalMetadata);
        assertThat(totalMetadata).isGreaterThanOrEqualTo(300);

        // 2. Clear existing scores & matrices for 2026-10-01 to ensure clean generation
        LocalDateTime evalDate = LocalDateTime.of(2026, 10, 1, 0, 0);
        scoreRepository.deleteByEvaluationDate(evalDate).block();
        pairwiseMatrixRepository.deleteByEvaluationDate(evalDate).block();

        // 3. Trigger Top List evaluation for 2026-10 via Service layer
        GlobalAssetScoreEvaluationResponse response = scoreEvaluationUseCase.evaluateGlobalAssetScores("2026-10", true).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SUCCESS");
        log.info("Evaluation completed: status={}, total={}, core={}, satellite={}, defensive={}",
                response.status(), response.evaluatedCandidatesCount(),
                response.coreCount(), response.satelliteCount(), response.defensiveCount());

        // 4. Retrieve evaluated scores and pairwise matrices from DB
        List<GlobalAssetScore> allScores = scoreRepository.findByEvaluationDateOrderByClassRankAsc(evalDate)
                .collectList().block();
        int expectedScores = response.coreCount() + response.satelliteCount() + response.defensiveCount();
        int expectedMatrices = (response.coreCount() * (response.coreCount() - 1) / 2)
                + (response.satelliteCount() * (response.satelliteCount() - 1) / 2);
        assertThat(allScores).isNotNull().hasSize(expectedScores);

        List<GlobalAssetPairwiseMatrix> allMatrices = pairwiseMatrixRepository.findAll()
                .filter(m -> evalDate.equals(m.getEvaluationDate()))
                .collectList().block();
        assertThat(allMatrices).isNotNull().hasSize(expectedMatrices);

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

        String fileName = "V9__seed_monthly_top_list.sql";
        Path filePath = targetDir.resolve(fileName);

        StringBuilder sql = new StringBuilder();
        sql.append("-- ").append(fileName).append("\n");
        sql.append("-- Seed official 2026-10 Monthly Candidate Screening, Multi-Factor Top List & Pairwise Matrix\n");
        sql.append("-- Evaluation Date: 2026-10-01 (Cutoff: 2026-09-30)\n");
        sql.append("-- Total Scores: ").append(allScores.size()).append(" (")
                .append(response.coreCount()).append(" Core, ")
                .append(response.satelliteCount()).append(" Satellite, ")
                .append(response.defensiveCount()).append(" Defensive)\n");
        sql.append("-- Total Pairwise Matrices: ").append(allMatrices.size()).append("\n\n");

        // 6. Generate GlobalAssetScore INSERT
        sql.append("INSERT INTO global_asset_score (id, asset_id, ticker, evaluation_date, asset_class, class_rank, composite_score, fund_size_twd, r_squared, momentum_12m, kaufman_er, sharpe_ratio, volatility_90d, ytm, dca_rank)\nVALUES\n");
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
                    formatDecimal(s.getMomentum12m()),
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
        log.info("Successfully generated Flyway V9 migration file: {} ({} scores, {} pairwise matrices)",
                filePath.toAbsolutePath(), allScores.size(), allMatrices.size());

        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.size(filePath)).isGreaterThan(1000L);
    }



    @Autowired
    private com.alphaharvester.adapter.out.persistence.CorporateActionRepository corporateActionRepository;

    @Autowired
    private com.alphaharvester.adapter.out.persistence.DividendAnnouncementRepository dividendRepository;

    @Test
    @DisplayName("Diagnose user questions: Core overseas ETFs, Satellite high-dividend ETFs, and 00720B")
    void inspectUserQuestions() {
        log.info("=== DIAGNOSING USER QUESTIONS ===");
        LocalDateTime evalDate = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 31, 23, 59, 59);
        LocalDateTime start365d = LocalDateTime.of(2025, 9, 1, 0, 0);

        // Q1: Check 00646 (S&P500), 00662 (Nasdaq), 00657 (Nikkei), 00645 (Topix)
        List<String> overseasCore = List.of("00646", "00662", "00657", "00645", "00661");
        log.info("--- Q1: Overseas Benchmark ETFs ---");
        for (String t : overseasCore) {
            var meta = metadataRepository.findByTicker(t).block();
            var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(t, start365d, cutoff)
                    .collectList().block();
            var aum = meta != null ? meta.getFundSizeTwd() : null;
            log.info("ETF {}: meta={}, quotesCount={}, aum={}",
                    t, meta != null ? meta.getName() : "NULL", quotes != null ? quotes.size() : 0, aum);

            if (quotes != null && !quotes.isEmpty()) {
                var bmTwii = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^TWII", start365d, cutoff).collectList().block();
                var bmGspc = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^GSPC", start365d, cutoff).collectList().block();
                var bmNdx = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^NDX", start365d, cutoff).collectList().block();
                var bmN225 = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^N225", start365d, cutoff).collectList().block();

                var ret = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(quotes);
                var retTwii = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmTwii);
                var retGspc = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmGspc);
                var retNdx = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmNdx);
                var retN225 = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmN225);

                double r2Twii = calcR2(ret, retTwii, 0);
                double r2Gspc = calcR2(ret, retGspc, 1);
                double r2Ndx = calcR2(ret, retNdx, 1);
                double r2N225 = calcR2(ret, retN225, 0);
                log.info("ETF {} R^2: TWII={}, GSPC={}, NDX={}, N225={}, maxR2={}",
                        t, r2Twii, r2Gspc, r2Ndx, r2N225, Math.max(r2Twii, Math.max(r2Gspc, Math.max(r2Ndx, r2N225))));
            }
        }

        // Q3: Check 00720B vs other bonds
        log.info("--- Q3: Defensive Bonds (00720B vs Top 5) ---");
        List<String> bonds = List.of("00720B", "00937B", "00768B", "00953B", "00981B", "00725B", "00679B", "00687B");
        for (String b : bonds) {
            var meta = metadataRepository.findByTicker(b).block();
            var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(b, start365d, cutoff)
                    .collectList().block();
            var aum = meta != null ? meta.getFundSizeTwd() : null;
            var divs = dividendRepository.findByTickerOrderByExDateDesc(b).collectList().block();
            double totalDiv = 0.0;
            if (divs != null) {
                totalDiv = divs.stream()
                        .filter(d -> d.getExDate() != null && !d.getExDate().isBefore(start365d) && !d.getExDate().isAfter(cutoff))
                        .mapToDouble(d -> d.getDividendPerShare().doubleValue())
                        .sum();
            }
            double lastPrice = (quotes != null && !quotes.isEmpty() && quotes.get(0).getClosePrice() != null)
                    ? quotes.get(0).getClosePrice().doubleValue() : 1.0;
            double ytm = lastPrice > 0 ? totalDiv / lastPrice : 0.0;
            log.info("BOND {}: quotes={}, aum={}, divsCount={}, totalDiv={}, lastPrice={}, ytm={}%",
                    b, quotes != null ? quotes.size() : 0, aum, divs != null ? divs.size() : 0, totalDiv, lastPrice, String.format("%.2f", ytm * 100));
        }

        // All evaluated defensive scores in DB:
        var allDef = scoreRepository.findByAssetClassAndEvaluationDateOrderByClassRankAsc(CandidateAssetClass.DEFENSIVE, evalDate)
                .collectList().block();
        log.info("Evaluated Defensive in DB: {}", allDef != null ? allDef.size() : 0);
        if (allDef != null) {
            for (var s : allDef) {
                log.info("Def Rank {}: {} - Score={}, YTM={}, AUM={}",
                        s.getClassRank(), s.getTicker(), s.getCompositeScore(), s.getYtm(), s.getFundSizeTwd());
            }
        }
        assertThat(allDef).isNotNull();
    }

    @Test
    void validateUserPortfolio() {
        LocalDate evalDate = LocalDate.of(2026, 9, 1);
        LocalDateTime cutoff = evalDate.minusDays(1).atTime(23, 59, 59);
        LocalDateTime start365d = cutoff.minusYears(1).plusDays(1).toLocalDate().atStartOfDay();

        List<String> coreTw = List.of("0050", "00692", "00922", "009816");
        List<String> coreUs = List.of("00646", "00662", "009813");
        List<String> satUsTech = List.of("00757", "00830", "009820");
        List<String> satTwTech = List.of("0052", "00935", "00881");
        List<String> satTwDiv = List.of("00878", "0056", "00919");
        List<String> satJp = List.of("00949", "00955", "00951");
        List<String> satGlobal = List.of("00909", "00965", "009805", "009821");

        List<List<String>> groups = List.of(coreTw, coreUs, satUsTech, satTwTech, satTwDiv, satJp, satGlobal);
        List<String> groupNames = List.of("Core TW", "Core US", "Sat US Tech", "Sat TW Tech", "Sat TW Div", "Sat Japan", "Sat Global");

        log.info("========== USER PORTFOLIO VALIDATION ==========");
        for (int g = 0; g < groups.size(); g++) {
            String gName = groupNames.get(g);
            List<String> tickers = groups.get(g);
            log.info("--- Group: {} ---", gName);
            Map<String, Map<LocalDate, Double>> returnsMap = new HashMap<>();

            for (String t : tickers) {
                var meta = metadataRepository.findByTicker(t).block();
                var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(t, start365d, cutoff)
                        .collectList().block();
                var aum = meta != null ? meta.getFundSizeTwd() : null;
                long listingDays = (meta != null && meta.getListingDate() != null) ? java.time.temporal.ChronoUnit.DAYS.between(meta.getListingDate().toLocalDate(), cutoff.toLocalDate()) : 0;
                boolean passAge = listingDays >= 365;
                log.info("Ticker {}: Name='{}', Listing='{}' (age={}d, passAge={}), QuotesCount={}, AUM={}",
                        t, meta != null ? meta.getName() : "NOT_FOUND", meta != null ? meta.getListingDate() : "NULL",
                        listingDays, passAge, quotes != null ? quotes.size() : 0, aum);

                if (quotes != null && !quotes.isEmpty()) {
                    returnsMap.put(t, com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(quotes));
                }
            }

            // Pairwise R2 within group
            for (int i = 0; i < tickers.size(); i++) {
                for (int j = i + 1; j < tickers.size(); j++) {
                    String t1 = tickers.get(i);
                    String t2 = tickers.get(j);
                    if (returnsMap.containsKey(t1) && returnsMap.containsKey(t2)) {
                        double r2 = calcR2(returnsMap.get(t1), returnsMap.get(t2), 0);
                        log.info("Pairwise R^2 [{} vs {}] in {}: {}", t1, t2, gName, String.format("%.4f", r2));
                    }
                }
            }
        }
        log.info("===============================================");
    }

    @Test
    void diagnoseTargetTickers() {
        LocalDate evalDate = LocalDate.of(2026, 9, 1);
        LocalDateTime cutoff = evalDate.minusDays(1).atTime(23, 59, 59);
        LocalDateTime start365d = cutoff.minusYears(1).plusDays(1).toLocalDate().atStartOfDay();
        LocalDate window90dStart = cutoff.toLocalDate().minusDays(90);
        LocalDate window30dStart = cutoff.toLocalDate().minusDays(30);

        List<String> targets = List.of("00949", "00965", "009805");

        var bmTwii = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^TWII", start365d, cutoff).collectList().block();
        var bmGspc = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^GSPC", start365d, cutoff).collectList().block();
        var bmNdx = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^NDX", start365d, cutoff).collectList().block();
        var bmN225 = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^N225", start365d, cutoff).collectList().block();

        var retTwii = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmTwii);
        var retGspc = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmGspc);
        var retNdx = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmNdx);
        var retN225 = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmN225);

        log.info("========== DIAGNOSING 00949, 00965, 009805 ==========");
        for (String t : targets) {
            var meta = metadataRepository.findByTicker(t).block();
            var rawQuotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(t, start365d, cutoff)
                    .collectList().block();
            long listingDays = (meta != null && meta.getListingDate() != null)
                    ? java.time.temporal.ChronoUnit.DAYS.between(meta.getListingDate().toLocalDate(), cutoff.toLocalDate()) : 0;
            boolean passAge = listingDays >= 365;

            var quotes365d = (rawQuotes != null) ? rawQuotes.stream()
                    .sorted(Comparator.comparing(com.alphaharvester.domain.entity.MarketDailyQuote::getTradeDate))
                    .toList() : Collections.<com.alphaharvester.domain.entity.MarketDailyQuote>emptyList();
            boolean passDays = quotes365d.size() >= 220;

            // 30d Turnover
            var quotes30d = quotes365d.stream()
                    .filter(q -> !q.getTradeDate().toLocalDate().isBefore(window30dStart))
                    .toList();
            double totalTurnover30d = 0.0;
            for (var q : quotes30d) {
                if (q.getTradeValueTwd() != null) totalTurnover30d += q.getTradeValueTwd().doubleValue();
            }
            double avgTurnover30d = quotes30d.isEmpty() ? 0.0 : totalTurnover30d / quotes30d.size();
            boolean passTurnover = avgTurnover30d >= 20_000_000.0;

            var ret = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(quotes365d);
            double r2Twii = calcR2(ret, retTwii, 0);
            double r2Gspc = calcR2(ret, retGspc, 1);
            double r2Ndx = calcR2(ret, retNdx, 1);
            double r2N225 = calcR2(ret, retN225, 0);
            double maxR2 = Math.max(r2Twii, Math.max(r2Gspc, Math.max(r2Ndx, r2N225)));
            boolean passCoreR2 = maxR2 >= 0.80;

            // Satellite Gates
            List<Double> returns90d = ret.entrySet().stream()
                    .filter(e -> !e.getKey().isBefore(window90dStart))
                    .map(Map.Entry::getValue)
                    .toList();
            double vol90d = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateAnnualizedVolatility(returns90d);
            boolean passVol = vol90d >= 0.18;

            BigDecimal pLatest = (quotes365d.isEmpty()) ? BigDecimal.ONE : quotes365d.get(quotes365d.size() - 1).getClosePrice();
            BigDecimal p365d = findPriceNearDate(quotes365d, start365d.toLocalDate());
            double mom12m = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateMomentum12M(pLatest, p365d);
            boolean passMom = mom12m > 0.0;

            List<BigDecimal> prices365d = quotes365d.stream().map(com.alphaharvester.domain.entity.MarketDailyQuote::getClosePrice).toList();
            double ker = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateKaufmanEfficiencyRatio(prices365d);
            double sharpe = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateSharpeRatio(new ArrayList<>(ret.values()));

            log.info("DIAG {}: Name='{}', Listing={}, Days={}, PassAge={}, PassDays={}, AvgTurnover30d={} (PassTurnover={})",
                    t, meta != null ? meta.getName() : "NULL", meta != null ? meta.getListingDate() : "NULL",
                    quotes365d.size(), passAge, passDays, String.format("%.0f", avgTurnover30d), passTurnover);
            log.info("DIAG {} R^2: TWII={}, GSPC={}, NDX={}, N225={}, maxR2={}, passCoreR2={}",
                    t, String.format("%.4f", r2Twii), String.format("%.4f", r2Gspc), String.format("%.4f", r2Ndx),
                    String.format("%.4f", r2N225), String.format("%.4f", maxR2), passCoreR2);
            log.info("DIAG {} Satellite: vol90d={} (passVol={}), mom12m={} (passMom={}), ker={}, sharpe={}, r2Twii={}",
                    t, String.format("%.4f", vol90d), passVol, String.format("%.4f", mom12m), passMom,
                    String.format("%.4f", ker), String.format("%.4f", sharpe), String.format("%.4f", r2Twii));
        }

        // Now run full pipeline in-memory to get exact ranks of all satellites
        var allAssets = metadataRepository.findAll().collectList().block();
        record SatCand(String ticker, double mom, double ker, double sharpe, double r2Twii) {}
        List<SatCand> satCands = new ArrayList<>();

        for (var asset : allAssets) {
            if (asset.getListingDate() == null || asset.getListingDate().isAfter(cutoff.minusYears(1))) continue;
            var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(asset.getTicker(), start365d, cutoff).collectList().block();
            if (quotes == null || quotes.size() < 220) continue;

            // 30d turnover
            var q30 = quotes.stream().filter(q -> !q.getTradeDate().toLocalDate().isBefore(window30dStart)).toList();
            double turn = 0.0;
            for (var q : q30) if (q.getTradeValueTwd() != null) turn += q.getTradeValueTwd().doubleValue();
            if (q30.isEmpty() || (turn / q30.size()) < 20_000_000.0) continue;

            boolean isBond = asset.getTicker().endsWith("B");
            if (isBond) continue;

            var ret = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(quotes);
            double r2Twii = calcR2(ret, retTwii, 0);
            double r2Gspc = calcR2(ret, retGspc, 1);
            double r2Ndx = calcR2(ret, retNdx, 1);
            double r2N225 = calcR2(ret, retN225, 0);
            double maxR2 = Math.max(r2Twii, Math.max(r2Gspc, Math.max(r2Ndx, r2N225)));
            if (maxR2 >= 0.80) continue; // Core

            var r90 = ret.entrySet().stream().filter(e -> !e.getKey().isBefore(window90dStart)).map(Map.Entry::getValue).toList();
            double vol = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateAnnualizedVolatility(r90);
            if (vol < 0.18) continue;

            var pLatest = (quotes.isEmpty()) ? BigDecimal.ONE : quotes.get(0).getClosePrice();
            var p365 = findPriceNearDate(quotes, start365d.toLocalDate());
            double mom = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateMomentum12M(pLatest, p365);
            if (mom <= 0.0) continue;

            var prices = quotes.stream().map(com.alphaharvester.domain.entity.MarketDailyQuote::getClosePrice).toList();
            double ker = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateKaufmanEfficiencyRatio(prices);
            double sh = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateSharpeRatio(new ArrayList<>(ret.values()));

            satCands.add(new SatCand(asset.getTicker(), mom, ker, sh, r2Twii));
        }

        log.info("Total qualified satellite candidates: {}", satCands.size());
        var momR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(satCands, SatCand::mom, true);
        var kerR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(satCands, SatCand::ker, true);
        var shR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(satCands, SatCand::sharpe, true);

        record ScoredSat(String ticker, double score, double raw, double discount, double mom, double ker, double sh, double r2Twii) {}
        List<ScoredSat> scoredList = new ArrayList<>();
        for (var c : satCands) {
            double raw = 0.50 * momR.get(c) + 0.25 * kerR.get(c) + 0.25 * shR.get(c);
            double disc = Math.max(0.0, 1.0 - c.r2Twii());
            scoredList.add(new ScoredSat(c.ticker(), raw * disc * 100.0, raw * 100.0, disc, c.mom(), c.ker(), c.sharpe(), c.r2Twii()));
        }
        scoredList.sort(Comparator.comparing(ScoredSat::score).reversed());

        for (int i = 0; i < scoredList.size(); i++) {
            var s = scoredList.get(i);
            if (targets.contains(s.ticker()) || i < 5 || i >= scoredList.size() - 5 || (i >= 18 && i <= 25)) {
                log.info("Sat Rank #{}: {} - Score={}, Raw={}, Disc={}, MOM={}, KER={}, Sharpe={}, R2Twii={}",
                        i + 1, s.ticker(), String.format("%.2f", s.score()), String.format("%.2f", s.raw()),
                        String.format("%.2f", s.discount()), String.format("%.2f", s.mom()), String.format("%.4f", s.ker()),
                        String.format("%.2f", s.sh()), String.format("%.4f", s.r2Twii()));
            }
        }
        log.info("=======================================================");
    }

    @Test
    void diagnoseAllUserExcludedTickers() {
        LocalDate evalDate = LocalDate.of(2026, 9, 1);
        LocalDateTime evalDateTime = evalDate.atStartOfDay();
        LocalDateTime cutoff = evalDate.minusDays(1).atTime(23, 59, 59);
        LocalDateTime start365d = cutoff.minusYears(1).plusDays(1).toLocalDate().atStartOfDay();
        LocalDate window90dStart = cutoff.toLocalDate().minusDays(90);
        LocalDate window30dStart = cutoff.toLocalDate().minusDays(30);

        List<String> userTickers = List.of(
                "0050", "00692", "00922", "009816",
                "00646", "00662", "009813",
                "00757", "00830", "009820",
                "0052", "00935", "00881",
                "00878", "0056", "00919",
                "00949", "00955", "00951",
                "00909", "00965", "009805", "009821"
        );

        var topScores = scoreRepository.findByEvaluationDateOrderByClassRankAsc(evalDateTime)
                .collectList().block();
        Map<String, GlobalAssetScore> topScoreMap = new HashMap<>();
        if (topScores != null) {
            for (var s : topScores) {
                topScoreMap.put(s.getTicker(), s);
            }
        }

        var bmTwii = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^TWII", start365d, cutoff).collectList().block();
        var bmGspc = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^GSPC", start365d, cutoff).collectList().block();
        var bmNdx = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^NDX", start365d, cutoff).collectList().block();
        var bmN225 = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^N225", start365d, cutoff).collectList().block();

        var retTwii = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmTwii);
        var retGspc = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmGspc);
        var retNdx = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmNdx);
        var retN225 = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmN225);

        var allAssets = metadataRepository.findAll().collectList().block();

        log.info("========== ALL USER TICKERS DIAGNOSIS (2026-09 TOP LIST) ==========");
        for (String t : userTickers) {
            if (topScoreMap.containsKey(t)) {
                GlobalAssetScore s = topScoreMap.get(t);
                log.info("[TOP LIST INCLUDED] {} ({} Rank #{}): Score={}, Status={}, Collision='{}'",
                        t, s.getAssetClass(), s.getClassRank(), s.getCompositeScore(), s.getOrthogonalStatus(), s.getCollisionDetail());
                continue;
            }

            // Excluded ticker: find exact root cause
            var meta = metadataRepository.findByTicker(t).block();
            if (meta == null) {
                log.info("[EXCLUDED] {}: NOT FOUND in database metadata repository (Stage 0).", t);
                continue;
            }

            long listingDays = (meta.getListingDate() != null)
                    ? java.time.temporal.ChronoUnit.DAYS.between(meta.getListingDate().toLocalDate(), cutoff.toLocalDate()) : 0;
            if (listingDays < 365) {
                log.info("[EXCLUDED] {}: Stage 1 Gate 1 FAILED - Listing Age {}d < 365d (Listed: {})",
                        t, listingDays, meta.getListingDate());
                continue;
            }

            var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(t, start365d, cutoff).collectList().block();
            if (quotes == null || quotes.size() < 220) {
                log.info("[EXCLUDED] {}: Stage 1 Gate 1.2 FAILED - Trading days {} < 220",
                        t, quotes != null ? quotes.size() : 0);
                continue;
            }

            BigDecimal aum = meta.getFundSizeTwd();
            if (aum != null && aum.compareTo(new BigDecimal("2000000000")) < 0) {
                log.info("[EXCLUDED] {}: Stage 1 Gate 2 FAILED - AUM {} < 20 億 TWD", t, aum);
                continue;
            }

            var q30 = quotes.stream().filter(q -> !q.getTradeDate().toLocalDate().isBefore(window30dStart)).toList();
            double turn = 0.0;
            for (var q : q30) if (q.getTradeValueTwd() != null) turn += q.getTradeValueTwd().doubleValue();
            double avgTurn = q30.isEmpty() ? 0.0 : turn / q30.size();
            if (avgTurn < 20_000_000.0) {
                log.info("[EXCLUDED] {}: Stage 1 Gate 3 FAILED - 30d Avg Daily Turnover {} < 20,000,000 TWD",
                        t, String.format("%.0f", avgTurn));
                continue;
            }

            var ret = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(quotes);
            double r2Twii = calcR2(ret, retTwii, 0);
            double r2Gspc = calcR2(ret, retGspc, 1);
            double r2Ndx = calcR2(ret, retNdx, 1);
            double r2N225 = calcR2(ret, retN225, 0);
            double maxR2 = Math.max(r2Twii, Math.max(r2Gspc, Math.max(r2Ndx, r2N225)));

            if (maxR2 >= 0.80) {
                log.info("[EXCLUDED] {}: Routed to CORE (maxR2={}), but ranked outside Core Top 10.", t, String.format("%.4f", maxR2));
            } else {
                // Satellite
                var r90 = ret.entrySet().stream().filter(e -> !e.getKey().isBefore(window90dStart)).map(Map.Entry::getValue).toList();
                double vol = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateAnnualizedVolatility(r90);
                if (vol < 0.18) {
                    log.info("[EXCLUDED] {}: Satellite Gate FAILED - Volatility 90d {} < 18%", t, String.format("%.2f%%", vol * 100));
                    continue;
                }

                var pLatest = (quotes.isEmpty()) ? BigDecimal.ONE : quotes.get(0).getClosePrice();
                var p365 = findPriceNearDate(quotes, start365d.toLocalDate());
                double mom = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateMomentum12M(pLatest, p365);
                if (mom <= 0.0) {
                    log.info("[EXCLUDED] {}: Satellite Gate FAILED - Momentum 12M {} <= 0", t, String.format("%.2f%%", mom * 100));
                    continue;
                }

                var prices = quotes.stream().map(com.alphaharvester.domain.entity.MarketDailyQuote::getClosePrice).toList();
                double ker = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateKaufmanEfficiencyRatio(prices);
                double sh = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateSharpeRatio(new ArrayList<>(ret.values()));

                log.info("[EXCLUDED] {}: Passed all gates, but Stage 2 score ranked outside Satellite Top 20 (MOM={}, KER={}, Sharpe={}, R2Twii={})",
                        t, String.format("%.2f%%", mom * 100), String.format("%.4f", ker), String.format("%.2f", sh), String.format("%.4f", r2Twii));
            }
        }
        log.info("====================================================================");
    }

    @Test
    void inspectFunnelBreakdown() {
        LocalDate evalDate = LocalDate.of(2026, 9, 1);
        LocalDateTime cutoff = evalDate.minusDays(1).atTime(23, 59, 59);
        LocalDateTime start365d = cutoff.minusYears(1).plusDays(1).toLocalDate().atStartOfDay();
        LocalDate window90dStart = cutoff.toLocalDate().minusDays(90);
        LocalDate window30dStart = cutoff.toLocalDate().minusDays(30);

        var allAssets = metadataRepository.findAll().collectList().block();

        var bmTwii = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^TWII", start365d, cutoff).collectList().block();
        var bmGspc = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^GSPC", start365d, cutoff).collectList().block();
        var bmNdx = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^NDX", start365d, cutoff).collectList().block();
        var bmN225 = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^N225", start365d, cutoff).collectList().block();

        var retTwii = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmTwii);
        var retGspc = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmGspc);
        var retNdx = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmNdx);
        var retN225 = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmN225);

        int totalUniverse = allAssets != null ? allAssets.size() : 0;
        int failedAge = 0;
        int failedDays = 0;
        int failedAum = 0;
        int failedTurnover = 0;
        int passedUniversal = 0;

        int defensiveBonds = 0;
        int corePool = 0;
        int satelliteInitial = 0;

        int satFailedVol = 0;
        int satFailedMom = 0;
        int satPassedQualified = 0;

        List<String> failedVolTickers = new ArrayList<>();
        List<String> failedMomTickers = new ArrayList<>();

        for (var asset : allAssets) {
            long listingDays = (asset.getListingDate() != null)
                    ? java.time.temporal.ChronoUnit.DAYS.between(asset.getListingDate().toLocalDate(), cutoff.toLocalDate()) : 0;
            if (listingDays < 365) {
                failedAge++;
                continue;
            }

            var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(asset.getTicker(), start365d, cutoff).collectList().block();
            if (quotes == null || quotes.size() < 220) {
                failedDays++;
                continue;
            }

            BigDecimal aum = asset.getFundSizeTwd();
            if (aum != null && aum.compareTo(new BigDecimal("2000000000")) < 0) {
                failedAum++;
                continue;
            }

            var q30 = quotes.stream().filter(q -> !q.getTradeDate().toLocalDate().isBefore(window30dStart)).toList();
            double turn = 0.0;
            for (var q : q30) if (q.getTradeValueTwd() != null) turn += q.getTradeValueTwd().doubleValue();
            double avgTurn = q30.isEmpty() ? 0.0 : turn / q30.size();
            if (avgTurn < 20_000_000.0) {
                failedTurnover++;
                continue;
            }

            passedUniversal++;

            boolean isBond = asset.getTicker().endsWith("B");
            if (isBond) {
                defensiveBonds++;
                continue;
            }

            var ret = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(quotes);
            double r2Twii = calcR2(ret, retTwii, 0);
            double r2Gspc = calcR2(ret, retGspc, 1);
            double r2Ndx = calcR2(ret, retNdx, 1);
            double r2N225 = calcR2(ret, retN225, 0);
            double maxR2 = Math.max(r2Twii, Math.max(r2Gspc, Math.max(r2Ndx, r2N225)));

            if (maxR2 >= 0.80) {
                corePool++;
            } else {
                satelliteInitial++;
                var r90 = ret.entrySet().stream().filter(e -> !e.getKey().isBefore(window90dStart)).map(Map.Entry::getValue).toList();
                double vol = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateAnnualizedVolatility(r90);
                if (vol < 0.18) {
                    satFailedVol++;
                    failedVolTickers.add(asset.getTicker());
                    continue;
                }

                var pLatest = (quotes.isEmpty()) ? BigDecimal.ONE : quotes.get(0).getClosePrice();
                var p365 = findPriceNearDate(quotes, start365d.toLocalDate());
                double mom = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateMomentum12M(pLatest, p365);
                if (mom <= 0.0) {
                    satFailedMom++;
                    failedMomTickers.add(asset.getTicker());
                    continue;
                }

                satPassedQualified++;
            }
        }

        log.info("========== SCREENING FUNNEL BREAKDOWN ==========");
        log.info("Total Universe: {}", totalUniverse);
        log.info("Failed Gate 1 (Age < 365d): {}", failedAge);
        log.info("Failed Gate 1.2 (Trading Days < 220): {}", failedDays);
        log.info("Failed Gate 2 (AUM < 20億): {}", failedAum);
        log.info("Failed Gate 3 (30d Turnover < 2000萬): {}", failedTurnover);
        log.info("Passed Stage 1 Universal Gates: {}", passedUniversal);
        log.info("--- Pool Distribution ---");
        log.info("Defensive Bonds: {}", defensiveBonds);
        log.info("Core Candidates (maxR2 >= 0.80): {}", corePool);
        log.info("Satellite Candidates (Initial): {}", satelliteInitial);
        log.info("--- Satellite Gates ---");
        log.info("Satellite Failed Volatility (< 18%): {} -> {}", satFailedVol, failedVolTickers);
        log.info("Satellite Failed Momentum (<= 0): {} -> {}", satFailedMom, failedMomTickers);
        log.info("FINAL QUALIFIED SATELLITES: {}", satPassedQualified);
        log.info("================================================");
    }

    @Test
    void simulateUserIdeas() {
        LocalDate evalDate = LocalDate.of(2026, 9, 1);
        LocalDateTime cutoff = evalDate.minusDays(1).atTime(23, 59, 59);
        LocalDateTime start365d = cutoff.minusYears(1).plusDays(1).toLocalDate().atStartOfDay();
        LocalDate window90dStart = cutoff.toLocalDate().minusDays(90);
        LocalDate window30dStart = cutoff.toLocalDate().minusDays(30);

        var allAssets = metadataRepository.findAll().collectList().block();

        var bmTwii = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^TWII", start365d, cutoff).collectList().block();
        var bmGspc = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^GSPC", start365d, cutoff).collectList().block();
        var bmNdx = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^NDX", start365d, cutoff).collectList().block();
        var bmN225 = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc("^N225", start365d, cutoff).collectList().block();

        var retTwii = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmTwii);
        var retGspc = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmGspc);
        var retNdx = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmNdx);
        var retN225 = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(bmN225);

        log.info("========== SIMULATING USER IDEAS ==========");

        // --- 1. Defensive Bond Pool with Junk Bond Exclusion ---
        record BondCand(String ticker, String name, double ytm, BigDecimal aum) {}
        List<BondCand> bondCands = new ArrayList<>();
        List<String> excludedJunkBonds = new ArrayList<>();

        for (var asset : allAssets) {
            if (!asset.getTicker().endsWith("B")) continue;
            if (asset.getListingDate() == null || asset.getListingDate().isAfter(cutoff.minusYears(1))) continue;
            var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(asset.getTicker(), start365d, cutoff).collectList().block();
            if (quotes == null || quotes.size() < 220) continue;

            // Check Junk Bond Veto
            String name = asset.getName() != null ? asset.getName() : "";
            if (name.contains("非投資等級") || name.contains("高收益")) {
                excludedJunkBonds.add(asset.getTicker() + "(" + name + ")");
                continue;
            }

            var divs = dividendRepository.findByTickerOrderByExDateDesc(asset.getTicker()).collectList().block();
            double totalDiv = 0.0;
            if (divs != null) {
                totalDiv = divs.stream()
                        .filter(d -> d.getExDate() != null && !d.getExDate().isBefore(start365d) && !d.getExDate().isAfter(cutoff))
                        .mapToDouble(d -> d.getDividendPerShare().doubleValue())
                        .sum();
            }
            double lastPrice = (quotes.get(0).getClosePrice() != null) ? quotes.get(0).getClosePrice().doubleValue() : 1.0;
            double ytm = lastPrice > 0 ? totalDiv / lastPrice : 0.0;
            BigDecimal aum = asset.getFundSizeTwd() != null ? asset.getFundSizeTwd() : BigDecimal.ZERO;
            bondCands.add(new BondCand(asset.getTicker(), name, ytm, aum));
        }

        log.info("--- Idea 3: Defensive Bonds (Junk Excluded: {}) ---", excludedJunkBonds);
        var ytmR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(bondCands, BondCand::ytm, true);
        var aumR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(bondCands, b -> b.aum() != null ? b.aum().doubleValue() : 0.0, true);
        record ScoredBond(String ticker, String name, double score, double ytm, BigDecimal aum) {}
        List<ScoredBond> scoredBonds = new ArrayList<>();
        for (var b : bondCands) {
            double sc = (0.70 * ytmR.get(b) + 0.30 * aumR.get(b)) * 100.0;
            scoredBonds.add(new ScoredBond(b.ticker(), b.name(), sc, b.ytm(), b.aum()));
        }
        scoredBonds.sort(Comparator.comparing(ScoredBond::score).reversed());
        for (int i = 0; i < Math.min(7, scoredBonds.size()); i++) {
            var b = scoredBonds.get(i);
            log.info("NEW Def Rank #{}: {} - Score={}, YTM={}%, AUM={}",
                    i + 1, b.ticker(), String.format("%.2f", b.score()), String.format("%.2f", b.ytm() * 100), b.aum());
        }

        // --- 2. Core vs Satellite with Core R2 >= 0.90 ---
        log.info("--- Idea 1: Core Threshold R^2 >= 0.90 ---");
        record CoreCand(String ticker, double maxR2, double dcaRank, BigDecimal aum) {}
        List<CoreCand> newCoreCands = new ArrayList<>();
        record SatCandSim(String ticker, double mom, double ker, double sharpe, double r2Twii, BigDecimal aum) {}
        List<SatCandSim> newSatCands = new ArrayList<>();
        Map<String, Map<LocalDate, Double>> satDailyReturns = new HashMap<>();

        for (var asset : allAssets) {
            if (asset.getTicker().endsWith("B")) continue;
            if (asset.getListingDate() == null || asset.getListingDate().isAfter(cutoff.minusYears(1))) continue;
            var quotes = quoteRepository.findByTickerAndTradeDateBetweenOrderByTradeDateDesc(asset.getTicker(), start365d, cutoff).collectList().block();
            if (quotes == null || quotes.size() < 220) continue;
            var q30 = quotes.stream().filter(q -> !q.getTradeDate().toLocalDate().isBefore(window30dStart)).toList();
            double turn = 0.0;
            for (var q : q30) if (q.getTradeValueTwd() != null) turn += q.getTradeValueTwd().doubleValue();
            if (q30.isEmpty() || (turn / q30.size()) < 20_000_000.0) continue;

            var ret = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateDailyReturns(quotes);
            double r2Twii = calcR2(ret, retTwii, 0);
            double r2Gspc = calcR2(ret, retGspc, 1);
            double r2Ndx = calcR2(ret, retNdx, 1);
            double r2N225 = calcR2(ret, retN225, 0);
            double maxR2 = Math.max(r2Twii, Math.max(r2Gspc, Math.max(r2Ndx, r2N225)));

            BigDecimal aum = asset.getFundSizeTwd() != null
                    ? asset.getFundSizeTwd()
                    : new BigDecimal("3000000000");

            if (maxR2 >= 0.90) { // NEW THRESHOLD 0.90!
                newCoreCands.add(new CoreCand(asset.getTicker(), maxR2, 0.0, aum));
            } else {
                // Routes to Satellite!
                var r90 = ret.entrySet().stream().filter(e -> !e.getKey().isBefore(window90dStart)).map(Map.Entry::getValue).toList();
                double vol = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateAnnualizedVolatility(r90);
                if (vol < 0.18) continue;
                var pLatest = (quotes.isEmpty()) ? BigDecimal.ONE : quotes.get(0).getClosePrice();
                var p365 = findPriceNearDate(quotes, start365d.toLocalDate());
                double mom = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateMomentum12M(pLatest, p365);
                if (mom <= 0.0) continue;

                var prices = quotes.stream().map(com.alphaharvester.domain.entity.MarketDailyQuote::getClosePrice).toList();
                double ker = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateKaufmanEfficiencyRatio(prices);
                double sh = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateSharpeRatio(new ArrayList<>(ret.values()));
                newSatCands.add(new SatCandSim(asset.getTicker(), mom, ker, sh, r2Twii, aum));
                satDailyReturns.put(asset.getTicker(), ret);
            }
        }

        log.info("New Core Qualifiers (R2 >= 0.90): {}", newCoreCands.stream().map(CoreCand::ticker).toList());
        log.info("New Sat Qualifiers count: {}", newSatCands.size());

        // Percentile ranks for Satellite factors
        var momR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(newSatCands, SatCandSim::mom, true);
        var kerR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(newSatCands, SatCandSim::ker, true);
        var shR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(newSatCands, SatCandSim::sharpe, true);
        // Anti-shadow: lower r2Twii gets higher rank (higher percentile)
        var antiShadowR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(newSatCands, c -> 1.0 - c.r2Twii(), true);
        var satAumR = com.alphaharvester.domain.math.FinancialMetricsCalculator.calculatePercentileRanks(newSatCands, c -> c.aum() != null ? c.aum().doubleValue() : 0.0, true);

        // --- Model 1: User's Pure Additive 4-Factor (25% MOM, 25% KER, 25% Sharpe, 25% AntiShadow) ---
        List<ScoredSat> userAdditive4F = new ArrayList<>();
        // --- Model 2: User's Additive 5-Factor with AUM (20% each) ---
        List<ScoredSat> userAdditive5F = new ArrayList<>();
        // --- Model 3: Alpha-focused 5-Factor (30% MOM, 20% KER, 20% Sharpe, 15% AntiShadow, 15% AUM) ---
        List<ScoredSat> alphaFocused5F = new ArrayList<>();
        // --- Baseline: Old Multiplicative Formula ---
        List<ScoredSat> oldMultiplicative = new ArrayList<>();

        for (var c : newSatCands) {
            double m = momR.get(c);
            double k = kerR.get(c);
            double s = shR.get(c);
            double as = antiShadowR.get(c);
            double a = satAumR.get(c);

            double s4F = (0.25 * m + 0.25 * k + 0.25 * s + 0.25 * as) * 100.0;
            userAdditive4F.add(new ScoredSat(c.ticker(), s4F, c.mom(), c.ker(), c.sharpe(), c.r2Twii(), c.aum()));

            double s5F = (0.20 * m + 0.20 * k + 0.20 * s + 0.20 * as + 0.20 * a) * 100.0;
            userAdditive5F.add(new ScoredSat(c.ticker(), s5F, c.mom(), c.ker(), c.sharpe(), c.r2Twii(), c.aum()));

            double sAlpha5F = (0.30 * m + 0.20 * k + 0.20 * s + 0.15 * as + 0.15 * a) * 100.0;
            alphaFocused5F.add(new ScoredSat(c.ticker(), sAlpha5F, c.mom(), c.ker(), c.sharpe(), c.r2Twii(), c.aum()));

            double sOld = ((1.0 / 3.0) * m + (1.0 / 3.0) * k + (1.0 / 3.0) * s) * (1.0 - c.r2Twii()) * 100.0;
            oldMultiplicative.add(new ScoredSat(c.ticker(), sOld, c.mom(), c.ker(), c.sharpe(), c.r2Twii(), c.aum()));
        }

        userAdditive4F.sort(Comparator.comparing(ScoredSat::score).reversed());
        userAdditive5F.sort(Comparator.comparing(ScoredSat::score).reversed());
        alphaFocused5F.sort(Comparator.comparing(ScoredSat::score).reversed());
        oldMultiplicative.sort(Comparator.comparing(ScoredSat::score).reversed());

        log.info("========== COMPARISON OF SCORING FORMULAS ==========");
        log.info("--- [Baseline] Old Multiplicative Formula Top 10 ---");
        for (int i = 0; i < Math.min(10, oldMultiplicative.size()); i++) {
            var sat = oldMultiplicative.get(i);
            log.info("  #{}: {} - Score={}, MOM={}%, R2={}, AUM={}億", i + 1, sat.ticker(),
                    String.format("%.2f", sat.score()), String.format("%.2f", sat.mom() * 100),
                    String.format("%.4f", sat.r2Twii()), String.format("%.0f", sat.aum().doubleValue() / 1e8));
        }

        log.info("--- [Model 1] User Additive 4-Factor (25% MOM, 25% KER, 25% Sharpe, 25% AntiShadow) Top 10 ---");
        for (int i = 0; i < Math.min(10, userAdditive4F.size()); i++) {
            var sat = userAdditive4F.get(i);
            log.info("  #{}: {} - Score={}, MOM={}%, R2={}, AUM={}億", i + 1, sat.ticker(),
                    String.format("%.2f", sat.score()), String.format("%.2f", sat.mom() * 100),
                    String.format("%.4f", sat.r2Twii()), String.format("%.0f", sat.aum().doubleValue() / 1e8));
        }

        log.info("--- [Model 2] User Additive 5-Factor with AUM (20% each) Top 10 ---");
        for (int i = 0; i < Math.min(10, userAdditive5F.size()); i++) {
            var sat = userAdditive5F.get(i);
            log.info("  #{}: {} - Score={}, MOM={}%, R2={}, AUM={}億", i + 1, sat.ticker(),
                    String.format("%.2f", sat.score()), String.format("%.2f", sat.mom() * 100),
                    String.format("%.4f", sat.r2Twii()), String.format("%.0f", sat.aum().doubleValue() / 1e8));
        }

        log.info("--- [Model 3] Alpha-focused 5-Factor (30% MOM, 20% KER, 20% Sharpe, 15% AntiShadow, 15% AUM) Top 10 ---");
        for (int i = 0; i < Math.min(10, alphaFocused5F.size()); i++) {
            var sat = alphaFocused5F.get(i);
            log.info("  #{}: {} - Score={}, MOM={}%, R2={}, AUM={}億", i + 1, sat.ticker(),
                    String.format("%.2f", sat.score()), String.format("%.2f", sat.mom() * 100),
                    String.format("%.4f", sat.r2Twii()), String.format("%.0f", sat.aum().doubleValue() / 1e8));
        }

        List<ScoredSat> pure3F = new ArrayList<>();
        for (var c : newSatCands) {
            double m = momR.get(c);
            double k = kerR.get(c);
            double s = shR.get(c);
            double s3F = (0.50 * m + 0.25 * k + 0.25 * s) * 100.0;
            pure3F.add(new ScoredSat(c.ticker(), s3F, c.mom(), c.ker(), c.sharpe(), c.r2Twii(), c.aum()));
        }
        pure3F.sort(Comparator.comparing(ScoredSat::score).reversed());

        log.info("========== PURE 3F RANKINGS (ALL {} SATELLITES) ==========", pure3F.size());
        for (int i = 0; i < pure3F.size(); i++) {
            var sat = pure3F.get(i);
            log.info("  Rank #{}: {} - Score={}, MOM={}%, KER={}, Sharpe={}, R2_TWII={}, AUM={}億",
                    i + 1, sat.ticker(), String.format("%.2f", sat.score()),
                    String.format("%.2f", sat.mom() * 100), String.format("%.4f", sat.ker()),
                    String.format("%.2f", sat.sharpe()), String.format("%.4f", sat.r2Twii()),
                    String.format("%.0f", sat.aum().doubleValue() / 1e8));
        }

        // --- STAGE 3 GREEDY ORTHOGONAL FILTRATION SIMULATION (ANCHORED AT 0052) ---
        log.info("========== STAGE 3 GREEDY ORTHOGONAL FILTRATION ANCHORED AT 0052 ==========");
        runStage3GreedyFilter("Pure 3F (Top 30 Candidates)", pure3F.subList(0, Math.min(30, pure3F.size())), satDailyReturns, 50);
        runStage3GreedyFilter("Pure 3F (Top 40 Candidates)", pure3F.subList(0, Math.min(40, pure3F.size())), satDailyReturns, 50);
        runStage3GreedyFilter("Pure 3F (Top 50 Candidates)", pure3F.subList(0, Math.min(50, pure3F.size())), satDailyReturns, 50);
        runStage3GreedyFilter("Pure 3F (ALL Candidates, Depth = " + pure3F.size() + ")", pure3F, satDailyReturns, 50);
        log.info("=========================================================================================");
    }

    private void runStage3GreedyFilter(String modelName, List<MonthlyTopListDumpIntegrationTest.ScoredSat> rankedCandidates,
                                       Map<String, Map<LocalDate, Double>> returnsMap, int maxSlots) {
        log.info(">>> Running Stage 3 Greedy Orthogonal Pruning for: {}", modelName);
        List<MonthlyTopListDumpIntegrationTest.ScoredSat> selected = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (var candidate : rankedCandidates) {
            if (selected.size() >= maxSlots) break;

            boolean collinear = false;
            String conflictWith = null;
            double conflictR2 = 0.0;

            for (var existing : selected) {
                double r2 = calcR2(returnsMap.get(candidate.ticker()), returnsMap.get(existing.ticker()), 0);
                if (r2 >= 0.50) {
                    collinear = true;
                    conflictWith = existing.ticker();
                    conflictR2 = r2;
                    break;
                }
            }

            if (!collinear) {
                selected.add(candidate);
                log.info("  [SELECTED Slot #{}] {} (Score={}, R2_TAIEX={}, MOM={}%, AUM={}億)",
                        selected.size(), candidate.ticker(), String.format("%.2f", candidate.score()),
                        String.format("%.4f", candidate.r2Twii()), String.format("%.2f", candidate.mom() * 100),
                        String.format("%.0f", candidate.aum().doubleValue() / 1e8));
            } else {
                rejected.add(candidate.ticker() + " (collinear with " + conflictWith + ", R2=" + String.format("%.3f", conflictR2) + ")");
            }
        }
        log.info("  Rejected due to collinearity (R2 >= 0.50): {}", rejected);

    }

    private BigDecimal findPriceNearDate(List<com.alphaharvester.domain.entity.MarketDailyQuote> quotes, LocalDate targetDate) {
        if (quotes.isEmpty()) return BigDecimal.ONE;
        com.alphaharvester.domain.entity.MarketDailyQuote closest = null;
        long minDiff = Long.MAX_VALUE;
        for (com.alphaharvester.domain.entity.MarketDailyQuote q : quotes) {
            long diff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(q.getTradeDate().toLocalDate(), targetDate));
            if (diff < minDiff) {
                minDiff = diff;
                closest = q;
            }
        }
        return (closest != null && closest.getClosePrice() != null) ? closest.getClosePrice() : BigDecimal.ONE;
    }

    private double calcR2(java.util.Map<java.time.LocalDate, Double> a, java.util.Map<java.time.LocalDate, Double> b, int shift) {
        if (a == null || b == null) return 0.0;
        var aligned = com.alphaharvester.domain.math.FinancialMetricsCalculator.alignReturnSeries(a, b, shift);
        return com.alphaharvester.domain.math.FinancialMetricsCalculator.calculateCorrelationAndRSquared(aligned.returnsA(), aligned.returnsB()).rSquared();
    }

    private String formatDecimal(BigDecimal val) {
        return val != null ? val.toPlainString() : "NULL";
    }
}
