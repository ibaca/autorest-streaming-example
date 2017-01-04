package com.intendia.gwt.example.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.intendia.rxgwt.client.RxEvents;
import rx.Observable;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaPlugins;

public class ExampleEntryPoint implements EntryPoint {

    public void onModuleLoad() {
        RxJavaPlugins.getInstance().registerErrorHandler(new RxJavaErrorHandler() {
            @Override public void handleError(Throwable e) {
                GWT.getUncaughtExceptionHandler().onUncaughtException(e);
            }
        });
        Broadcaster broadcaster = new Broadcaster_RestServiceModel(() -> new XhrResourceBuilder()
                .path("http://localhost:8000/"));

        FlowPanel history = new FlowPanel(); RootPanel.get().add(history);

        FormPanel form = new FormPanel(); RootPanel.get().add(form);
        form.setAction("#");

        TextBox sent = new TextBox(); form.add(sent);
        sent.getElement().setAttribute("required", "");

        Label console = new Label(); RootPanel.get().add(console);

        broadcaster.listen()
                .doOnNext(msg -> history.add(new Label(msg)))
                .subscribe();

        RxEvents.submit(form)
                .switchMap(e -> {
                    console.setText("Sending…");
                    return broadcaster.sent(sent.getText())
                            .doOnCompleted(() -> {
                                sent.setValue("");
                                console.setText("Success!");
                            })
                            .onErrorResumeNext(err -> {
                                console.setText("Error: " + err);
                                return Observable.empty();
                            });
                })
                .subscribe();
    }

}
