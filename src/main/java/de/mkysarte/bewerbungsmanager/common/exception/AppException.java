package de.mkysarte.bewerbungsmanager.common.exception;

/**
 * Fachlicher Fehler aus der Service-Schicht.
 *
 * Local-First: die Exception trägt bewusst keinen HTTP-Status mehr — es gibt
 * keine REST-Schicht. Die Unterscheidung läuft ausschließlich über {@link ErrorCode},
 * den die JavaFX-Controller für die Fehleranzeige auswerten.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static AppException notFound(String message) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public static AppException conflict(String message) {
        return new AppException(ErrorCode.CONFLICT, message);
    }

    public static AppException badRequest(String message) {
        return new AppException(ErrorCode.BAD_REQUEST, message);
    }

    public static AppException internal(String message) {
        return new AppException(ErrorCode.INTERNAL_ERROR, message);
    }
}
