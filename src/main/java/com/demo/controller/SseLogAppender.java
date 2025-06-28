package com.demo.controller;

import lombok.Getter;
import lombok.Setter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *日志附加器类
 * 自定义Logback日志追加器，用于将日志事件通过SSE（Server-Sent Events）推送到客户端。
 */
@Setter
@Getter
public class SseLogAppender extends AppenderBase<ILoggingEvent> {
    private static final BlockingQueue<String> LOG_QUEUE = new LinkedBlockingQueue<>(1000); // 可选：设置队列容量
    private Layout<ILoggingEvent> layout;//日志布局对象，用于定义日志的输出格式。

    /**
     * 实现日志追加逻辑。
     * 当日志事件被触发时，该方法会被调用，将日志格式化后存入队列。
     *
     * @param eventObject 日志事件对象，包含日志级别、消息、时间戳等信息
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) {
            return; // 如果Appender未启动，直接返回
        }
        String formattedLog;
        if (layout != null) {
            formattedLog = layout.doLayout(eventObject);// 使用自定义布局格式化日志
        } else {
            formattedLog = eventObject.getFormattedMessage();
        }

        boolean offered = LOG_QUEUE.offer(formattedLog);// 尝试将日志存入队列
        if (!offered) {
            addWarn("Log queue is full. Log message dropped: " + formattedLog);// 队列满时记录警告，并丢弃该日志
        }
    }

    /**
     * 获取日志队列，供外部消费日志消息。
     *
     * @return 阻塞队列实例，包含所有待推送的日志消息
     */
    public static BlockingQueue<String> getLogQueue() {
        return LOG_QUEUE;
    }
}