package com.airepublic.http.sse.se;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Super simple server delegating the {@link SocketChannel} to a {@link Consumer} to handle the IO.
 * 
 * @author Torsten Oltmanns
 *
 */
public class SimpleServer implements Runnable, AutoCloseable {
    private final Consumer<SocketChannel> consumer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ServerSocketChannel server;
    private final Selector selector;
    private final int port;


    /**
     * Constructor.
     * 
     * @param port the server port
     * @param consumer the {@link Consumer} to accept and handle connections
     * @throws IOException if server could not be initialized
     */
    public SimpleServer(final int port, final Consumer<SocketChannel> consumer) throws IOException {
        this.port = port;
        this.consumer = consumer;

        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            selector = Selector.open();
        } catch (final Exception e) {
            close();
            throw e;
        }

        // register to VM shutdown to clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                SimpleServer.this.close();
            }
        });
    }


    /**
     * Starts this server in a new {@link Thread}.
     */
    public void start() {
        new Thread(this).start();
    }


    /**
     * Listens to accept events from the {@link ServerSocketChannel} and notifies the
     * {@link Consumer}.
     */
    @Override
    public void run() {
        try {
            server.bind(new InetSocketAddress(port));
            server.register(selector, SelectionKey.OP_ACCEPT);
        } catch (final Exception e) {
            close();
            throw new RuntimeException(e);
        }

        while (selector.isOpen() && !closed.get()) {
            try {
                selector.select();

                if (selector.isOpen()) {
                    final Iterator<SelectionKey> it = selector.keys().iterator();

                    while (it.hasNext()) {
                        final SelectionKey key = it.next();

                        if (key.isValid() && key.isAcceptable()) {
                            consumer.accept(server.accept());
                        }
                    }
                }
            } catch (final IOException e) {
            }
        }

        close();
    }


    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.keyFor(selector).cancel();

            try {
                server.close();
            } catch (final IOException e) {
            }

            try {
                selector.close();
            } catch (final IOException e) {
            }
        }
    }
}