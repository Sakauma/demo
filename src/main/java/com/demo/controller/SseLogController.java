package com.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@CrossOrigin(origins = "http://localhost:8080")
@RequestMapping("/sse")
public class SseLogController {

    private static final Logger logger = LoggerFactory.getLogger(SseLogController.class);

    // 1. 管理所有连接的 SseEmitter 实例
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final BlockingQueue<String> logQueue = SseLogAppender.getLogQueue();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sse-log-processor");
        t.setDaemon(true); // 设置为守护线程，以便主程序退出时它也退出
        return t;
    });

    @GetMapping(path = "/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClassLogs() {
        SseEmitter emitter = new SseEmitter(0L);

        this.emitters.add(emitter);
        logger.info("New SSE client connected. Total clients: {}", emitters.size());

        // 在完成、超时或错误时从列表中移除
        emitter.onCompletion(() -> {
            this.emitters.remove(emitter);
            logger.info("SSE client disconnected (completed). Total clients: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitter.complete();
            logger.info("SSE client timed out. Total clients: {}", emitters.size());
        });
        emitter.onError(throwable -> {
            emitter.completeWithError(throwable);
            logger.error("SSE client error: {}", throwable.getMessage());
        });

        return emitter;
    }

    // 2. 应用启动后开始处理日志队列
    @PostConstruct
    public void startLogProcessing() {
        logger.info("Initializing SSE log processing thread...");
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String logMessage = logQueue.take();

                    List<SseEmitter> deadEmitters = new ArrayList<>();
                    for (SseEmitter emitter : this.emitters) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("log") // 事件名称
                                    .data(logMessage)); // 发送日志数据
                        } catch (Exception e) {
                            logger.warn("Failed to send log to an SSE client: {}. Marking for removal.", e.getMessage());
                            deadEmitters.add(emitter);
                        }
                    }
                    // 移除已失效的 emitters
                    if (!deadEmitters.isEmpty()) {
                        this.emitters.removeAll(deadEmitters);
                        logger.info("Removed {} dead SSE clients. Total clients: {}", deadEmitters.size(), emitters.size());
                    }

                } catch (InterruptedException e) {
                    logger.info("Log processing thread interrupted. Shutting down.");
                    Thread.currentThread().interrupt(); // 重新设置中断状态
                    break;
                } catch (Exception e) {
                    logger.error("Unexpected error in log processing thread", e);
                    // 避免因为意外错误导致线程退出，可以考虑短暂sleep后继续
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            logger.info("SSE log processing thread finished.");
        });
    }

    // 3. 应用关闭前停止 ExecutorService
    @PreDestroy
    public void shutdownLogProcessing() {
        logger.info("Shutting down SSE log processing executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Executor did not terminate.");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("SSE log processing executor shut down.");
        // 清理 emitter，确保所有连接关闭
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
        emitters.clear();
    }
}