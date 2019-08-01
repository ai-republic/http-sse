package com.airepublic.http.sse.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Defines a producer of {@link SseEvent}s. The resource must specify a path on which to react.
 * Optionally it may define a delay when to repeat the sending of events. The method must return a
 * {@link SseEvent}.
 * 
 * @author Torsten Oltmanns
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface SseProducer {
    /**
     * The path for this producer to listen on.
     * 
     * @return the path
     */
    String path();


    /**
     * The delay before the call to the producer method will be repeated. The default is 1. The
     * effective delay is calculated using the {@link SseProducer#unit()} parameter.
     * 
     * @return the delay
     */
    long delay() default 1L;


    /**
     * The {@link TimeUnit} for the {@link SseProducer#delay()} parameter. Default is
     * {@link TimeUnit#SECONDS}.
     * 
     * @return the {@link TimeUnit}
     */
    TimeUnit unit() default TimeUnit.SECONDS;


    /**
     * Specified how often the SSE producer method should be called before the connection is closed.
     * A value of -1 means forever. Default is -1 (forever).
     * 
     * @return the maximum number of times the producer method can be called before the connection
     *         is closed
     */
    long maxTimes() default -1L;
}
