package com.airepublic.http.sse.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import com.airepublic.http.common.AsyncHttpReader;
import com.airepublic.http.common.BufferUtil;
import com.airepublic.http.common.Headers;
import com.airepublic.http.common.HttpRequest;
import com.airepublic.http.common.HttpResponse;
import com.airepublic.http.common.HttpStatus;
import com.airepublic.http.common.SslSupport;
import com.airepublic.http.sse.api.ISseRegistry;
import com.airepublic.http.sse.api.ISseService;
import com.airepublic.http.sse.api.ProducerEntry;
import com.airepublic.http.sse.api.SseEvent;
import com.airepublic.http.sse.api.SseProducer;

/**
 * A service to send and received SSE.
 * 
 * @author Torsten Oltmanns
 *
 */
@Named
public class SseService implements Serializable, ISseService {
    private static final Logger logger = Logger.getGlobal();
    private static final long serialVersionUID = 1L;
    private static SSLContext clientSSLContext;

    /**
     * The task to execute a sending of an SSE.
     * 
     * @author Torsten Oltmanns
     *
     */
    private class SendTask extends ForkJoinTask<SseEvent> {
        private static final long serialVersionUID = 1L;
        private final SseEvent event;
        private final SocketChannel channel;
        private final SSLEngine sslEngine;


        /**
         * Constructor.
         * 
         * @param event the {@link SseEvent} to send
         * @param channel the {@link SocketChannel} to the receiver
         * @param sslEngine the {@link SSLEngine} to encrypt the {@link SseEvent} or null if not an
         *        SSL connections
         */
        public SendTask(final SseEvent event, final SocketChannel channel, final SSLEngine sslEngine) {
            this.event = event;
            this.channel = channel;
            this.sslEngine = sslEngine;
        }


        @Override
        protected boolean exec() {
            try {
                ByteBuffer[] buffers = { encode(event) };

                if (sslEngine != null) {
                    buffers = SslSupport.wrap(sslEngine, channel, buffers);
                }

                channel.write(buffers);
            } catch (final Exception e) {
                completeExceptionally(e);
                return false;
            }

            return true;
        }


        @Override
        public SseEvent getRawResult() {
            return event;
        }


        @Override
        protected void setRawResult(final SseEvent value) {
        }
    }

    /**
     * The task to execute a broadcast of an SSE.
     * 
     * @author Torsten Oltmanns
     *
     */
    private class BroadcastTask extends ForkJoinTask<SseEvent> {
        private static final long serialVersionUID = 1L;
        private final SseEvent event;
        private final Map<SocketChannel, SSLEngine> channels;


        /**
         * Constructor.
         * 
         * @param event the {@link SseEvent} to send
         * @param channels the {@link SocketChannel} to the receiver mapped to the associated
         *        {@link SSLEngine} (or null if not an SSL connection)
         */
        public BroadcastTask(final SseEvent event, final Map<SocketChannel, SSLEngine> channels) {
            this.event = event;
            this.channels = channels;
        }


        @Override
        protected boolean exec() {
            Throwable t = null;

            for (final Entry<SocketChannel, SSLEngine> entry : channels.entrySet()) {
                final SocketChannel channel = entry.getKey();

                if (channel.isOpen()) {
                    try {
                        ByteBuffer[] buffers = { encode(event) };
                        final SSLEngine sslEngine = entry.getValue();

                        if (sslEngine != null) {
                            buffers = SslSupport.wrap(sslEngine, channel, buffers);
                        }

                        channel.write(buffers);
                    } catch (final Exception e) {
                        t = e;
                    }
                }
            }

            if (t != null) {
                completeExceptionally(t);
                return false;
            }

            return true;
        }


        @Override
        public SseEvent getRawResult() {
            return event;
        }


        @Override
        protected void setRawResult(final SseEvent value) {
        }
    }

    /**
     * The task to execute receiving of SSE.
     * 
     * @author Torsten Oltmanns
     *
     */
    private class ReceiveTask extends ForkJoinTask<Void> {
        private static final long serialVersionUID = 1L;
        private final Consumer<SseEvent> consumer;
        private final URI uri;
        private final boolean isSecure;
        private SSLEngine sslEngine;
        private SocketChannel channel = null;
        private Selector selector = null;
        private boolean closed = false;


        /**
         * Constructor.
         * 
         * @param uri the {@link URI} to the event source
         * @param consumer the {@link Consumer} accepting the incoming {@link SseEvent}s
         * @throws IOException if opening a connection fails
         */
        public ReceiveTask(final URI uri, final Consumer<SseEvent> consumer) throws IOException {
            this.consumer = consumer;
            this.uri = uri;
            isSecure = uri.getScheme().equals("https");
            selector = Selector.open();
        }


        @Override
        protected boolean exec() {
            boolean repeat = true;
            long retry = -1L;

            // repeat reconnecting forever unless the ForkJointask is cancelled
            while (repeat) {
                try {
                    // open channel to URI destination
                    channel = openChannel();
                    channel.register(selector, SelectionKey.OP_READ);
                    closed = false;

                    // handshake with the remote server
                    final HttpResponse response = outboundHandshake(uri, selector, channel, sslEngine, consumer);

                    // check if content will be chunked
                    final String transferEncoding = response.getHeaders().getFirst(Headers.TRANSFER_ENCODING);
                    final boolean isChunked = transferEncoding != null && transferEncoding.equals("chunked");

                    // read following SSE events
                    while (!closed && selector.isOpen()) {
                        final int len = selector.select();

                        if (len > 0) {
                            final Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                            while (it.hasNext()) {
                                final SelectionKey key = it.next();
                                it.remove();

                                if (key.isValid() && key.isReadable()) {
                                    ByteBuffer buffer = ByteBuffer.allocate(1024 * 16);
                                    final int read = ((SocketChannel) key.channel()).read(buffer);

                                    if (read == -1) {
                                        // channel has closed
                                        selector.close();
                                    } else if (read > 0) {
                                        buffer.flip();

                                        if (isSecure) {
                                            buffer = SslSupport.unwrap(sslEngine, channel, buffer);
                                        }

                                        final Long retryChk = processEventBuffer(buffer, isChunked, consumer);

                                        if (retryChk != null) {
                                            retry = retryChk;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (final CancellationException e) {
                    completeExceptionally(e);
                    repeat = false;
                } catch (final Exception e) {
                    logger.log(Level.WARNING, "Receiving SSE encountered an error: " + e.getLocalizedMessage());
                } finally {
                    // close channel, remove it from selector and free resources
                    close();
                }

                // possibly wait before re-connecting
                if (retry != SseEvent.RETRY_NOT_SET && retry > 0) {
                    try {
                        Thread.currentThread().wait(retry);
                    } catch (final InterruptedException e) {
                    }
                }
            }

            close();
            // close the selector after cancellation
            if (selector.isOpen()) {
                try {
                    selector.close();
                } catch (final IOException e) {
                }
            }

            return true;
        }


        /**
         * Closes the current connection.
         */
        protected void close() {
            closed = true;

            // close channel
            if (channel != null) {
                channel.keyFor(selector).cancel();

                try {
                    channel.close();
                } catch (final IOException e) {
                }
            }

            // close SSLEngine
            if (sslEngine != null) {
                try {
                    sslEngine.closeInbound();
                } catch (final SSLException e) {
                }

                sslEngine = null;
            }

            logger.info("Closed channel receiving SSE from: " + uri);
        }


        /**
         * Opens a {@link SocketChannel} to the {@link URI} of the event source.
         * 
         * @return the {@link SocketChannel}
         */
        protected SocketChannel openChannel() {
            int port = uri.getPort();

            if (port <= 0) {
                port = isSecure ? 443 : 80;
            }

            logger.info("Opening channel to receive SSE from: " + uri);

            SocketChannel channel;

            try {
                final SocketAddress remote = new InetSocketAddress(uri.getHost(), port);
                channel = SocketChannel.open();
                channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                channel.configureBlocking(false);
                channel.connect(remote);

                while (!channel.finishConnect()) {
                }

                if (isSecure) {
                    // perform SSL handshake
                    sslEngine = SslSupport.clientSSLHandshake(clientSSLContext, channel, uri);
                }
            } catch (final IOException e) {
                throw new RuntimeException("Failed to create socket channel!", e);
            }

            return channel;
        }


        @Override
        public Void getRawResult() {
            return null;
        }


        @Override
        protected void setRawResult(final Void value) {
        }
    }

    /**
     * The task to execute a process the whole lifecycle of an {@link SseProducer}.
     * <ul>
     * <li>accepting the incoming request</li>
     * <li>sending the handshake response</li>
     * <li>sending {@link SseEvent}s by calling the associated {@link SseProducer} method</li>
     * <li>respecting delay and maximum times configured in the {@link SseProducer} annotation</li>
     * </ul>
     * 
     * @author Torsten Oltmanns
     *
     */
    private class ProcessTask extends ForkJoinTask<Void> {
        private static final long serialVersionUID = 1L;
        private final SocketChannel channel;
        private final SSLContext sslContext;
        private final ISseRegistry sseRegistry;


        public ProcessTask(final SocketChannel channel, final SSLContext sslContext, final ISseRegistry sseRegistry) {
            this.channel = channel;
            this.sslContext = sslContext;
            this.sseRegistry = sseRegistry;
        }


        @Override
        protected boolean exec() {
            SSLEngine sslEngine = null;

            try {
                // if a SSLContext is set assume the server to be in secure mode
                if (sslContext != null) {
                    sslEngine = SslSupport.serverSSLHandshake(sslContext, channel);
                }

                // accept the handshake request and send a response
                final HttpRequest request = handshake(channel, sslEngine);

                // find the SSE producer method mapped to the URI path
                final ProducerEntry producerEntry = sseRegistry.getSseProducer(request.getPath());

                if (producerEntry != null) {
                    final SseProducer annotation = producerEntry.getMethod().getAnnotation(SseProducer.class);
                    final long delayInMs = annotation.unit().toMillis(annotation.delay());
                    final long maxTimes = annotation.maxTimes();
                    long times = 0;

                    try {
                        while (maxTimes == -1 || times < maxTimes) {
                            // call the producer method
                            final Object result = producerEntry.getMethod().invoke(producerEntry.getObject(), new Object[0]);

                            if (result instanceof SseEvent) {
                                // if we got an event, send it
                                final SseEvent event = (SseEvent) result;
                                send(event, channel, sslEngine);
                            }

                            times++;

                            try {
                                Thread.sleep(delayInMs);
                            } catch (final Exception e) {
                            }
                        }
                    } catch (final Exception e) {
                        logger.log(Level.SEVERE, "Could not invoke SSE outbound producer method: " + producerEntry.getObject().getClass().getSimpleName() + "#" + producerEntry.getMethod().getName(), e);
                    }

                }
            } catch (final Exception e) {
                logger.log(Level.WARNING, "SSE producer encountered error: " + e.getLocalizedMessage());
            }

            try {
                channel.close();
                return true;
            } catch (final IOException e) {
            }
            return false;
        }


        @Override
        public Void getRawResult() {
            return null;
        }


        @Override
        protected void setRawResult(final Void value) {
        }

    }


    /**
     * Create a new instance.
     */
    public SseService() {
        try {
            clientSSLContext = SslSupport.createClientSSLContext();
        } catch (final IOException e) {
            throw new IllegalStateException("SSL context could not be created!", e);
        }
    }


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
     * @param sseRegistry the {@link SseRegistry} where the producer is registered
     */
    @Override
    public void processRequest(final SocketChannel channel, final SSLContext sslContext, final ISseRegistry sseRegistry) {
        ForkJoinPool.commonPool().submit(new ProcessTask(channel, sslContext, sseRegistry));
    }


    /**
     * Performs an initial handshake for incoming requests.
     * 
     * @param channel the {@link SocketChannel}
     * @param sslEngine the {@link SSLEngine} or null
     * @throws IOException if handshake fails
     */
    @Override
    public HttpRequest handshake(final SocketChannel channel, final SSLEngine sslEngine) throws IOException {
        // read initiating request
        final ByteBuffer buffer = ByteBuffer.allocate(4096);
        final int len = channel.read(buffer);

        if (len == -1) {
            try {
                channel.close();
            } catch (final Exception e) {
            }

            throw new IOException("Channel is already closed!");
        } else if (len == 0) {
            return null;
        }

        buffer.flip();

        final AsyncHttpReader httpReader = new AsyncHttpReader();
        httpReader.receiveBuffer(buffer);
        final HttpRequest request = httpReader.getHttpRequest();

        // send handshake response
        sendHandshakeResponse(channel, sslEngine);

        return request;
    }


    @Override
    public HttpResponse getHandshakeResponse() {
        final Headers headers = new Headers();
        headers.add(Headers.CONTENT_TYPE, "text/event-stream");
        headers.add(Headers.CONNECTION, "keep-alive");
        headers.add(Headers.CACHE_CONTROL, "no-cache");
        headers.add(Headers.PRAGMA, "no-cache");

        return new HttpResponse(HttpStatus.SUCCESS, headers);
    }


    /**
     * Sends the handshake response to the client.
     * 
     * @param channel the {@link SocketChannel} to the client
     * @param sslEngine the {@link SSLEngine}
     * @throws IOException if sending fails
     */
    @Override
    public void sendHandshakeResponse(final SocketChannel channel, final SSLEngine sslEngine) throws IOException {
        ByteBuffer[] buffers = { getHandshakeResponse().getHeaderBuffer() };

        if (sslEngine != null) {
            buffers = SslSupport.wrap(sslEngine, channel, buffers);
        }

        channel.write(buffers);
    }


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
    @Override
    public HttpResponse outboundHandshake(final URI uri, final Selector selector, final SocketChannel channel, final SSLEngine sslEngine, final Consumer<SseEvent> consumer) throws IOException {
        // create request to URI
        final Headers headers = new Headers();
        headers.add(Headers.HOST, uri.getHost());
        headers.add(Headers.ACCEPT, "text/event-stream");
        headers.add(Headers.CONNECTION, "keep-alive");
        headers.add(Headers.CACHE_CONTROL, "no-cache");
        headers.add(Headers.PRAGMA, "no-cache");
        headers.add(Headers.TE, "Trailers");
        headers.add(Headers.ACCEPT_ENCODING, "gzip, deflate, br");
        headers.add("DNT", "1");
        headers.add(Headers.REFERER, uri.getHost());
        headers.add("Origin", uri.getHost());

        final HttpRequest request = new HttpRequest(uri, headers);
        request.withMethod("GET").withVersion("HTTP/1.1");

        logger.fine("SSE client handshake request: " + request.getRequestLine());

        ByteBuffer[] buffers = { request.getHeaderBuffer() };

        if (sslEngine != null) {
            buffers = SslSupport.wrap(sslEngine, channel, buffers);
        }

        channel.write(buffers);

        // read the response
        HttpResponse response = null;
        final AsyncHttpReader httpReader = new AsyncHttpReader();

        if (selector.isOpen()) {
            final int len = selector.select();

            if (len > 0) {
                final Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                while (it.hasNext()) {
                    final SelectionKey key = it.next();
                    it.remove();

                    if (key.isValid() && key.isReadable()) {
                        ByteBuffer buffer = ByteBuffer.allocate(1024 * 16);
                        buffer.position(0);

                        final int read = ((SocketChannel) key.channel()).read(buffer);
                        buffer.flip();

                        if (sslEngine != null) {
                            buffer = SslSupport.unwrap(sslEngine, channel, buffer);

                            // close outbound channel
                            sslEngine.closeOutbound();
                        }

                        if (read == -1) {
                            // channel has closed
                            break;
                        } else if (read > 0) {

                            if (httpReader.receiveBuffer(buffer)) {
                                response = httpReader.getHttpResponse();
                                logger.fine("SSE client handshake response: " + response.getStatus());
                                final String transferEncoding = response.getHeaders().getFirst(Headers.TRANSFER_ENCODING);
                                final boolean isChunked = transferEncoding != null && transferEncoding.equals("chunked");

                                // if the response contains a body its already an event
                                if (response.getStatus() == HttpStatus.SUCCESS) {
                                    if (response.getBody() != null) {
                                        processEventBuffer(response.getBody(), isChunked, consumer);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return response;
    }


    /**
     * Processes the received {@link ByteBuffer} and notifies the consumer.
     * 
     * @param buffer the {@link ByteBuffer}
     * @param isChunked flag whether the received {@link ByteBuffer} was received by a chunked
     *        connection
     * @param consumer the consumer to notify
     * @return the last {@link SseEvent#getRetry()} value or null if no event was processed
     * @throws IOException if parsing the buffer fails
     */
    protected Long processEventBuffer(final ByteBuffer buffer, final boolean isChunked, final Consumer<SseEvent> consumer) throws IOException {
        if (buffer == null || !buffer.hasRemaining()) {
            return null;
        }

        final List<SseEvent> events = decode(buffer, isChunked);
        Long retry = null;

        if (events != null && !events.isEmpty()) {
            for (final SseEvent event : events) {
                consumer.accept(event);
                retry = event.getRetry();
            }
        }

        return retry;
    }


    /**
     * Sends the {@link SseEvent} asynchronously to the channel.
     * 
     * @param event the {@link SseEvent}
     * @param channel the {@link SocketChannel}
     * @param sslEngine the {@link SSLEngine} or null
     * @return a {@link Future} containing the original event
     * @throws IOException if sending fails
     */
    @Override
    public Future<SseEvent> send(final SseEvent event, final SocketChannel channel, final SSLEngine sslEngine) throws IOException {
        if (channel.isOpen()) {
            return ForkJoinPool.commonPool().submit(new SendTask(event, channel, sslEngine));
        } else {
            throw new IOException("Cannot send SseEvent due to closed channel!");
        }
    }


    /**
     * Broadcasts the {@link SseEvent} asynchronously to the channels.
     * 
     * @param event the {@link SseEvent}
     * @param channels the {@link SocketChannel}s mapped to their {@link SSLEngine}s
     * @return a {@link Future} containing the original event
     * @throws IOException if sending fails
     */
    @Override
    public Future<SseEvent> broadcast(final SseEvent event, final Map<SocketChannel, SSLEngine> channels) throws IOException {
        return ForkJoinPool.commonPool().submit(new BroadcastTask(event, channels));
    }


    /**
     * Receives {@link SseEvent}s asynchronously from the specified URI and notifies the
     * {@link Consumer} when an event has been read.
     * 
     * @param uri the {@link URI} to the event source
     * @param consumer the {@link Consumer} accepting the received {@link SseEvent}s
     * @return a {@link Future}
     * @throws IOException if sending fails
     */
    @Override
    public Future<Void> receive(final URI uri, final Consumer<SseEvent> consumer) throws IOException {
        return ForkJoinPool.commonPool().submit(new ReceiveTask(uri, consumer));
    }


    /**
     * Reads {@link SseEvent}s from the {@link ByteBuffer}.
     * <p>
     * NOTE: This method expects complete events to be contained in the buffer.
     * </p>
     * 
     * @param buffer the {@link ByteBuffer}
     * @return the {@link SseEvent}
     * @throws IOException if reading the event fails
     */
    @Override
    public List<SseEvent> decode(final ByteBuffer buffer, final boolean isChunked) throws IOException {
        final List<SseEvent> events = new ArrayList<>();
        SseEvent currentEvent = new SseEvent();
        String currentKey = "";
        String currentValue = "";
        String line = "";
        boolean isReadKey = true;

        // read all preceding blank lines
        while (line != null && line.isBlank()) {
            buffer.mark();
            line = BufferUtil.readLine(buffer, Charset.forName("UTF-8"));
        }

        buffer.reset();

        if (line == null) {
            return Collections.emptyList();
        }

        int contentLength = 0;

        if (isChunked) {
            // first line contains the content-length in hexadecimal
            line = BufferUtil.readLine(buffer, Charset.forName("UTF-8"));
            contentLength = Integer.parseInt(line.strip(), 16);
        }

        if (!isChunked || contentLength > 0) {
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            final String eventMessage = new String(bytes, Charset.forName("UTF-8"));
            final BufferedReader reader = new BufferedReader(new StringReader(eventMessage));

            line = reader.readLine();

            while (line != null) {

                if (line.isBlank() && currentEvent.getData() != null && currentEvent.getData().length() > 0 && !events.contains(currentEvent)) {
                    events.add(currentEvent);
                    currentEvent = new SseEvent();
                }

                int pos = 0;

                if (isReadKey) {
                    int idx = line.indexOf(':');

                    // check if only a part of a key has been received
                    if (idx == -1) {
                        // add whatever is left to the current key
                        currentKey = currentKey + line;
                        pos = line.length();
                    } else {
                        // otherwise add the line until the : to the key
                        currentKey = currentKey + line.substring(0, idx);
                        pos = ++idx;

                        isReadKey = false;
                    }
                }

                if (!isReadKey) {
                    currentValue = currentValue + line.substring(pos);
                    pos = line.length();

                    if (currentKey.equalsIgnoreCase("event")) {
                        currentEvent.setName(currentValue);
                    } else if (currentKey.equalsIgnoreCase("id")) {
                        currentEvent.setId(currentValue);
                    } else if (currentKey.equalsIgnoreCase("retry")) {
                        try {
                            final Long retry = Long.valueOf(currentValue);
                            currentEvent.setRetry(retry);
                        } catch (final NumberFormatException e) {
                            logger.warning("SSE retry value is not a long value:" + currentValue);
                        }
                    } else if (currentKey.equalsIgnoreCase("data")) {
                        currentEvent.setData((currentEvent.getData() != null ? currentEvent.getData() : "") + currentValue);
                    } else if (currentKey.isBlank()) {
                        currentEvent.setComment(currentValue);
                    }
                }

                line = reader.readLine();

                // check if a value has been fully read by checking if the previous line ended with
                // \n (then there must be a new line)
                if (currentValue.length() > 0 && line != null) {
                    // clear the key and value
                    currentKey = "";
                    currentValue = "";
                    isReadKey = true;
                }
            }

            // check if the buffer has only one event without separator (\n\n)
            if (currentEvent.getData() != null && currentEvent.getData().length() > 0 && !events.contains(currentEvent)) {
                events.add(currentEvent);
                currentEvent = new SseEvent();
            }
        }

        return events;
    }


    /**
     * Encodes the {@link SseEvent}s into a {@link ByteBuffer}.
     * 
     * @param events the {@link SseEvent}s
     * @return the {@link ByteBuffer} containing the event
     * @throws IOException if something fails
     */
    @Override
    public ByteBuffer encode(final SseEvent... events) throws IOException {
        final StringBuffer buffer = new StringBuffer();

        for (final SseEvent event : events) {

            if (event.getName() != null) {
                buffer.append("event:").append(event.getName()).append("\n");
            }

            if (event.getId() != null) {
                buffer.append("id:").append(event.getId()).append("\n");
            }

            if (event.getComment() != null) {
                final BufferedReader reader = new BufferedReader(new StringReader(event.getComment()));
                String line = reader.readLine();

                while (line != null) {
                    buffer.append(":").append(line).append("\n");
                    line = reader.readLine();
                }
            }

            if (event.getRetry() != SseEvent.RETRY_NOT_SET) {
                buffer.append("retry:").append(event.getRetry()).append("\n");
            }

            final BufferedReader reader = new BufferedReader(new StringReader(event.getData()));
            String line = reader.readLine();

            while (line != null) {
                buffer.append("data:").append(line).append("\n");
                line = reader.readLine();
            }

            buffer.append("\n");
        }

        return ByteBuffer.wrap(buffer.toString().getBytes());
    }

}
