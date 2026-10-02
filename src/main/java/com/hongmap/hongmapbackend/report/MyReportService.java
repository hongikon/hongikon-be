package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.common.dto.PageResponse;
import com.hongmap.hongmapbackend.report.dto.MyReportCountResponse;
import com.hongmap.hongmapbackend.report.dto.MyReportResponse;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 내 제보 내역 — 로그인한 작성자가 자기 제보와 검토 결과(승인 대기·지도 표시·반려 사유·숨김·종료·삭제됨)를 본다.
 * 언제나 호출한 사람 본인 것만 읽고, 신고한 사람 정보는 내보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MyReportService {

    private final MyReportRepository myReportRepository;
    private final ReportImageService reportImageService;

    @Transactional(readOnly = true)
    public PageResponse<MyReportResponse> getMine(Long userId, Pageable pageable) {
        // 클라이언트가 보낸 sort 는 버리고 쿼리의 최신순만 쓴다(NewsService 와 같은 방식).
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        LocalDateTime now = LocalDateTime.now();
        return PageResponse.of(myReportRepository.findMine(userId, pageOnly)
                .map(r -> MyReportResponse.of(r, now, reportImageService.viewUrls(r.getImageKeys()))));
    }

    @Transactional(readOnly = true)
    public MyReportCountResponse count(Long userId) {
        return new MyReportCountResponse(
                myReportRepository.countByUser_Id(userId),
                myReportRepository.countByUser_IdAndStatus(userId, ReportStatus.PENDING));
    }
}
