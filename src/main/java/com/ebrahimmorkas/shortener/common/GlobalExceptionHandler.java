package com.ebrahimmorkas.shortener.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.stream.Collectors;

/** RFC 9457 problem details for domain and framework errors. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(LinkNotFoundException.class)
    ProblemDetail handleNotFound(LinkNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Link not found", ex.getMessage());
    }

    /** 410 Gone tells clients (and crawlers) the link existed but will never work again. */
    @ExceptionHandler(LinkExpiredException.class)
    ProblemDetail handleExpired(LinkExpiredException ex) {
        return problem(HttpStatus.GONE, "Link expired", ex.getMessage());
    }

    @ExceptionHandler(InvalidUrlException.class)
    ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid URL", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request body is invalid");
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, e -> String.valueOf(e.getDefaultMessage()), (a, b) -> a));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    protected static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
