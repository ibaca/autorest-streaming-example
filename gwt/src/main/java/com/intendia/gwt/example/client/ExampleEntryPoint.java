package com.intendia.gwt.example.client;

import static com.intendia.rxgwt.elemental2.RxElemental2.submit;
import static elemental2.dom.DomGlobal.document;

import com.google.gwt.core.client.EntryPoint;
import com.intendia.rxgwt.elemental2.RxElemental2;
import elemental2.dom.Element;
import elemental2.dom.HTMLFormElement;
import elemental2.dom.HTMLInputElement;
import jsinterop.base.Js;
import rx.Observable;

public class ExampleEntryPoint implements EntryPoint {

    public void onModuleLoad() {
        Broadcaster broadcaster = new Broadcaster_RestServiceModel(() -> new XhrResourceBuilder()
                .path("http://localhost:8000/"));

        Element history = document.createElement("pre"); document.body.appendChild(history);

        HTMLFormElement form = Js.cast(document.createElement("form")); document.body.appendChild(form);

        HTMLInputElement sent = Js.cast(document.createElement("input")); form.appendChild(sent);
        sent.type = "text"; sent.required = true;

        Element console = document.createElement("div"); document.body.appendChild(console);

        broadcaster.listen()
                .doOnNext(msg -> history.textContent += msg + "\n")
                .subscribe();

        RxElemental2.fromEvent(form, submit)
                .switchMap(e -> {
                    e.preventDefault();
                    console.textContent = "Sending…";
                    return broadcaster.sent(sent.value)
                            .doOnCompleted(() -> {
                                sent.value = "";
                                console.textContent = "Success!";
                            })
                            .onErrorResumeNext(err -> {
                                console.textContent = "Error: " + err;
                                return Observable.empty();
                            });
                })
                .subscribe();
    }
}
