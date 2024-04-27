package iped.engine.webapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.data.ItemId;
import iped.engine.data.ItemIdSet;
import iped.engine.search.IPEDSearcher;
import iped.engine.webapi.json.DataListJSON;
import iped.engine.webapi.json.DocIDJSON;
import iped.engine.webapi.json.SourceToIDsJSON;
import iped.search.IMultiSearchResult;

@Api(value = "Bookmarks")
@Path("bookmarks")
public class Bookmarks {

    @ApiOperation(value = "List bookmarks")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public DataListJSON<String> getAll() {
        Set<String> bookmarks = Sources.multiSource.getMultiBookmarks().getBookmarkSet();
        String[] IDs = bookmarks.toArray(new String[0]);
        return new DataListJSON<String>(IDs);
    }

    @ApiOperation(value = "List bookmark documents")
    @GET
    @Path("{bookmark}")
    @Produces(MediaType.APPLICATION_JSON)
    public SourceToIDsJSON get(@PathParam("bookmark") String bookmark) throws Exception {

        IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        IMultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiBookmarks().filterBookmarks(result, Collections.singleton(bookmark));

        List<DocIDJSON> docs = new ArrayList<DocIDJSON>();
        for (IItemId id : result.getIterator()) {
            docs.add(new DocIDJSON(Sources.sourceIntToString.get(id.getSourceId()), id.getId()));
        }

        return new SourceToIDsJSON(docs);
    }

    @ApiOperation(value = "Add documents to bookmark")
    @PUT
    @Path("{bookmark}/add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response insertLabel(@PathParam("bookmark") String bookmark, @ApiParam(required = true) DocIDJSON[] docs) {
        IMultiBookmarks mm = Sources.multiSource.getMultiBookmarks();
        ItemIdSet itemIds = new ItemIdSet();
        for (DocIDJSON d : docs) {
            itemIds.add(new ItemId(Sources.sourceStringToInt.get(d.getSource()), d.getId()));
        }
        mm.addBookmark(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }

    @ApiOperation(value = "Remove documents from bookmark")
    @PUT
    @Path("{bookmark}/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeLabel(@PathParam("bookmark") String bookmark, @ApiParam(required = true) DocIDJSON[] docs) {
        IMultiBookmarks mm = Sources.multiSource.getMultiBookmarks();
        ItemIdSet itemIds = new ItemIdSet();
        for (DocIDJSON d : docs) {
            itemIds.add(new ItemId(Sources.sourceStringToInt.get(d.getSource()), d.getId()));
        }
        mm.removeBookmark(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }

    @ApiOperation(value = "Create bookmark")
    @POST
    @Path("{bookmark}")
    public Response addLabel(@PathParam("bookmark") String bookmark) {
        IMultiBookmarks mm = Sources.multiSource.getMultiBookmarks();
        mm.newBookmark(bookmark);
        mm.saveState();
        return Response.ok().build();
    }

    @ApiOperation(value = "Delete bookmark")
    @DELETE
    @Path("{bookmark}")
    public Response delLabel(@PathParam("bookmark") String bookmark) {
        IMultiBookmarks mm = Sources.multiSource.getMultiBookmarks();
        mm.delBookmark(bookmark);
        mm.saveState();
        return Response.ok().build();
    }

    @ApiOperation(value = "Rename bookmark")
    @PUT
    @Path("{old}/rename/{new}")
    public Response changeLabel(@PathParam("old") String oldLabel, @PathParam("new") String newLabel) {
        IMultiBookmarks mm = Sources.multiSource.getMultiBookmarks();
        mm.renameBookmark(oldLabel, newLabel);
        mm.saveState();
        return Response.ok().build();
    }

}