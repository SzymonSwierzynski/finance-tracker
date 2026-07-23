package com.financetracker.common.error;

import static com.financetracker.common.error.ProblemSupport.problem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single source of truth mapping exceptions to RFC 9457 {@code application/problem+json}.
 * Validation failures are {@code 422} with field-level {@code errors}; unexpected errors are {@code
 * 500} with a quotable id and no stack-trace leakage.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler({ConflictException.class, ObjectOptimisticLockingFailureException.class})
  public ProblemDetail handleConflict(RuntimeException ex, HttpServletRequest req) {
    String detail =
        ex instanceof ObjectOptimisticLockingFailureException
            ? "The resource was modified concurrently; reload and retry."
            : ex.getMessage();
    return problem(HttpStatus.CONFLICT, "Conflict", detail, req.getRequestURI());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest req) {
    return problem(
        HttpStatus.CONFLICT,
        "Conflict",
        "The request violates a data constraint.",
        req.getRequestURI());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
    return problem(
        HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password.", req.getRequestURI());
  }

  @ExceptionHandler(UnprocessableEntityException.class)
  public ProblemDetail handleUnprocessable(
      UnprocessableEntityException ex, HttpServletRequest req) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    ProblemDetail pd =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Validation failed",
            "One or more fields are invalid.",
            req.getRequestURI());
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
            .toList();
    pd.setProperty("errors", errors);
    return pd;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    ProblemDetail pd =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Validation failed",
            "One or more parameters are invalid.",
            req.getRequestURI());
    List<Map<String, String>> errors =
        ex.getConstraintViolations().stream()
            .map(
                v ->
                    Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
            .toList();
    pd.setProperty("errors", errors);
    return pd;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Malformed request",
        "The request body could not be read.",
        req.getRequestURI());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
    // An unmapped path is a client 404, not an unexpected 500 — and must not be logged as an error.
    return problem(
        HttpStatus.NOT_FOUND, "Not found", "No resource for this path.", req.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
    String errorId = UUID.randomUUID().toString();
    // Log the full detail server-side; return only the id the user can quote.
    log.error("Unhandled exception [errorId={}] on {}", errorId, req.getRequestURI(), ex);
    ProblemDetail pd =
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "An unexpected error occurred. Quote this id when contacting support.",
            req.getRequestURI());
    pd.setProperty("errorId", errorId);
    return pd;
  }
}
