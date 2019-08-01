import com.airepublic.http.sse.api.ISseRegistry;
import com.airepublic.http.sse.api.ISseService;
import com.airepublic.http.sse.impl.SseRegistry;
import com.airepublic.http.sse.impl.SseService;

module com.airepublic.http.sse.impl {
    exports com.airepublic.http.sse.impl;

    requires transitive com.airepublic.http.sse.api;
    requires transitive com.airepublic.http.common;

    requires java.logging;
    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject;
    requires java.annotation;

    provides ISseRegistry with SseRegistry;
    provides ISseService with SseService;

    opens com.airepublic.http.sse.impl;

}