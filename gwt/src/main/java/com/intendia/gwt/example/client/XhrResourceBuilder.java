package com.intendia.gwt.example.client;

import static java.util.Arrays.asList;

import com.intendia.gwt.autorest.client.RequestResourceBuilder;
import com.intendia.gwt.autorest.client.RequestResponseException.ResponseFormatException;
import elemental2.core.Global;
import elemental2.dom.EventListener;
import elemental2.dom.EventSource;
import elemental2.dom.MessageEvent;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jsinterop.base.Js;

public class XhrResourceBuilder extends RequestResourceBuilder {
    private static final Logger L = Logger.getLogger(XhrResourceBuilder.class.getName());

    public XhrResourceBuilder(String base) { path(base);}

    @SuppressWarnings("unchecked") @Override public <T> T as(Class<? super T> container, Class<?> type) {
        return asList(produces).contains("text/event-stream") ? (T) eventSource(uri()) : super.as(container, type);
    }

    static <T> Observable<T> eventSource(String uri) {
        return Observable.create(em -> {
            try {
                EventSource source = new EventSource(uri);
                CompositeDisposable s = new CompositeDisposable();
                s.add(on(source, "message", ev -> em.onNext(decode(Js.cast(ev)))));
                s.add(on(source, "open", ev -> L.fine("Connection opened (uri: " + uri + ")")));
                s.add(on(source, "error", ev -> {
                    L.log(Level.SEVERE, "Error: " + ev);
                    if (source.readyState == source.CLOSED) {
                        em.onError(new RuntimeException("Event source error (uri: " + uri + ")"));
                    }
                }));
                s.add(Disposables.fromAction(() -> source.close.onInvoke()));
                em.setDisposable(s);
            } catch (Throwable e) {
                L.log(Level.FINE, "Received http error for: " + uri, e);
                em.onError(new RuntimeException("Event source error (uri: " + uri + ")", e));
            }
        });
    }

    static @Nullable <T> T decode(MessageEvent<String> message) {
        try {
            String text = message.data;
            return text == null || text.isEmpty() ? null : Js.cast(Global.JSON.parse(text));
        } catch (Throwable e) {
            throw new ResponseFormatException("Parsing response error", e);
        }
    }

    static Disposable on(EventSource source, String type, EventListener fn) {
        source.addEventListener(type, fn);
        return Disposables.fromAction(() -> source.removeEventListener(type, fn));
    }
}
