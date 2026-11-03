package com.hanol.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스 전체의 시간 기준(Asia/Seoul).
 * '하루'는 한국 시간 자정(00시)에 바뀐다.
 * 현재 시각이 필요하면 LocalDateTime.now() 대신 Clock 을 주입받아 LocalDateTime.now(clock) 로 사용한다. (테스트에서 시간 고정 가능)
 */
@Configuration
public class ClockConfig {

	public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(ZONE_ID);
	}
}
