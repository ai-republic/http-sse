package com.airepublic.microprofile.plugin.http.sse.api;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.airepublic.http.sse.api.SseEvent;
import com.airepublic.http.sse.impl.SseService;
import com.airepublic.http.sse.se.SimpleServer;

public class TestSseService {
    final SseService service = new SseService();


    protected SocketChannel openChannel(final URI uri) {
        final boolean isSecure = uri.getScheme().toLowerCase().equals("https");
        int port = uri.getPort();

        if (port <= 0) {
            port = isSecure ? 443 : 80;
        }

        SocketChannel channel;

        try {
            final SocketAddress remote = new InetSocketAddress(uri.getHost(), port);
            channel = SocketChannel.open();
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            channel.configureBlocking(false);
            channel.connect(remote);

            while (!channel.finishConnect()) {
                Thread.onSpinWait();
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create socket channel!", e);
        }

        return channel;
    }


    @Test
    public void testReceive() throws IOException, URISyntaxException {
        // GIVEN:
        // - a server exists which performs SSE handshake and replies with an event
        final SseEvent event = new SseEvent.Builder()
                .withId("1")
                .withName("Test")
                .withComment("a comment")
                .withRetry(1)
                .withData("Hello world").build();

        final SimpleServer server = new SimpleServer(8888, serverChannel -> {
            try {
                service.handshake(serverChannel, null);
                service.send(event, serverChannel, null);
                service.send(event, serverChannel, null);
            } catch (final Exception e) {
                Assertions.fail(e);
            }
        });

        new Thread(server, "SimpleServer").start();

        // WHEN:
        // - the service is called to receive events as a client
        final AtomicBoolean called = new AtomicBoolean(false);
        final Future<Void> future = service.receive(new URI("http://localhost:8888"), e -> called.set(true));
        try {
            Thread.sleep(500L);
            future.cancel(true);
        } catch (InterruptedException | CancellationException e) {
            e.printStackTrace();
        }

        // THEN:
        // - the consumer must have been called
        Assertions.assertTrue(called.get());
    }


    @Test
    public void testSend() throws Exception {
        // GIVEN:
        // - a server exists which performs SSE handshake and replies with an event
        final SseEvent event = new SseEvent.Builder()
                .withId("1")
                .withName("Test")
                .withComment("a comment")
                .withRetry(1)
                .withData("Hello world").build();

        final SimpleServer server = new SimpleServer(8888, serverChannel -> {
            try {
                service.handshake(serverChannel, null);
                service.send(event, serverChannel, null);
                service.send(event, serverChannel, null);
            } catch (final Exception e) {
                Assertions.fail(e);
            }
        });

        new Thread(server, "SimpleServer").start();

        // WHEN:
        // - a client connects to the SSE server
        try {
            final URI uri = new URI("http://localhost:8888");
            final Selector selector = Selector.open();
            final SocketChannel client = openChannel(uri);
            client.register(selector, SelectionKey.OP_READ);
            service.outboundHandshake(uri, selector, client, null, e -> {
                // THEN:
                // - the client should receive the exact event
                Assertions.assertEquals(event.getName(), e.getName());
                Assertions.assertEquals(event.getId(), e.getId());
                Assertions.assertEquals(event.getComment(), e.getComment());
                Assertions.assertEquals(event.getRetry(), e.getRetry());
                Assertions.assertEquals(event.getData(), e.getData());
            });

            // wait for the connect and accept and send
            Thread.sleep(500);

        } finally {
            server.close();
        }

    }


    public static void main(final String[] args) throws IOException, URISyntaxException {
        final TestSseService test = new TestSseService();
        test.testReceive();
    }
}
