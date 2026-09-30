-- V6__seed_twse_dca_rankings.sql
-- Seed official TWSE regular quota (DCA) Top 20 ETF investor popularity rankings (Auto-generated from TWSE OpenAPI)
-- Ranking Period: 2026-08 (Total records: 20)

ALTER TABLE dca_popularity_rank ALTER COLUMN asset_id DROP NOT NULL;

INSERT INTO dca_popularity_rank (id, asset_id, ticker, ranking_year, ranking_month, rank_position, regular_investor_count)
VALUES
    ('652e169a-12ee-3074-a322-acc2b6848ee2', '9864935b-266e-3264-a3af-450a1b66747c', '0050', 2026, 8, 1, 1280028),
    ('109a528c-b7f1-3806-aacd-29f7517979a4', 'd14b2258-2009-3e64-bae9-a148e5ea9ae2', '0056', 2026, 8, 2, 338456),
    ('234af0d9-bfe5-3a4b-90a5-2a8e41af4f49', 'f4873744-258a-31ee-b58d-0f53af324e1a', '00878', 2026, 8, 3, 312688),
    ('421425d0-d49d-3a5b-a58b-52544a5b62b9', 'b66fd0e4-b9bc-3b1a-92ff-a78957ecedcf', '006208', 2026, 8, 4, 257065),
    ('36a48569-b141-3b41-9e7d-aba718d4d74b', '969c58af-1d50-3634-b063-e7be5a0f8fc5', '00919', 2026, 8, 5, 164751),
    ('16ba3795-31a7-36cd-8442-f77885c55840', NULL, '00981A', 2026, 8, 6, 140760),
    ('dd383f49-16c4-3a7e-a8e7-80f75b7784f6', '4b7318fe-cadb-3457-884e-a6716ba26dd1', '009816', 2026, 8, 7, 96426),
    ('808e9fe6-5f5c-3461-9ace-a6e43d4d0387', '9ddd9ae1-57d4-378e-882c-d65303b5aac4', '0052', 2026, 8, 8, 74431),
    ('ff554383-4030-3b7d-be47-504067330291', '21597e68-1136-3449-a653-f1e1dd3ce699', '00881', 2026, 8, 9, 57246),
    ('b4a41492-75ed-3b2e-9ff0-44fdf3fb42a5', 'f332017b-af00-3794-a1e6-e2cd8c466142', '00713', 2026, 8, 10, 56927),
    ('a743a12f-bc51-32fd-8a89-287ee26265cd', '8182664f-e8ef-3d31-8037-0abe87b48b3c', '00646', 2026, 8, 11, 45672),
    ('09d0cdf1-c8ae-3adb-97e9-d2c16eeb24ec', 'cc19a252-748c-35b7-8e8b-8fb5b8eeb363', '00830', 2026, 8, 12, 36642),
    ('a1b8e85d-c502-3dcf-9733-f0dec36928e6', '2f0dc615-0a7e-3406-8a1a-69fdd177ec33', '00662', 2026, 8, 13, 35872),
    ('4d5e9897-db1c-3500-b6fd-be461ef5d1bf', 'fd640aed-af5c-39b4-a268-72f7dfdc1d59', '00929', 2026, 8, 14, 31325),
    ('1e4adae2-4c5b-3d32-a4bc-aab891e46c64', '5a16288d-c955-3328-bfe4-2d4cdf954c86', '00922', 2026, 8, 15, 29944),
    ('a9af7574-5379-398f-8291-01e8987d5651', '9e6c1419-9135-34dc-bea8-70d3f3821668', '00692', 2026, 8, 16, 28410),
    ('aebb3c58-a86d-3f13-8255-3cd89b74777b', NULL, '00991A', 2026, 8, 17, 27132),
    ('85203d79-a4dd-3ade-bca9-8263008a87f1', '25db47e7-d4f1-3139-a797-858a4155fd7c', '00935', 2026, 8, 18, 22029),
    ('f91fb4f6-9553-37e0-ac0c-509758b79959', 'dbc0fba0-ec5e-3608-86ff-2176062e2535', '00850', 2026, 8, 19, 21485),
    ('cb6b6210-dc65-355a-98c6-d4bfcdaf5ef9', NULL, '00988A', 2026, 8, 20, 20826);

-- Update watermark for TWSE_DCA_RANKINGS
UPDATE data_feed_sync_watermark
SET latest_record_date = TIMESTAMP '2026-08-31 00:00:00',
    records_synced_count = 20,
    status = 'SUCCESS',
    updated_at = CURRENT_TIMESTAMP
WHERE feed_name = 'TWSE_DCA_RANKINGS';
