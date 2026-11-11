package com.hanol.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hanol.global.exception.ErrorCode;

/**
 * 모든 API 의 공통 응답 형식.
 *
 * <pre>
 * 성공: { "message": "success", "data": { ... } }  (데이터가 없으면 message 만)
 * 실패: { "code": "400_BAD_REQUEST", "message": "입력값이 올바르지 않습니다." }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data) {

  private static final String SUCCESS_MESSAGE = "success";

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(null, SUCCESS_MESSAGE, data);
  }

  public static ApiResponse<Void> ok() {
    return new ApiResponse<>(null, SUCCESS_MESSAGE, null);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode) {
    return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
  }
}
