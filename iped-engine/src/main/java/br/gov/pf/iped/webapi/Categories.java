package br.gov.pf.iped.webapi;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import br.gov.pf.iped.webapi.json.DataListJSON;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value = "Categories")
@Path("categories")
public class Categories {

    @ApiOperation(value = "List categories")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public DataListJSON<String> get() throws Exception {

        List<String> categories = Sources.multiSource.getCategories();
        DataListJSON<String> result = new DataListJSON<String>(categories);

        return result;
    }
}