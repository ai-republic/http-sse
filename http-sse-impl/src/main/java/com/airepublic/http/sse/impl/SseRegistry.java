package com.airepublic.http.sse.impl;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import javax.enterprise.inject.spi.CDI;

import com.airepublic.http.sse.api.ISseRegistry;
import com.airepublic.http.sse.api.ProducerEntry;
import com.airepublic.http.sse.api.SseConsumer;
import com.airepublic.http.sse.api.SseEvent;
import com.airepublic.http.sse.api.SseProducer;

/**
 * The registry for all SSE resources annotated with {@link SseProducer} or {@link SseConsumer}.
 * <p>
 * After {@link SseConsumer}s have been started the associated {@link Future} can be acquired via
 * the {@link SseRegistry#getSseConsumer(URI)} and similar methods to cancel them listening for
 * {@link SseEvent}s.
 * </p>
 * <p>
 * Example:
 * </p>
 * <p>
 * {@code Future<Void> future = sseService.receive(uri, consumer);}
 * </p>
 * <p>
 * {@code sseRegistry.registerSseConsumer(uri, future);}
 * </p>
 * <p>
 * ...
 * </p>
 * <p>
 * {@code Future<Void> future = sseRegistry.getSseConsumer(uri);}
 * </p>
 * <p>
 * {@code future.cancel();}
 * </p>
 * 
 * @author Torsten Oltmanns
 *
 */
public class SseRegistry implements Closeable, ISseRegistry {
    private final Map<String, ProducerEntry> producers = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> producerObjects = new ConcurrentHashMap<>();
    private final Map<URI, Future<Void>> consumers = new ConcurrentHashMap<>();


    /**
     * Registers a {@link SseProducer}.
     * 
     * @param path the URI path to register the {@link SseProducer} for.
     * @param clazz the producer class
     * @param method the producer {@link Method}
     */
    @Override
    public void registerSseProducer(final String path, final Class<?> clazz, final Method method) {
        if (producers.containsKey(path)) {
            throw new IllegalArgumentException("Path '" + path + "' is already registered as SseProducer!");
        }

        Object object = producerObjects.get(clazz);
        if (object == null) {
            object = CDI.current().select(clazz).get();
            producerObjects.put(clazz, object);
        }

        producers.put(path, new ProducerEntry(object, method));
    }


    /**
     * Unregisters a {@link SseProducer}.
     * 
     * @param path the URI path to register the {@link SseProducer} for.
     */
    @Override
    public void unregisterSseProducer(final String path) {
        producers.remove(path);
    }


    /**
     * Gets a registered {@link SseProducer}.
     * 
     * @param path the URI path to register the {@link SseProducer} for.
     * @return the {@link ProducerEntry} for the registered {@link SseProducer} or <code>null</code>
     */
    @Override
    public ProducerEntry getSseProducer(final String path) {
        return producers.get(path);
    }


    /**
     * Registers a {@link SseConsumer}.
     * 
     * @param uri the {@link URI} of the event source
     * @param sseConsumer the {@link Future} returned from calling
     *        {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    @Override
    public void registerSseConsumer(final URI uri, final Future<Void> sseConsumer) {
        consumers.put(uri, sseConsumer);
    }


    /**
     * Gets a registered {@link SseConsumer}.
     * 
     * @param uri the {@link URI} of the event source
     * @return the {@link Future} returned from calling
     *         {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    @Override
    public Future<Void> getSseConsumer(final URI uri) {
        return consumers.get(uri);
    }


    /**
     * Unregisters a {@link SseConsumer}.
     * 
     * @param uri the {@link URI} of the event source
     * @return the {@link Future} returned from calling
     *         {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    @Override
    public Future<Void> unregisterSseConsumer(final URI uri) {
        return consumers.remove(uri);
    }


    /**
     * Gets all registered {@link SseConsumer}s.
     * 
     * @return a mapping of {@link URI} of the event source and the {@link Future} returned from
     *         calling {@link SseService#receive(URI, java.util.function.Consumer)}
     */
    @Override
    public Map<URI, Future<Void>> getAllConsumers() {
        return consumers;
    }


    /**
     * Closes this registry an removes all {@link SseProducer} and {@link SseConsumer} mappings.
     */
    @Override
    public void close() {
        getAllConsumers().forEach((uri, future) -> future.cancel(true));
        consumers.clear();
        producers.clear();
        producerObjects.clear();
    }
}
