package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportDay;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 작성자의 제보 수정(PUT /reports/{id}, 11-09). 등록 본문에서 건물·좌표를 뺀 것 — 핀 위치는 그대로 두고(옮기려면 지우고 새로 올린다)
 * 층·장소 설명·카테고리·제목·글·사진·시간을 통째로 바꾼다. 규칙은 등록과 같고, 시작 시각을 그대로 두면 이미 시작한 제보도 고칠 수 있다.
 */
public record ReportUpdateRequest(
        /** 건물 안 제보일 때만(지하는 음수, 0 은 없음). 캠퍼스 밖 제보는 비운다. */
        @Min(value = -10, message = "층은 지하 10층부터 30층까지 고를 수 있어요.")
        @Max(value = 30, message = "층은 지하 10층부터 30층까지 고를 수 있어요.")
        Integer floor,

        @NotBlank
        String category,

        @Size(max = 50)
        String customCategoryLabel,

        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 2000)
        String content,

        @Size(max = 60, message = "장소 설명은 60자 이내로 적어 주세요.")
        String placeLabel,

        /** 남길 지금 사진의 번호(0부터, 지금 imageUrls 순서). 이 순서대로 앞에 둔다. 비우면 지금 사진을 모두 뺀다. */
        @Size(max = Report.MAX_IMAGES)
        List<@NotNull Integer> keepImageIndexes,

        /** 새로 붙일 사진(POST /reports/images 로 받은 key). 남긴 사진 뒤에 붙고, 합쳐 최대 3장. */
        @Size(max = Report.MAX_IMAGES)
        List<@Size(max = 200) String> imageKeys,

        @NotNull
        LocalDateTime startsAt,

        @NotNull
        @Future(message = "종료 시각이 이미 지났어요. 시간을 다시 골라 주세요.")
        LocalDateTime endsAt,

        @Size(max = 8, message = "운영 날짜는 8일까지 정할 수 있어요.")
        List<ReportDay> days
) {
}
