package com.hanol.global.exception;

/** 입력값 검증(@Valid) 실패 시 어떤 필드가 왜 틀렸는지 알려주는 응답 항목. */
public record FieldErrorDetail(String field, String reason) {}
