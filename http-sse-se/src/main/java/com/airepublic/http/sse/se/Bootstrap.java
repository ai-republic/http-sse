package com.airepublic.http.sse.se;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.net.ssl.SSLContext;

import com.airepublic.http.common.SslSupport;
import com.airepublic.http.sse.api.ISseRegistry;
import com.airepublic.http.sse.api.ISseService;
import com.airepublic.http.sse.api.SseConsumer;
import com.airepublic.http.sse.api.SseProducer;
import com.airepublic.reflections.Reflections;

/**
 * Bootstraps a {@link SimpleServer} as an SSE server serving {@link SseProducer} and
 * {@link SseConsumer} resources.
 * 
 * @author Torsten Oltmanns
 *
 */
public class Bootstrap {
    private static final Logger logger = Logger.getGlobal();
    @Inject
    private ISseRegistry sseRegistry;
    @Inject
    private ISseService sseService;


    /**
     * Constructor (for CDI use only!).
     */
    public Bootstrap() {
    }


    /**
     * Constructor (for non-CDI use only!).
     * 
     * @param sseRegistry the {@link ISseRegistry}
     * @param sseService the {@link ISseService}
     */
    public Bootstrap(final ISseRegistry sseRegistry, final ISseService sseService) {
        this.sseRegistry = sseRegistry;
        this.sseService = sseService;

        init();
    }


    /**
     * Discovers and initializes the resources.
     */
    @PostConstruct
    public void init() {
        logger.info("Starting SSE server...");
        logger.info("Searching for SSE resources...");

        final Set<Class<?>> resources = findSseResources();

        if (resources != null && !resources.isEmpty()) {
            logger.info("Found SSE Resources:");

            for (final Class<?> resource : resources) {
                logger.info("\t" + resource.getName());

                try {
                    addResource(resource);
                } catch (final Exception e) {
                    logger.log(Level.SEVERE, "Failed to add SSE resource: " + resource.getName());
                }
            }
        } else {
            logger.info("No SSE Resources found!");
        }

        // use the configured or default context path
        logger.info("Finished configuring SSE server!");
    }


    /**
     * Finds the SSE classes annotated with {@link SseProducer} or {@link SseConsumer}.
     * 
     * @return all classes found or null
     */
    Set<Class<?>> findSseResources() {
        return Reflections.findClassesWithMethodAnnotations(SseProducer.class, SseConsumer.class);
    }


    /**
     * Adds SSE resources by scanning the specified resource for {@link SseProducer} or
     * {@link SseConsumer} annotations.
     * 
     * @param resource the resource class to scan
     */
    void addResource(final Class<?> resource) {
        try {

            // check for SseProducer or SseConsumer annotated methods
            for (final Method method : Reflections.getAnnotatedMethods(resource, SseProducer.class, SseConsumer.class)) {

                if (method.isAnnotationPresent(SseProducer.class)) {
                    final SseProducer annotation = method.getAnnotation(SseProducer.class);
                    sseRegistry.registerSseProducer(annotation.path(), resource, method);

                    logger.info("\t\tAdded SSE mapping for outbounded SSE events: " + resource.getName() + ":" + method.getName() + " -> " + annotation.path());
                } else if (method.isAnnotationPresent(SseConsumer.class)) {
                    try {
                        final SseConsumer annotation = method.getAnnotation(SseConsumer.class);
                        final URI uri = new URI(annotation.value());
                        final Object object = CDI.current().select(resource).get();

                        final Future<Void> sseConsumer = sseService.receive(uri, event -> {
                            try {
                                method.invoke(object, event);
                            } catch (final Exception e) {
                                logger.log(Level.SEVERE, "Error invoking SSE event method:", e);
                            }
                        });

                        sseRegistry.registerSseConsumer(uri, sseConsumer);

                        logger.info("\t\tAdded SSE mapping for inbounded SSE events: " + resource.getName() + ":" + method.getName() + " -> " + uri);
                    } catch (final Exception e) {
                        logger.log(Level.SEVERE, "Error adding SSE resource!", e);
                    }
                } else {
                    logger.warning("SSE inbound event method is lacking the SseConsumer annotation to define the source of the events - it will not receive any events!");
                }
            }

        } catch (final Exception e) {
            // otherwise use the configured or default context-path
        }
    }


    /**
     * Starts the server on the specified port without SSL.
     * 
     * @param port the port
     * @throws IOException if something goes wrong
     */
    public void startServer(final int port) throws IOException {
        startServer(port, null);
    }


    /**
     * Starts the server on the specified port with SSL if a {@link SSLContext} can be created with
     * the specified keystore and truststore.
     * 
     * @param port the port
     * @param keystoreFile the path to the keystore file
     * @param keystorePassword the keystore password
     * @param truststoreFile the path to the truststore file
     * @param truststorePassword the truststore password
     * @throws IOException if something goes wrong
     */
    public void startServer(final int port, final String keystoreFile, final String keystorePassword, final String truststoreFile, final String truststorePassword) throws IOException {
        final SSLContext sslContext = SslSupport.createServerSSLContext(keystoreFile, keystorePassword, truststoreFile, truststorePassword);
        startServer(port, sslContext);
    }


    /**
     * Starts the server on the specified port with SSL if {@link SSLContext} is not
     * <code>null</code>.
     * 
     * @param port the port
     * @param sslContext the {@link SSLContext}
     * @throws IOException if something goes wrong
     */
    public void startServer(final int port, final SSLContext sslContext) throws IOException {
        try (final SimpleServer server = new SimpleServer(port, serverChannel -> sseService.processRequest(serverChannel, sslContext, sseRegistry))) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    server.close();
                }
            });

            server.run();
        }
    }


    /**
     * Starts the server as an application. Command line arguments can be as follows:
     * <p>
     * SYNTAX: [&lt;port (default 8080)&gt;] | [&lt;port&gt; &lt;keystorefile&gt;
     * &lt;keystorepassword&gt; &lt;truststorefile&gt; &lt;truststorepassword&gt;]
     * </p>
     * 
     * @param args see syntax description
     * @throws IOException if something goes wrong
     */
    public static void main(final String[] args) throws IOException {
        final Bootstrap bootstrap = SeContainerInitializer.newInstance().initialize().select(Bootstrap.class).get();
        int port = 8080;

        if (args != null && args.length <= 1) {
            if (args.length == 1) {
                port = Integer.valueOf(args[0]);
            }

            bootstrap.startServer(port);
        } else if (args != null && args.length == 5) {
            port = Integer.valueOf(args[0]);
            final String keystoreFile = args[1];
            final String keystorePassword = args[2];
            final String truststoreFile = args[3];
            final String truststorePassword = args[4];

            bootstrap.startServer(port, keystoreFile, keystorePassword, truststoreFile, truststorePassword);
        } else {
            System.out.println("SYNTAX: [<port (default 8080)>] | [<port> <keystorefile> <keystorepassword> <truststorefile> <truststorepassword>]");
        }
    }
}
