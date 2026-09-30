package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.report.ReportFlag;

import java.time.LocalDateTime;
import java.util.List;

public record AdminReportFlagListResponse(List<Item> flags) {

    public record Item(Long id, String reason, String reporterNickname, LocalDateTime createdAt) {
        public static Item of(ReportFlag flag) {
            return new Item(flag.getId(), flag.getReason(), flag.getUser().getNickname(), flag.getCreatedAt());
        }
    }
}
