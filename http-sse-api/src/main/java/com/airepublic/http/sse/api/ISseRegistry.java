package com.airepublic.http.sse.api;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Future;

public interface ISseRegistry extends Closeable {

    /**
     * Registers a {@link SseProducer}.
     * 
     * @param path the URI path to register the {@link SseProducer} for.
     * @param clazz the producer class
     * @param method the producer {@link Method}
     */
    <T> void registerSseProducer(String path, Class<?> clazz, Method method);


    /**
     * Unregisters a {@link SseProducer}.
     * 
     * @param path the URI path to register the {@link SseProducer} for.
     */
    void unregisterSseProducer(String path);


    /**
     * Gets a registered {@link SseProducer}.
     * 
     * @param path the URI path to register the {@link SseProducer} for.
     * @return the {@link ProducerEntry} for the registered {@link SseProducer} or <code>null</code>
     */
    ProducerEntry getSseProducer(String path);


    /**
     * Registers a {@link SseConsumer}.
     * 
     * @param uri the {@link URI} of the event source
     * @param sseConsumer the {@link Future} returned from calling
     *        {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    void registerSseConsumer(URI uri, Future<Void> sseConsumer);


    /**
     * Gets a registered {@link SseConsumer}.
     * 
     * @param uri the {@link URI} of the event source
     * @return the {@link Future} returned from calling
     *         {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    Future<Void> getSseConsumer(URI uri);


    /**
     * Unregisters a {@link SseConsumer}.
     * 
     * @param uri the {@link URI} of the event source
     * @return the {@link Future} returned from calling
     *         {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    Future<Void> unregisterSseConsumer(URI uri);


    /**
     * Gets all registered {@link SseConsumer}s.
     * 
     * @return a mapping of {@link URI} of the event source and the {@link Future} returned from
     *         calling {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    Map<URI, Future<Void>> getAllConsumers();

}