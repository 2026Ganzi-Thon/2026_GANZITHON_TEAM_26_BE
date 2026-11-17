package com.hanol.global.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/** 비동기 실행(@Async) 설정. 실행기는 Spring Boot 기본 applicationTaskExecutor 를 쓴다. */
@Configuration
@EnableAsync
public class AsyncConfig {

  /** 호출한 스레드의 MDC 를 작업 스레드로 복사한다. Spring Boot 가 applicationTaskExecutor 에 자동 적용한다. */
  @Bean
  public TaskDecorator mdcTaskDecorator() {
    return runnable -> {
      Map<String, String> context = MDC.getCopyOfContextMap();
      return () -> {
        if (context != null) {
          MDC.setContextMap(context);
        }
        try {
          runnable.run();
        } finally {
          MDC.clear();
        }
      };
    };
  }
}
