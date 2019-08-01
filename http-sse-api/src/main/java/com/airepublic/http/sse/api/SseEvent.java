package com.airepublic.http.sse.api;

import java.util.Objects;

/**
 * The representation of an SSE event.
 * 
 * @author Torsten Oltmanns
 *
 */
public class SseEvent {
    public static final long RETRY_NOT_SET = -1;
    private String id;
    private String name;
    private String comment;
    private String data;
    private long retry = RETRY_NOT_SET;

    /**
     * A builder to create {@link SseEvent}s.
     * 
     * @author Torsten Oltmanns
     *
     */
    public static class Builder {
        private final SseEvent sseEvent = new SseEvent();


        /**
         * Adds the event id.
         * 
         * @param id the event id
         * @return the {@link Builder}
         */
        public Builder withId(final String id) {
            sseEvent.setId(id);
            return this;
        }


        /**
         * Adds the event name.
         * 
         * @param name the event name
         * @return the {@link Builder}
         */
        public Builder withName(final String name) {
            sseEvent.setName(name);
            return this;
        }


        /**
         * Adds the event comment.
         * 
         * @param comment the event comment
         * @return the {@link Builder}
         */
        public Builder withComment(final String comment) {
            sseEvent.setComment(comment);
            return this;
        }


        /**
         * Adds the event connection retry delay.
         * 
         * @param retryDelay the event connection retry delay
         * @return the {@link Builder}
         */
        public Builder withRetry(final long retryDelay) {
            sseEvent.setRetry(retryDelay);
            return this;
        }


        /**
         * Adds the event data.
         * 
         * @param data the event data
         * @return the {@link Builder}
         */
        public Builder withData(final String data) {
            sseEvent.setData(data);
            return this;
        }


        /**
         * Builds the {@link SseEvent}.
         * 
         * @return the {@link SseEvent}
         */
        public SseEvent build() {
            return sseEvent;
        }
    }


    /**
     * Constructor.
     */
    public SseEvent() {
    }


    /**
     * Gets the event id.
     * 
     * @return the event id
     */
    public String getId() {
        return id;
    }


    /**
     * Sets the event id.
     * 
     * @param id the event id
     */
    public void setId(final String id) {
        this.id = id;
    }


    /**
     * Gets the event name.
     * 
     * @return the event name
     */
    public String getName() {
        return name;
    }


    /**
     * Sets the event name.
     * 
     * @param name the event name
     */
    public void setName(final String name) {
        this.name = name;
    }


    /**
     * Gets the event comment.
     * 
     * @return the event comment
     */
    public String getComment() {
        return comment;
    }


    /**
     * Sets the event comment.
     * 
     * @param comment the event comment
     */
    public void setComment(final String comment) {
        this.comment = comment;
    }


    /**
     * Gets the event data.
     * 
     * @return the event data
     */
    public String getData() {
        return data;
    }


    /**
     * Sets the event data.
     * 
     * @param data the event data
     */
    public void setData(final String data) {
        this.data = data;
    }


    /**
     * Gets the event connection retry delay.
     * 
     * @return the event connection retry delay
     */
    public long getRetry() {
        return retry;
    }


    /**
     * Sets the event connection retry delay.
     * 
     * @param reconnectDelay the event connection retry delay
     */
    public void setRetry(final long reconnectDelay) {
        retry = reconnectDelay;
    }


    /**
     * Gets whether a event connection retry delay is set.
     * 
     * @return the flag whether the event connection retry delay is set
     */
    public boolean isReconnectDelaySet() {
        return retry != -1L;
    }


    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }


    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SseEvent other = (SseEvent) obj;
        return Objects.equals(id, other.id) && Objects.equals(name, other.name);
    }


    @Override
    public String toString() {
        return "SseEvent [id=" + id + ", name=" + name + ", comment=" + comment + ", data=" + data + ", retry=" + retry + "]";
    }
}
