module com.airepublic.http.sse.api {
    exports com.airepublic.http.sse.api;

    requires transitive com.airepublic.http.common;
    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject;
    requires java.annotation;

    opens com.airepublic.http.sse.api;

}