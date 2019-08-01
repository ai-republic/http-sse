package com.airepublic.http.sse.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.airepublic.http.common.BufferUtil;
import com.airepublic.http.sse.api.SseEvent;

/**
 * Reads synchronous or asynchronous {@link SseEvent}s from {@link ByteBuffer}s and puts them in a
 * queue.
 * 
 * @author Torsten Oltmanns
 *
 */
public class SseAsyncEventReader {
    private SseEvent currentEvent = new SseEvent();
    private final Queue<SseEvent> events = new ConcurrentLinkedDeque<>();
    private boolean isReadKey = true;
    private String currentKey = "";
    private String currentValue = "";


    /**
     * Receive the next {@link ByteBuffer} to process.
     * 
     * @param buffer the {@link ByteBuffer}
     * @param isChunked flag whether the source connection is using chunked transfer protocol
     * @return a flag whether a new {@link SseEvent} is availble
     * @throws IOException if something goes wrong
     */
    public boolean receiveBuffer(final ByteBuffer buffer, final boolean isChunked) throws IOException {
        String line = "";
        boolean addedEvent = false;

        // read all blank lines
        while (line != null && line.isBlank()) {
            buffer.mark();
            line = BufferUtil.readLine(buffer, Charset.forName("UTF-8"));
        }

        buffer.reset();

        if (line == null) {
            return false;
        }

        int contentLength = 0;

        if (isChunked) {
            // first line contains the content-length in hexadecimal
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
                    addedEvent = true;
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

                // check if reading the value part
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
                addedEvent = true;
                currentEvent = new SseEvent();
            }
        }

        return addedEvent;
    }


    /**
     * Poll the next {@link SseEvent} in the queue.
     * 
     * @return the next {@link SseEvent} or <code>null</code>
     */
    public SseEvent poll() {
        return events.isEmpty() ? null : events.poll();
    }
}
