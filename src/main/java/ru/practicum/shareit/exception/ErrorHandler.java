package ru.practicum.shareit.exception;

import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException exception) {
        return respond(HttpStatus.CONFLICT, exception.getMessage());
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
