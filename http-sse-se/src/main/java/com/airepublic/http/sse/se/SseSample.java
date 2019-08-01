package com.airepublic.http.sse.se;

import java.util.concurrent.TimeUnit;

import com.airepublic.http.sse.api.SseConsumer;
import com.airepublic.http.sse.api.SseEvent;
import com.airepublic.http.sse.api.SseProducer;

public class SseSample {
    private final static String[] words = { "Hello", "world", "of", "SSE" };
    private int counter = 0;


    @SseProducer(path = "/sse/produce", maxTimes = -1, delay = 1, unit = TimeUnit.SECONDS)
    public SseEvent produce() {
        return new SseEvent.Builder().withData(words[counter++ % 4]).build();
    }


    @SseConsumer("https://api.boerse-frankfurt.de:443/data/price_information?isin=US00724F1012&mic=XFRA")
    public void consumes(final SseEvent event) {
        System.out.println("received event: " + event);
    }
}
