package com.intendia.gwt.example.client;

import static com.intendia.gwt.autorest.client.CollectorResourceVisitor.Param.expand;
import static elemental2.core.Global.JSON;
import static elemental2.core.Global.encodeURI;
import static elemental2.core.Global.encodeURIComponent;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import com.intendia.gwt.autorest.client.CollectorResourceVisitor;
import elemental2.dom.EventListener;
import elemental2.dom.EventSource;
import elemental2.dom.FormData;
import elemental2.dom.MessageEvent;
import elemental2.dom.MessagePort;
import elemental2.dom.XMLHttpRequest;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;
import rx.Observable;
import rx.Single;
import rx.Subscriber;
import rx.Subscription;
import rx.annotations.Experimental;
import rx.internal.producers.QueuedProducer;
import rx.internal.producers.SingleDelayedProducer;
import rx.subscriptions.Subscriptions;

@Experimental @SuppressWarnings("GwtInconsistentSerializableClass")
public class XhrResourceBuilder extends CollectorResourceVisitor {
    private static final Logger log = Logger.getLogger(XhrResourceBuilder.class.getName());

    @Override @SuppressWarnings("unchecked") public <T> T as(Class<? super T> container, Class<?> type) {
        if (Single.class.equals(container)) return (T) single();
        if (Observable.class.equals(container)) return (T) observe();
        throw new UnsupportedOperationException("unsupported type " + container);
    }

    public <T> Observable<T> observe() {
        if (Stream.of(produces).anyMatch("text/event-stream"::equals)) {
            //noinspection Convert2MethodRef,unchecked
            return (Observable) Observable.<String>create(s -> eventSourceSubscription(s));
        } else {
            //noinspection Convert2MethodRef
            return Observable.<T[]>create(s -> xmlHttpRequestSubscription(s))
                    .flatMapIterable(o -> o == null ? emptyList() : asList(o));
        }
    }

    public <T> Single<T> single() {
        //noinspection Convert2MethodRef
        return Observable.<T>create(s -> xmlHttpRequestSubscription(s)).toSingle();
    }

    public String query() {
        String q = "";
        for (Param p : expand(queryParams)) q += (q.isEmpty() ? "" : "&") + encode(p.k) + "=" + encode(p.v.toString());
        return q.isEmpty() ? "" : "?" + q;
    }

    public String uri() {
        String uri = "";
        for (String path : paths) uri += path;
        return encodeURI(uri) + query();
    }

    /**
     * Local file-system (file://) does not return any status codes. Therefore - if we read from the file-system we
     * accept all codes. This is for instance relevant when developing a PhoneGap application.
     */
    private boolean isExpected(int status, String uri) {
        switch (status) {
            case 200: return true;
            case 201: return true;
            case 202: return true;
            case 204: return true;
            case 1223: return true;
            default: return uri.startsWith("file");
        }
    }

    private <T> void xmlHttpRequestSubscription(Subscriber<T> s) {
        final XMLHttpRequest xhr = new XMLHttpRequest();
        final String uri = uri();
        xhr.open(method, uri);
        xhr.setRequestHeader(ACCEPT, stream(produces).collect(joining(", ")));
        xhr.setRequestHeader(CONTENT_TYPE, stream(consumes).collect(joining(", ")));
        for (Param h : headerParams) xhr.setRequestHeader(h.k, Objects.toString(h.v));

        SingleDelayedProducer<T> producer = new SingleDelayedProducer<>(s);
        try {
            xhr.onreadystatechange = Js.cast((Fn) () -> {
                if (s.isUnsubscribed()) return;
                if (xhr.readyState == XMLHttpRequest.DONE) {
                    if (!isExpected((int) xhr.status, uri)) {
                        s.onError(new FailedStatusCodeException(xhr));
                    } else {
                        try {
                            log.fine("Received http response for request: " + uri);
                            String text = xhr.responseText;
                            if (text == null || text.isEmpty()) {
                                producer.setValue(null);
                            } else {
                                producer.setValue(parse(text));
                            }
                        } catch (Throwable e) {
                            log.log(Level.FINE, "Could not parse response: " + e, e);
                            s.onError(new ResponseFormatException(xhr, e));
                        }
                    }
                }
            });
            s.setProducer(producer);
            s.add(Subscriptions.create(xhr::abort));

            if (data != null) {
                xhr.setRequestHeader(CONTENT_TYPE, APPLICATION_JSON);
                xhr.send(stringify(data));
            } else if (!formParams.isEmpty()) {
                xhr.setRequestHeader(CONTENT_TYPE, MULTIPART_FORM_DATA);
                FormData form = new FormData();
                formParams.forEach(p -> form.append(p.k, stringify(p.v)));
                xhr.send(form);
            } else {
                xhr.send();
            }
        } catch (Throwable e) {
            log.log(Level.FINE, "Received http error for: " + uri, e);
            s.onError(new RequestResponseException(xhr, e));
        }
    }

    private <T> void eventSourceSubscription(Subscriber<T> s) {
        final EventSource source = new EventSource(uri());
        final QueuedProducer<T> producer = new QueuedProducer<>(s);
        try {
            s.add(subscribeEventListener(source, "message", evt -> {
                producer.onNext(parse(Js.<MessageEvent<String>>cast(evt).data));
            }));
            s.add(subscribeEventListener(source, "open", evt -> {
                log.fine("Connection opened: " + uri());
            }));
            s.add(subscribeEventListener(source, "error", evt -> {
                log.log(Level.SEVERE, "Error: " + evt);
                if (source.readyState == source.CLOSED) {
                    producer.onError(new RuntimeException("Event source error"));
                }
            }));
            s.setProducer(producer);
            s.add(Subscriptions.create(() -> {
                // hack because elemental API EventSource.close is missing
                Js.<MessagePort>uncheckedCast(source).close();
            }));
        } catch (Throwable e) {
            log.log(Level.FINE, "Received http error for: " + uri(), e);
            s.onError(new RuntimeException("Event source error", e));
        }
    }

    public static Subscription subscribeEventListener(EventSource source, String type, EventListener fn) {
        source.addEventListener(type, fn);
        return Subscriptions.create(() -> source.removeEventListener(type, fn));
    }

    public static class RequestResponseException extends RuntimeException {
        protected final XMLHttpRequest xhr;
        public RequestResponseException(XMLHttpRequest xhr, String msg) { super(msg); this.xhr = xhr; }
        public RequestResponseException(XMLHttpRequest xhr, Throwable cause) { super(cause); this.xhr = xhr; }
        public XMLHttpRequest getXhr() { return xhr; }
    }

    public static class ResponseFormatException extends RequestResponseException {
        public ResponseFormatException(XMLHttpRequest xhr, Throwable e) { super(xhr, e); }
    }

    public static class FailedStatusCodeException extends RequestResponseException {
        public FailedStatusCodeException(XMLHttpRequest xhr) { super(xhr, xhr.statusText); }
        public int getStatusCode() { return (int) xhr.status; }
    }

    private static <T> T parse(String text) {return Js.cast(JSON.parse(text)); }

    private static String stringify(Object value) { return JSON.stringify(value); }

    @JsFunction
    public interface Fn {
        void onInvoke();
    }

    private static String encode(String decodedURLComponent) {
        return encodeURIComponent(decodedURLComponent).replaceAll("%20", "+");
    }
}
