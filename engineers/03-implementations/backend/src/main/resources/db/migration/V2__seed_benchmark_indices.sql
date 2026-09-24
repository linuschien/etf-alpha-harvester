-- V2__seed_benchmark_indices.sql
-- Seed predefined 9 Global Benchmarks & Sentiment Radar (No ETF sample data)

INSERT INTO benchmark_index (id, ticker, name, region, description, version, created_at, updated_at)
VALUES 
    ('b0000001-0000-0000-0000-000000000001', '^TWII', '臺灣加權股價指數', 'TW', '台灣股市整體 Beta 基準指標與回撤監控線', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000002', '^GSPC', '標普 500 指數 (S&P 500)', 'US', '美股與全球廣基大盤代表性 Beta 基準', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000003', '^NDX', '那斯達克 100 指數 (Nasdaq 100)', 'US', '全球頂尖科技巨頭與動能衛星資產基準', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000004', '^SOX', '費城半導體指數 (PHLX Semiconductor)', 'US', '晶片硬體與科技供應鏈領先循環基準', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000005', '^N225', '日經 225 指數 (Nikkei 225)', 'JP', '亞洲成熟市場對標與日圓利差交易風險風向球', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000006', '^VIX', 'CBOE 標普 500 波動率指數 (VIX)', 'US', '美股大盤期權隱含恐慌指數，輔助抄底超跌確認', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000007', '^VXN', 'CBOE 那斯達克 100 波動率指數 (VXN)', 'US', '科技股專屬期權隱含恐慌指數，輔助科技衛星超跌加碼', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000008', '^MOVE', 'ICE 美銀美債波動率指數 (MOVE)', 'US', '美國公債債券市場隱含波動率與流動性恐慌指數', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000001-0000-0000-0000-000000000009', 'FEAR_GREED', 'CNN 恐懼與貪婪指數', 'GLOBAL', '宏觀綜合情緒指標 (0~100，<20極度恐懼，>80極度貪婪)', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

