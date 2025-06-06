package com.demo.imgProcess;

import com.demo.imgProcess.SseLogAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

//import jakarta.annotation.PostConstruct; // For Spring Boot 3+
//import jakarta.annotation.PreDestroy;  // For Spring Boot 3+
import javax.annotation.PostConstruct; // For Spring Boot 2.x
import javax.annotation.PreDestroy;  // For Spring Boot 2.x

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@CrossOrigin(origins = "http://localhost:8080") // 根据你的前端调整
@RequestMapping("/sse")
public class LogSseController {

    private static final Logger logger = LoggerFactory.getLogger(LogSseController.class);

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
        // 永不超时。也可以设置一个合理的超时时间，例如 SseEmitter emitter = new SseEmitter(300_000L);
        SseEmitter emitter = new SseEmitter(0L);

        // 添加到列表
        this.emitters.add(emitter);
        logger.info("New SSE client connected. Total clients: {}", emitters.size());

        // 在完成、超时或错误时从列表中移除
        emitter.onCompletion(() -> {
            this.emitters.remove(emitter);
            logger.info("SSE client disconnected (completed). Total clients: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitter.complete(); // 确保完成
            // this.emitters.remove(emitter); // onCompletion 会被调用
            logger.info("SSE client timed out. Total clients: {}", emitters.size());
        });
        emitter.onError(throwable -> {
            emitter.completeWithError(throwable); // 确保完成
            // this.emitters.remove(emitter); // onCompletion 应该也会被调用
            logger.error("SSE client error: {}", throwable.getMessage());
        });

        // (可选) 可以立即发送一条连接成功的消息或历史日志
        // try {
        //   emitter.send(SseEmitter.event().name("system").data("Connected to log stream"));
        // } catch (IOException e) {
        //    logger.warn("Failed to send initial connection message to SSE client", e);
        // }

        return emitter;
    }

    // 2. 应用启动后开始处理日志队列
    @PostConstruct
    public void startLogProcessing() {
        logger.info("Initializing SSE log processing thread...");
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 从队列中获取日志，如果队列为空，take() 会阻塞
                    String logMessage = logQueue.take(); // 或者 poll(timeout, unit) 避免永久阻塞

                    List<SseEmitter> deadEmitters = new ArrayList<>();
                    for (SseEmitter emitter : this.emitters) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("log") // 事件名称
                                    .data(logMessage)); // 发送日志数据
                        } catch (Exception e) {
                            // 通常是客户端断开了连接 (e.g., IOException or IllegalStateException)
                            // 标记这个 emitter，后续统一移除
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
                    break; // 退出循环
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
