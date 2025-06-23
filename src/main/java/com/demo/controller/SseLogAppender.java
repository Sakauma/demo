package com.demo.controller;


import lombok.Getter;
import lombok.Setter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


@Setter
@Getter
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
            formattedLog = eventObject.getFormattedMessage();
        }

        boolean offered = LOG_QUEUE.offer(formattedLog);
        if (!offered) {
            addWarn("Log queue is full. Log message dropped: " + formattedLog);
        }
    }

    public static BlockingQueue<String> getLogQueue() {
        return LOG_QUEUE;
    }
}