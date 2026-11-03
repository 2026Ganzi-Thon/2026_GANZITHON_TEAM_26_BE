package com.hanol;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.hanol.global.config.ClockConfig;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HanolApplication {

	public static void main(String[] args) {
		// 서버가 어디서 돌든(UTC 서버 포함) 한국 시간 기준으로 동작하도록 고정
		TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.ZONE_ID));
		SpringApplication.run(HanolApplication.class, args);
	}
}
