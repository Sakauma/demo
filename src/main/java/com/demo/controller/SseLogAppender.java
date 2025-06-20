package com.demo.controller;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout; // 另一种格式化方式

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class SseLogAppender extends AppenderBase<ILoggingEvent> {
    private static final BlockingQueue<String> LOG_QUEUE = new LinkedBlockingQueue<>(1000); // 可选：设置队列容量
    private Layout<ILoggingEvent> layout;

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }
        String formattedLog;
        if (layout != null) {
            formattedLog = layout.doLayout(eventObject);
        } else {
            // 默认或简单的格式化
            formattedLog = eventObject.getFormattedMessage(); // 或者更详细的自定义格式
        }

        // 尝试将格式化后的日志放入队列，如果队列已满且设置了容量，offer 会返回 false
        // 对于有界队列，可以考虑 add (抛异常) 或 put (阻塞)
        boolean offered = LOG_QUEUE.offer(formattedLog);
        if (!offered) {
            // 处理队列满的情况，例如打印警告
            // addWarn("Log queue is full. Log message dropped: " + formattedLog);
        }
    }

    public static BlockingQueue<String> getLogQueue() {
        return LOG_QUEUE;
    }

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    // --- Getter/Setter for encoder (如果使用 Encoder) ---
    //public Encoder<ILoggingEvent> getEncoder() {
    //return encoder;
    //}


    //public void setEncoder(Encoder<ILoggingEvent> encoder) {
    //this.encoder = encoder;
    //}
}