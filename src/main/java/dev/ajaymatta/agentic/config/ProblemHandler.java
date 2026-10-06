package dev.ajaymatta.agentic.config;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ProblemHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ProblemHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(status, "Request validation failed");
        List<String> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).distinct().sorted().toList();
        problem.setProperty("errors", fields);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Do not expose parser messages, rejected values, repository paths, SQL or exception internals.
        ProblemDetail safe = body instanceof ProblemDetail detail ? detail
                : problem(status, safeDetail(status));
        if (!(exception instanceof MethodArgumentNotValidException)) safe.setDetail(safeDetail(status));
        safe.setType(URI.create("urn:agentic:problem:http-" + status.value()));
        return super.handleExceptionInternal(exception, safe, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        LOG.error("Unhandled platform failure: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "An internal platform error occurred"));
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setType(URI.create("urn:agentic:problem:http-" + status.value()));
        return result;
    }

    private static String safeDetail(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Malformed or unsupported request; only documented fields and types are accepted";
            case 404 -> "Requested resource was not found";
            case 405 -> "This operation is not supported";
            case 415 -> "Request content type is not supported";
            default -> "Request could not be processed";
        };
    }
}
