package com.hanol.domain.auth.dto.request;

import com.hanol.domain.auth.entity.TermsType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignupRequest(
    @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
    @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요.")
        String password,
    @NotNull(message = "필수 약관에 모두 동의해 주세요.") List<TermsType> agreements) {}
