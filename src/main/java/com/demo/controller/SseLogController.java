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

/**
 * SSE (Server-Sent Events) 日志控制器类
 * 该控制器管理所有客户端的连接，并从日志队列中获取日志消息，推送给所有连接的客户端。
 */
@RestController
@CrossOrigin(origins = "http://localhost:8080") // 允许来自 http://localhost:8080 的跨域请求
@RequestMapping("/sse") // 所有请求路径以 "/sse" 开头
public class SseLogController {

    private static final Logger logger = LoggerFactory.getLogger(SseLogController.class);

    // 1. 管理所有连接的 SseEmitter 实例
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>(); // 使用线程安全的 List 存储所有 SSE 连接
    private final BlockingQueue<String> logQueue = SseLogAppender.getLogQueue(); // 获取日志队列，用于从日志系统获取日志消息
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sse-log-processor");// 创建单线程执行器，用于处理日志队列
        t.setDaemon(true);
        return t;
    });

    /**
     * 提供流式日志的 SSE 端点。
     * 客户端通过此端点建立连接，接收服务器推送的日志消息。
     * @return SseEmitter 对象，用于管理客户端连接
     */
    @GetMapping(path = "/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClassLogs() {
        SseEmitter emitter = new SseEmitter(0L); // 创建 SSE 发射器，0L 表示不设置超时时间

        this.emitters.add(emitter); // 将新连接的 emitter 添加到列表中
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
            while (!Thread.currentThread().isInterrupted()) { // 线程未被中断时持续运行
                try {
                    String logMessage = logQueue.take(); // 从日志队列中获取日志消息（阻塞直到有消息）

                    List<SseEmitter> deadEmitters = new ArrayList<>(); // 存储需要移除的失效 emitter
                    for (SseEmitter emitter : this.emitters) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("log") // 事件名称
                                    .data(logMessage)); // 发送日志数据
                        } catch (Exception e) {
                            logger.warn("Failed to send log to an SSE client: {}. Marking for removal.", e.getMessage());
                            deadEmitters.add(emitter); // 标记失效的 emitter
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
                    // 避免因为意外错误导致线程退出，可以考虑短暂 sleep 后继续
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
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) { // 等待线程终止
                executor.shutdownNow(); // 强制关闭
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
