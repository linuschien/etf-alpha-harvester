package com.alphaharvester.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
@Table("data_feed_sync_watermark")
public class DataFeedSyncWatermark {

    @Id
    @Column("id")
    private UUID id;

    @Column("feed_name")
    private String feedName;

    @Column("last_successful_sync_at")
    private LocalDateTime lastSuccessfulSyncAt;

    @Column("latest_record_date")
    private LocalDateTime latestRecordDate;

    @Column("records_synced_count")
    private Integer recordsSyncedCount;

    @Column("status")
    private String status;

    @Column("error_message")
    private String errorMessage;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}

