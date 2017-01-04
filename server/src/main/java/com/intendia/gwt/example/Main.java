package com.intendia.gwt.example;

import static java.util.Collections.singletonList;
import static java.util.logging.Level.SEVERE;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.ReplaySubject;

public class Main {
    static final Logger L = Logger.getLogger("server");

    public static void main(String[] args) throws Exception {
        ReplaySubject<String> broadcasterIn = ReplaySubject.create(1024);
        Observable<Pair> broadcasterOut = broadcasterIn.map(Pair::new);
        broadcasterOut.subscribe(System.out::println);

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8000), 0 /*default*/);
        httpServer.setExecutor(null /*default*/);
        httpServer.createContext("/api/sent", httpExchange -> {
            if (CORS(httpExchange)) return;
            broadcasterIn.onNext(read(httpExchange.getRequestBody()));

            // Out
            httpExchange.sendResponseHeaders(200, 0);
            write(httpExchange, "\"ok\"");
        });
        httpServer.createContext("/api/listen", exchange -> {
            if (CORS(exchange)) return;
            read(exchange.getRequestBody()); // just read, to flush network buffers

            // Out
            try {
                exchange.getResponseHeaders().put("content-type", singletonList("text/event-stream"));
                exchange.sendResponseHeaders(200, 0);
                final OutputStream out = exchange.getResponseBody();

                L.info("subscribing " + exchange.getRemoteAddress());
                @Nullable String firstOpt = exchange.getRequestHeaders().getFirst("Last-Event-ID");
                int first = Integer.parseInt(firstOpt == null ? "0" : firstOpt);
                broadcasterOut.filter(n -> n.seq > first).subscribe(new Subscriber<Pair>() {
                    public void onCompleted() {
                        try { out.close(); } catch (IOException ignore) { }
                    }
                    public void onError(Throwable e) {
                        try { out.close(); } catch (IOException ignore) { }
                    }
                    public void onNext(Pair n) {
                        try {
                            L.info("sending data to " + exchange.getRemoteAddress());
                            out.write(("id: " + n.seq + "\n").getBytes());
                            for (String row : n.msg.split("\n")) out.write(("data: " + row + "\n").getBytes());
                            out.write(("\n").getBytes()); out.flush();
                        } catch (IOException e) {
                            L.log(SEVERE, "error sending data to " + exchange.getRemoteAddress() + ": " + e, e);
                        }
                    }
                });
            } catch (Throwable e) {
                L.log(SEVERE, "error subscribing " + exchange.getRemoteAddress(), e);
            }
        });
        httpServer.start();
    }

    static class Pair {
        static AtomicInteger SEQ = new AtomicInteger((int) (Math.random() * 9999.));
        final int seq;
        final String msg;
        Pair(String msg) {
            this.seq = SEQ.getAndIncrement();
            this.msg = msg;
        }
        @Override public String toString() {
            return "Pair{seq=" + seq + ", msg='" + msg + '\'' + '}';
        }
    }

    private static boolean CORS(HttpExchange httpExchange) throws IOException {
        Headers headers = httpExchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Headers", "Content-Type");
        headers.add("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE");
        headers.add("Access-Control-Allow-Origin", httpExchange.getRequestHeaders().getFirst("origin"));
        headers.add("Access-Control-Max-Age", "3600");

        if (!httpExchange.getRequestMethod().equals("OPTIONS")) return false;
        httpExchange.sendResponseHeaders(200, 0);
        write(httpExchange, "");
        return true;
    }

    private static void write(HttpExchange output, String data) throws IOException {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(output.getResponseBody()))) {
            out.write(data);
        }
    }

    public static String read(InputStream input) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }
}
