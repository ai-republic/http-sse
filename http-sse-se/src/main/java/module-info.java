module com.airepublic.http.sse.se {
    exports com.airepublic.http.sse.se;

    requires transitive com.airepublic.http.sse.api;
    requires com.airepublic.http.sse.impl;

    requires com.airepublic.http.common;
    requires com.airepublic.reflections;
    requires java.logging;
    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject;
    requires java.annotation;

    requires openwebbeans.se;
    // requires openwebbeans.impl;
    requires jdk.unsupported;

    opens com.airepublic.http.sse.se;

}