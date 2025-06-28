package com.demo.exception;

/**
 * 自定义运行时异常类，
 * 用于处理业务流程中的异常情况。
 */
public class ProcessException extends RuntimeException {

    /**
     * 构造方法，仅接收错误消息。
     * @param message 错误消息，描述异常发生的原因。
     */
    public ProcessException(String message) {
        super(message);
    }
    /**
     * 构造方法，接收错误消息和导致异常的原始原因。
     * @param message 错误消息，描述异常发生的原因。
     * @param cause 导致当前异常发生的原始异常或错误对象。
     */
    public ProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}