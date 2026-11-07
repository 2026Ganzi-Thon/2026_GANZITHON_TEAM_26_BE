package com.hanol.global.exception;

import com.hanol.global.response.ApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    log.info("BusinessException: {}", errorCode.getCode());
    return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<List<FieldErrorDetail>>> handleValidationException(
      MethodArgumentNotValidException exception) {
    List<FieldErrorDetail> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .toList();
    log.info("Validation failed: {}", errors);
    CommonErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
    return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode, errors));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
    log.info("Bad request: {}", exception.getMessage());
    return toResponse(CommonErrorCode.INVALID_INPUT);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException exception) {
    log.info("Method not supported: {}", exception.getMessage());
    return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
      NoResourceFoundException exception) {
    log.info("No resource found: {}", exception.getMessage());
    return toResponse(CommonErrorCode.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
    log.error("Unhandled exception", exception);
    return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
  }
}
