package com.airepublic.http.sse.api;

import java.lang.reflect.Method;

/**
 * Entry to register a {@link SseProducer}.
 * 
 * @author Torsten Oltmanns
 *
 */
public class ProducerEntry {
    private final Object object;
    private final Method method;


    /**
     * Constructor.
     * 
     * @param object the resource object instance
     * @param method the resource method
     */
    public ProducerEntry(final Object object, final Method method) {
        this.object = object;
        this.method = method;
    }


    /**
     * Gets the resource object instance.
     * 
     * @return the resource object instance
     */
    public Object getObject() {
        return object;
    }


    /**
     * Gets the resource {@link Method}.
     * 
     * @return the resource {@link Method}
     */
    public Method getMethod() {
        return method;
    }
}