package com.hongmap.hongmapbackend.notice;

import com.hongmap.hongmapbackend.notice.dto.AdminNoticeListResponse;
import com.hongmap.hongmapbackend.notice.dto.AdminNoticeRequest;
import com.hongmap.hongmapbackend.notice.dto.AdminNoticeResponse;
import com.hongmap.hongmapbackend.notice.dto.NoticeListResponse;
import com.hongmap.hongmapbackend.notice.dto.NoticeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 앱 공지사항. 앱은 게시 시각이 지난 공지만 받고(GET /notices), 관리자는 예약 공지까지 다룬다(/admin/notices).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppNoticeService {

    /** 팝업은 게시 후 최대 31일까지 — 오래 띄우는 공지를 막는다. */
    static final Duration MAX_POPUP = Duration.ofDays(31);
    /** 예약은 1년 안까지 — 날짜를 잘못 넣은 공지가 영영 안 보이는 일을 막는다. */
    static final Duration MAX_SCHEDULE_AHEAD = Duration.ofDays(366);

    private final AppNoticeRepository repository;
    private final Clock clock;

    public NoticeListResponse published() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new NoticeListResponse(repository.findTop50ByPublishAtLessThanEqualOrderByPublishAtDescIdDesc(now)
                .stream().map(n -> toPublic(n, now)).toList());
    }

    public AdminNoticeListResponse all() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new AdminNoticeListResponse(
                repository.findAllByOrderByPublishAtDescIdDesc().stream().map(n -> toAdmin(n, now)).toList());
    }

    @Transactional
    public AdminNoticeResponse create(AdminNoticeRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Fields f = fields(request, now);
        AppNotice saved = repository.save(AppNotice.builder().title(f.title).body(f.body).publishAt(f.publishAt)
                .popupUntil(f.popupUntil).popupText(f.popupText).build());
        return toAdmin(saved, now);
    }

    @Transactional
    public AdminNoticeResponse update(Long id, AdminNoticeRequest request) {
        AppNotice notice = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 공지예요."));
        LocalDateTime now = LocalDateTime.now(clock);
        Fields f = fields(request, now);
        notice.update(f.title, f.body, f.publishAt, f.popupUntil, f.popupText);
        repository.flush();
        return toAdmin(notice, now);
    }

    @Transactional
    public void delete(Long id) {
        AppNotice notice = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 공지예요."));
        repository.delete(notice);
    }

    private record Fields(String title, String body, LocalDateTime publishAt, LocalDateTime popupUntil,
                          String popupText) {
    }

    private Fields fields(AdminNoticeRequest r, LocalDateTime now) {
        LocalDateTime publishAt = r.publishAt();
        if (publishAt.isAfter(now.plus(MAX_SCHEDULE_AHEAD))) {
            throw badRequest("게시 시각은 1년 안으로 정해 주세요.");
        }
        LocalDateTime popupUntil = r.popupUntil();
        String popupText = null;
        if (popupUntil != null) {
            popupText = r.popupText() == null ? "" : r.popupText().strip();
            if (popupText.isEmpty()) {
                throw badRequest("팝업으로 띄우려면 팝업 문구를 입력해 주세요.");
            }
            if (!popupUntil.isAfter(publishAt)) {
                throw badRequest("팝업 종료 시각은 게시 시각보다 뒤여야 해요.");
            }
            if (popupUntil.isAfter(publishAt.plus(MAX_POPUP))) {
                throw badRequest("팝업은 게시 후 31일까지만 띄울 수 있어요.");
            }
        }
        return new Fields(r.title().strip(), r.body().strip(), publishAt, popupUntil, popupText);
    }

    private static NoticeResponse toPublic(AppNotice n, LocalDateTime now) {
        NoticeResponse.Popup popup = n.isPopupActive(now) ? new NoticeResponse.Popup(n.getPopupText(), n.getPopupUntil()) : null;
        return new NoticeResponse(n.getId(), n.getTitle(), n.getBody(), n.getPublishAt(), popup);
    }

    private static AdminNoticeResponse toAdmin(AppNotice n, LocalDateTime now) {
        return new AdminNoticeResponse(n.getId(), n.getTitle(), n.getBody(), n.getPublishAt(), n.getPopupUntil(),
                n.getPopupText(), n.isPublished(now) ? "PUBLISHED" : "SCHEDULED", n.getCreatedAt(), n.getUpdatedAt());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
