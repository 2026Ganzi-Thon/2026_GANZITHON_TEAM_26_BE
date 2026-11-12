package com.hanol.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 비동기 실행(@Async) 설정. 실행기는 Spring Boot 기본 applicationTaskExecutor 를 쓴다. */
@Configuration
@EnableAsync
public class AsyncConfig {}
