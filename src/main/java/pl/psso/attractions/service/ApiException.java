package pl.psso.attractions.service;

public class ApiException extends RuntimeException {
    public enum Code { VALIDATION_ERROR, NOT_FOUND, START_NOT_RESOLVED }

    private final Code code;
    private final String field;

    public ApiException(Code code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public Code code() {
        return code;
    }

    public String field() {
        return field;
    }
}
