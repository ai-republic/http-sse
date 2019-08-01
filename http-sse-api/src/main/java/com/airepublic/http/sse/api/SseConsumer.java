package com.airepublic.http.sse.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URI;

/**
 * Defines a consumer of {@link SseEvent}s from a specified {@link URI}.
 * 
 * @author Torsten Oltmanns
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface SseConsumer {
    /**
     * The {@link URI} to the SSE source.
     * 
     * @return the {@link URI} to the SSE source
     */
    String value();
}
