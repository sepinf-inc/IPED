package br.gov.pf.iped.webapi;

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("")
public class Root {
    @GET
    public static Response root() throws URISyntaxException {
        return Response.temporaryRedirect(new URI("./swagger.json")).build();
    }
}
