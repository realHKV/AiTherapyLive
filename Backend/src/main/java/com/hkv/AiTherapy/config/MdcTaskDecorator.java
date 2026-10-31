package com.hkv.AiTherapy.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/** Carries safe request correlation fields into bounded worker threads and clears them afterward. */
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            if (captured != null) MDC.setContextMap(captured);
            else MDC.clear();
            try { task.run(); }
            finally { MDC.clear(); }
        };
    }
}
