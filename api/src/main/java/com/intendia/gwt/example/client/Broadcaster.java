package com.intendia.gwt.example.client;

import com.intendia.gwt.autorest.client.AutoRestGwt;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import rx.Observable;

@AutoRestGwt @Path("api") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public interface Broadcaster {

    @POST @Path("sent") @Consumes("text/plain") Observable<String> sent(String message);

    @GET @Produces("text/event-stream") @Path("listen") Observable<String> listen();

}

