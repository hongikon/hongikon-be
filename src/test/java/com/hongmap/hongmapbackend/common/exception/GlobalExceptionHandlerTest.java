package com.hongmap.hongmapbackend.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void DB_제약_위반은_값을_드러내지_않는_409() {
        SQLException sql = new SQLException("Duplicate entry 'ExponentPushToken[secret]' for key 'uq_device_token'");
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, "uq_device_token"));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo(GlobalExceptionHandler.CONFLICT_MESSAGE)
                .doesNotContain("ExponentPushToken").doesNotContain("uq_device_token");
    }
}
