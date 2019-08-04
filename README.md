# HTTP SSE

Libraries for receiving and sending SSE (Server-Sent-Events). SSE producers and consumers can be defined via annotations. A standalone mode is also provided including SSL support.

## Receiving SSE events
If you are using the <code>Bootstrap</code> class all you need to do to receive SSE events is simply annotate a method with <code>@SseConsumer</code> and specify the event-source URI like the following:
<p>
<code>@SseConsumer("https://some.server.com/sending/events")</code><br>
<code>public void consumeEvent(SseEvent sseEvent) {</code><br>
<code>...</code><br>
<code>}</code><br>
</p>
<p>
Or <br>
</p>
if you want to receive <code>SseEvent</code>s only using the <code>SseService</code> you can do this simply by calling:
<p>
<code>sseService.receive(new URI("https://to.some.server"), sseEvent -> processTheEvent);</code>
</p>

## Producing SSE events
If you are using the <code>Bootstrap</code> class all you need to do to produce SSE events is simply annotate a method with <code>@SseProducer</code> which must return a <code>SseEvent</code> like the following:
<p>
<code>@SseProducer(path = "/sse/produce", maxTimes = -1, delay = 1, unit = TimeUnit.SECONDS)</code><br>
<code>public SseEvent produceEvent() {</code><br>
<code>&nbsp;&nbsp;return new SseEvent.Builder().withData(words[counter++ % 4]).build();</code><br>
<code>}</code><br>
</p>

This will start the <code>SimpleServer</code> to receive incoming requests, react on the configured URI path, perform the handshake and call the producer method according to the configured delay.
<p/>
<p>
Or <br>
</p>
<p>
if you implement your own server you can just register your producer class with the <code>ISseRegistry</code> and call the <code>SseService</code> like the following:
</p>
<p>
<code>sseService.processRequest(socketChannel, sslContext, sseRegistry);</code>
</p> 
<p>
Or <br>
</p>
if you want to send <code>SseEvent</code>s to an open <code>SocketChannel</code> only using the <code>SseService</code> you can do this simply by calling:
<p>
<code>sseService.handshake(socketChannel, sslEngine);</code><br>
<code>sseService.send(sseEvent, socketChannel, sslEngine);</code>
</p> 

## SSL
To use SSL you only need to supply a <code>SSLContext</code> and/or <code>SSLEngine</code>. This can be easily created using the <code>SslSupport</code> class from the <code>http-common</code> library which is included as dependency with this project.