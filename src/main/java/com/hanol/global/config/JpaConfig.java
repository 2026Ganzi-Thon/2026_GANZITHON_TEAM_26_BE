package com.hanol.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
	// @CreatedDate, @LastModifiedDate 자동 기록(JPA Auditing)을 켜기 위한 설정 클래스
}
