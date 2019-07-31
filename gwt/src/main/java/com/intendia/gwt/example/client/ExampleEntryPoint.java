package com.intendia.gwt.example.client;

import static elemental2.dom.DomGlobal.document;

import com.google.gwt.core.client.EntryPoint;
import com.intendia.rxgwt2.elemento.RxElemento;
import elemental2.dom.Element;
import elemental2.dom.HTMLFormElement;
import elemental2.dom.HTMLInputElement;
import io.reactivex.Single;
import jsinterop.base.Js;
import org.jboss.gwt.elemento.core.EventType;

public class ExampleEntryPoint implements EntryPoint {

    public void onModuleLoad() {
        Broadcaster broadcaster = new Broadcaster_RestServiceModel(
                () -> new XhrResourceBuilder("http://localhost:8000/"));

        Element history = document.createElement("pre"); document.body.appendChild(history);

        HTMLFormElement form = Js.cast(document.createElement("form")); document.body.appendChild(form);

        HTMLInputElement sent = Js.cast(document.createElement("input")); form.appendChild(sent);
        sent.type = "text"; sent.required = true;

        Element console = document.createElement("div"); document.body.appendChild(console);

        broadcaster.listen()
                .doOnNext(msg -> history.textContent += msg + "\n")
                .subscribe();

        RxElemento.fromEvent(form, EventType.submit)
                .switchMapSingle(e -> {
                    e.preventDefault();
                    console.textContent = "Sending…";
                    return broadcaster.sent(sent.value)
                            .onErrorResumeNext(err -> Single.just("error: " + err))
                            .doOnSuccess(n -> {
                                sent.value = "";
                                console.textContent = n;
                            });
                })
                .subscribe();
    }
}
