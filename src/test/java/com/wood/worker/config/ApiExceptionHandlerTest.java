package com.wood.worker.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    static class SampleBean {
        @NotBlank
        String code;
    }

    @Test
    void illegalArgumentMapsToBadRequest() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("Nope"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Nope", response.getBody().message());
    }

    @Test
    void constraintViolationMapsToBadRequestWithFieldError() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SampleBean bean = new SampleBean();
        bean.code = "  ";
        Set<ConstraintViolation<SampleBean>> violations = validator.validate(bean);
        assertEquals(1, violations.size());

        var response = handler.handleConstraintViolation(new ConstraintViolationException(violations));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("must not be blank", response.getBody().message());
        assertEquals("code", response.getBody().fieldErrors().get(0).field());
    }

    @Test
    void missingPartMapsToBadRequest() {
        var response = handler.handleMissingPart(new MissingServletRequestPartException("item"));
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().message().contains("item"));
    }

    @Test
    void uploadTooLargeMapsToPayloadTooLarge() {
        var response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(1024));
        assertEquals(413, response.getStatusCode().value());
        assertEquals("Uploaded file exceeds the allowed size", response.getBody().message());
    }

    @Test
    void conflictMapsToConflict() {
        var response = handler.handleConflict(new DataIntegrityViolationException("dupe"));
        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void multipleResultsMapsToConflict() {
        var response = handler.handleMultipleResults(new IncorrectResultSizeDataAccessException(1, 2));
        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void unexpectedErrorMapsToInternalServerError() {
        var response = handler.handleUnexpected(new IllegalStateException("boom"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Internal server error", response.getBody().message());
    }
}