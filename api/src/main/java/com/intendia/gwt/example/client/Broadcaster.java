package com.intendia.gwt.example.client;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import com.intendia.gwt.autorest.client.AutoRestGwt;
import io.reactivex.Observable;
import io.reactivex.Single;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@AutoRestGwt @Path("api") @Produces(APPLICATION_JSON) @Consumes(APPLICATION_JSON)
public interface Broadcaster {

    @POST @Path("sent") @Consumes("text/plain") Single<String> sent(String message);

    @GET @Produces("text/event-stream") @Path("listen") Observable<String> listen();

}

