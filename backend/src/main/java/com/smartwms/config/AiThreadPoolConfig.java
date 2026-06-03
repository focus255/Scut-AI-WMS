/**
 * AI 异步任务线程池配置，用于后台处理大模型调用与报告生成。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AiThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(AiThreadPoolConfig.class);

    /**
     * AI 智能预测专用线程池。
     * 核心线程 4，最大线程 8，队列容量 100，拒绝策略为 CallerRunsPolicy。
     */
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-predict-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                log.warn("[AI线程池] 任务被拒绝提交，线程池已满，使用调用者线程执行降级");
                super.rejectedExecution(r, e);
            }
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        log.info("[AI线程池] 初始化完成，核心线程数={}, 最大线程数={}, 队列容量={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
}
