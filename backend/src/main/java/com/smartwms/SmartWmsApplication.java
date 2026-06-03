/**
 * 智库WMS 后端应用启动入口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // 启用异步线程池支持，用于 AI 预测任务的异步执行
public class SmartWmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWmsApplication.class, args);
    }
}
