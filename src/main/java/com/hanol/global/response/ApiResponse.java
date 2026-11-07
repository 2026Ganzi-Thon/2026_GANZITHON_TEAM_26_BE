package com.hanol.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hanol.global.exception.ErrorCode;

/**
 * 모든 API 의 공통 응답 형식.
 *
 * <pre>
 * 성공: { "success": true,  "code": "SUCCESS", "message": "성공", "data": { ... } }
 * 실패: { "success": false, "code": "400_BAD_REQUEST", "message": "입력값이 올바르지 않습니다." }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

  private static final String SUCCESS_CODE = "SUCCESS";
  private static final String SUCCESS_MESSAGE = "성공";

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data);
  }

  public static ApiResponse<Void> ok() {
    return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, null);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode) {
    return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
    return new ApiResponse<>(false, errorCode.getCode(), message, null);
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
    return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), data);
  }
}
