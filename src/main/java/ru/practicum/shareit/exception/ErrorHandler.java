package ru.practicum.shareit.exception;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidation(ValidationException exception) {
        return respond(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException exception) {
        return respond(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException exception) {
        return respond(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrityViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if ("23505".equals(violation.getSQLState()) && constraint != null
                        && constraint.toLowerCase(Locale.ROOT).contains("uq_user_email")) {
                    return respond(HttpStatus.CONFLICT, "Email is already in use");
                }
                if ("23503".equals(violation.getSQLState()) || "23506".equals(violation.getSQLState())) {
                    return respond(HttpStatus.CONFLICT, "Referenced data has changed");
                }
                if ("23514".equals(violation.getSQLState())) {
                    return respond(HttpStatus.BAD_REQUEST, "Invalid data");
                }
            }
            if (cause instanceof java.sql.SQLException sqlException && "22001".equals(sqlException.getSQLState())) {
                return respond(HttpStatus.BAD_REQUEST, "A value exceeds the maximum length");
            }
        }
        return handleUnexpected(exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request processing failed", exception);
        }
        return super.handleExceptionInternal(exception, error(messageFor(exception, status)), headers, status, request);
    }

    private String messageFor(Exception exception, HttpStatusCode status) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            FieldError fieldError = validationException.getBindingResult().getFieldError();
            return fieldError == null ? "Invalid request body" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        }
        if (exception instanceof MissingRequestHeaderException missingHeader) {
            return "Missing required header " + missingHeader.getHeaderName();
        }
        if (exception instanceof MissingServletRequestParameterException missingParameter) {
            return "Missing required parameter " + missingParameter.getParameterName();
        }
        if (exception instanceof MethodArgumentTypeMismatchException typeMismatch) {
            return "Invalid format for " + typeMismatch.getName();
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "Invalid or missing request body";
        }

        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        return httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase();
    }

    private ResponseEntity<Object> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(error(message));
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
