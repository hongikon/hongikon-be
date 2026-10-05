package com.hongmap.hongmapbackend.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 유니크 키 경합 등 DB 제약 위반의 공통 안내. 어떤 제약인지·값은 밖에 알리지 않는다. */
    static final String CONFLICT_MESSAGE = "이미 처리된 요청이에요. 잠시 후 다시 확인해 주세요.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        return ResponseEntity.status(status).body(new ErrorResponse(e.getReason()));
    }

    /**
     * 확인 → INSERT 사이에 같은 요청이 끼어들어 생기는 유니크 키 위반 등. 처리하지 않으면 500 이 나가 앱이 "서버 오류"로
     * 보여 주고 재시도까지 한다. 대부분 같은 요청이 두 번 온 경우라 409 로 돌려준다(서비스에서 더 알맞은 응답으로 바꾼 곳은
     * 여기까지 오지 않는다 — UniqueConflictRetry, ReportService.flag 등).
     * 로그에는 제약 이름만 남긴다 — MySQL 메시지("Duplicate entry '…'")에 푸시 토큰·키워드 같은 값이 그대로 들어 있다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String constraint = e.getCause() instanceof ConstraintViolationException cve ? cve.getConstraintName() : null;
        log.warn("DB 제약 위반 → 409 (constraint={})", constraint);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(CONFLICT_MESSAGE));
    }
}
