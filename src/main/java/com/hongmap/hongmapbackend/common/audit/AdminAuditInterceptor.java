package com.hongmap.hongmapbackend.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자(개인정보취급자) 접속 기록. 「개인정보의 안전성 확보조치 기준」 제8조는 개인정보처리시스템 접속 기록
 * (계정, 접속 일시, 접속지, 처리한 정보주체 정보, 수행 업무)을 남기고 1년 이상 보관하도록 한다.
 * 관리자 화면은 문의 연락처·제보 작성자 등을 보므로 /admin/**, /crawler/**, 제휴업체 등록·삭제 요청을
 * 전용 로거(ADMIN_AUDIT)로 한 줄씩 남긴다. 요청 본문·쿼리 값은 남기지 않는다(대상 id 는 경로에 있다).
 *
 * 보관: 컨테이너 표준출력 로그는 재배포 때 사라지므로, 운영에서는 이 로거를 파일/CloudWatch 로 내보내
 * 1년 이상 보관해야 한다(docs 참고).
 */
@Component
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final Logger AUDIT = LoggerFactory.getLogger("ADMIN_AUDIT");

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!isAuditTarget(request)) {
            return;
        }
        AUDIT.info("admin-access actor={} ip={} method={} path={} status={}",
                actor(), request.getRemoteAddr(), request.getMethod(), request.getRequestURI(), response.getStatus());
    }

    static boolean isAuditTarget(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/admin/") || path.equals("/admin") || path.startsWith("/crawler/") || path.equals("/crawler")) {
            return true;
        }
        boolean partnerWrite = path.equals("/partners") || path.startsWith("/partners/");
        return partnerWrite && !HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.OPTIONS.matches(request.getMethod());
    }

    private static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return "anonymous";
        }
        return "user:" + userId;
    }
}
