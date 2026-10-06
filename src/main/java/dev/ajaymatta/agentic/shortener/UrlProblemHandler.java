package dev.ajaymatta.agentic.shortener;

import java.net.URI;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice @Order(-10)
public class UrlProblemHandler {
    @ExceptionHandler(UrlFailure.class)
    public ResponseEntity<ProblemDetail> failure(UrlFailure failure) {
        var problem=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(failure.status()),failure.getMessage());
        problem.setType(URI.create("urn:shortener:problem:http-"+failure.status()));
        return ResponseEntity.status(failure.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
