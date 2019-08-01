package com.airepublic.http.sse.api;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.airepublic.http.common.HttpRequest;
import com.airepublic.http.common.HttpResponse;

public interface ISseService {

    /**
     * Processes the whole lifecycle of an {@link SseProducer}.
     * <ul>
     * <li>accepting the incoming request</li>
     * <li>sending the handshake response</li>
     * <li>sending {@link SseEvent}s by calling the associated {@link SseProducer} method</li>
     * <li>respecting delay and maximum times configured in the {@link SseProducer} annotation</li>
     * </ul>
     * 
     * @param channel the freshly accepted {@link SocketChannel}
     * @param sslContext the {@link SSLContext}
     * @param sseRegistry the {@link ISseRegistry} where the producer is registered
     */
    void processRequest(SocketChannel channel, SSLContext sslContext, ISseRegistry sseRegistry);


    /**
     * Performs an initial handshake for incoming requests.
     * 
     * @param channel the {@link SocketChannel}
     * @param sslEngine the {@link SSLEngine} or null
     * @return the {@link HttpRequest} generated for the handshake
     * @throws IOException if handshake fails
     */
    HttpRequest handshake(SocketChannel channel, SSLEngine sslEngine) throws IOException;


    /**
     * Performs the client handshake with the server found under the specified URI.
     * 
     * @param uri the URI to the SSE resource
     * @param selector the selector for the {@link SocketChannel}
     * @param channel the {@link SocketChannel}
     * @param sslEngine the {@link SSLEngine} (optional)
     * @param consumer the {@link SseEvent} {@link Consumer}
     * @return the {@link HttpResponse} from the server
     * @throws IOException if the communication fails
     */
    HttpResponse outboundHandshake(URI uri, Selector selector, SocketChannel channel, SSLEngine sslEngine, Consumer<SseEvent> consumer) throws IOException;


    /**
     * Sends the {@link SseEvent} asynchronously to the channel.
     * 
     * @param event the {@link SseEvent}
     * @param channel the {@link SocketChannel}
     * @param sslEngine the {@link SSLEngine} or null
     * @return a {@link Future} containing the original event
     * @throws IOException if sending fails
     */
    Future<SseEvent> send(SseEvent event, SocketChannel channel, SSLEngine sslEngine) throws IOException;


    /**
     * Broadcasts the {@link SseEvent} asynchronously to the channels.
     * 
     * @param event the {@link SseEvent}
     * @param channels the {@link SocketChannel}s mapped to their {@link SSLEngine}s
     * @return a {@link Future} containing the original event
     * @throws IOException if sending fails
     */
    Future<SseEvent> broadcast(SseEvent event, Map<SocketChannel, SSLEngine> channels) throws IOException;


    /**
     * Receives {@link SseEvent}s asynchronously from the URI specified in the {@link SseConsumer}
     * and notifies the {@link Consumer} when an event has been read.
     * 
     * @param uri the {@link URI} to the event source
     * @param consumer the {@link Consumer} accepting the received {@link SseEvent}s
     * @return a {@link Future}
     * @throws IOException if sending fails
     */
    Future<Void> receive(URI uri, Consumer<SseEvent> consumer) throws IOException;


    /**
     * Reads {@link SseEvent}s from the {@link ByteBuffer}.
     * <p>
     * NOTE: This method expects complete events to be contained in the buffer.
     * </p>
     * 
     * @param buffer the {@link ByteBuffer}
     * @param isChunked flag whether the connection stream is chunked
     * @return the {@link SseEvent}
     * @throws IOException if reading the event fails
     */
    List<SseEvent> decode(ByteBuffer buffer, boolean isChunked) throws IOException;


    /**
     * Encodes the {@link SseEvent}s into a {@link ByteBuffer}.
     * 
     * @param events the {@link SseEvent}s
     * @return the {@link ByteBuffer} containing the event
     * @throws IOException if something fails
     */
    ByteBuffer encode(SseEvent... events) throws IOException;

}