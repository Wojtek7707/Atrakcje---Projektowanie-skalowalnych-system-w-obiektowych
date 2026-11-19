package pl.psso.attractions.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pl.psso.attractions.service.ApiException;

@RestControllerAdvice(assignableTypes = AttractionController.class)
public class GlobalExceptionHandler {
    public record ErrorResponse(ErrorDetail error) {}
    public record ErrorDetail(String code, String message, String field) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> businessError(ApiException exception) {
        var status = switch (exception.code()) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case START_NOT_RESOLVED -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return error(status, exception.code().name(), exception.getMessage(), exception.field());
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorResponse> bindingError(BindException exception) {
        var fieldError = exception.getFieldError();
        var field = fieldError == null ? null : fieldError.getField();
        var message = field == null ? "Invalid request parameters" : "Invalid parameter: " + field;
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, field);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpectedError(Exception exception) {
        LOGGER.error("Request failed", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Request failed", null);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, String field) {
        return ResponseEntity.status(status).body(new ErrorResponse(new ErrorDetail(code, message, field)));
    }
}
