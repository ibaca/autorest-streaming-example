package com.intendia.gwt.example;

import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.SEVERE;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.ReplaySubject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class Servlet {
    static final Logger L = Logger.getLogger("server");

    public static void main(String[] args) throws Exception {
        // assert that PrintWriter.print(String) uses UTF-8, much less verbose than Writer.write(byte[])
        if (defaultCharset() != UTF_8) throw new Exception("default charset must be UTF-8 (-Dfile.encoding=UTF-8)");

        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(new ServletHolder(new HelloServlet()), "/*");

        Server server = new Server(8000);
        server.setHandler(handler);
        server.start();
        server.join();
    }

    @SuppressWarnings("serial")
    public static class HelloServlet extends GenericServlet {
        ReplaySubject<String> broadcasterIn = ReplaySubject.create(1024);
        Observable<Pair> broadcasterOut = broadcasterIn.map(Pair::next);
        private @Nullable Disposable subscription;

        @Override public void init() throws ServletException {
            super.init();
            subscription = broadcasterOut.subscribe(n -> L.info("broadcast " + n));
        }

        @Override public void destroy() {
            super.destroy();
            if (subscription != null) {subscription.dispose(); subscription = null;}
        }

        @Override public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
            if (!(req instanceof HttpServletRequest && res instanceof HttpServletResponse)) {
                throw new ServletException("non-HTTP request or response");
            }

            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            if (CORS(request, response)) return;

            String path = request.getPathInfo();
            switch (path) {
                case "/api/sent": {
                    broadcasterIn.onNext(read(request));

                    // Out
                    response.setStatus(SC_OK);
                    response.addHeader("content-type", "application/json;charset=utf-8");
                    write(response, "\"ok\"");
                } break;
                case "/api/listen": {
                    read(request); // just read, to flush network buffers
                    String client = request.getRemoteAddr();

                    // Out
                    try {
                        response.setCharacterEncoding("utf-8");
                        response.setContentType("text/event-stream");
                        response.setStatus(SC_OK);
                        // By adding this header, and not closing the connection, we disable HTTP chunking,
                        // and we can use write()+flush() to send data in the text/event-stream protocol
                        response.addHeader("Connection", "close");
                        response.flushBuffer();

                        AsyncContext async = request.startAsync();
                        async.setTimeout(TimeUnit.HOURS.toMillis(1));
                        async.addListener(new AsyncListener() {
                            @Override public void onStartAsync(AsyncEvent event) { L.info("on start " + event); }
                            @Override public void onComplete(AsyncEvent event) { L.info("on complete " + event); }
                            @Override public void onTimeout(AsyncEvent event) { L.info("on timeout " + event); }
                            @Override public void onError(AsyncEvent event) { L.info("on error " + event); }
                        });
                        PrintWriter out = response.getWriter();

                        L.info("subscribing " + client);
                        @Nullable String firstOpt = request.getHeader("Last-Event-ID");
                        int first = Integer.parseInt(firstOpt == null ? "0" : firstOpt);
                        broadcasterOut.filter(n -> n.seq > first)
                                .startWith(new Pair(0, "\"subscription success\""))
                                .subscribe(new Observer<Pair>() {
                                    public void onSubscribe(Disposable d) {}
                                    public void onComplete() { async.complete(); }
                                    public void onError(Throwable e) { async.complete(); }
                                    public void onNext(Pair n) {
                                        L.info("sending '" + n.msg + "'(" + n.seq + ") to " + client);
                                        if (n.seq > 0) out.print("id: " + n.seq + "\n");
                                        for (String row : n.msg.split("\n")) out.print("data: " + row + "\n");
                                        out.print("\n");
                                        out.flush();
                                    }
                                });
                    } catch (Throwable e) {
                        L.log(SEVERE, "error subscribing " + client, e);
                    }

                } break;
                default: throw new ServletException(path + " not handled");
            }
        }
    }

    static class Pair {
        static Pair next(String msg) { return new Pair(SEQ.getAndIncrement(), msg); }
        static AtomicInteger SEQ = new AtomicInteger((int) (Math.random() * 9999.));
        final int seq;
        final String msg;
        private Pair(int seq, String msg) { this.seq = seq; this.msg = msg; }
        @Override public String toString() { return "Pair{seq=" + seq + ", msg=" + msg + '}'; }
    }

    private static boolean CORS(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.addHeader("Access-Control-Allow-Headers", "Content-Type");
        res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE");
        res.addHeader("Access-Control-Allow-Origin", req.getHeader("origin"));
        res.addHeader("Access-Control-Max-Age", "3600");

        if (!req.getMethod().equals("OPTIONS")) return false;
        res.setStatus(SC_OK);
        write(res, "");
        return true;
    }

    private static void write(HttpServletResponse output, String data) throws IOException {
        try (PrintWriter out = output.getWriter()) {
            out.print(data);
        }
    }

    public static String read(HttpServletRequest input) throws IOException {
        try (BufferedReader reader = input.getReader()) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
