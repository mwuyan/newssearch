package edu.northwestern.ssa.api;

import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor;
import edu.northwestern.ssa.AwsSignedRestRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.utils.IoUtils;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map;

/*
Code help from:
https://stackoverflow.com/questions/22687771/how-to-convert-jsonobjects-to-jsonarray
https://stackoverflow.com/questions/6697147/json-iterate-through-jsonarray
https://www.elastic.co/guide/en/elasticsearch/reference/current/search-search.html#search-api-response-body

 */

@Path("/search")
public class Search {

    /** when testing, this is reachable at http://localhost:8080/api/search?query=hello */
    @GET
    public Response getMsg(@QueryParam("query") String q,
                           @QueryParam("language") String language,
                           @QueryParam("date") String date,
                           @QueryParam("count") String count,
                           @QueryParam("offset") String offset) throws IOException {

        if(q == null) return Response.status(400).header("Access-Control-Allow-Origin", "*").build();

        Map<String,String> query = new HashMap<String, String>();
        String qstring = "txt:( " + q.replaceAll(" ", " AND ") + ")";

        if(language != null) qstring += " AND lang:" + language;
        if(date != null) qstring += " AND date:" + date;
        query.put("q",qstring);
        if(count != null) query.put("size", count);
        if(offset != null) query.put("from",offset);
        query.put("track_total_hits","true");

        AwsSignedRestRequest request = new AwsSignedRestRequest("es");
        HttpExecuteResponse response = request.restRequest(SdkHttpMethod.GET, System.getProperty("ELASTIC_SEARCH_HOST"),
                System.getProperty("ELASTIC_SEARCH_INDEX") + "/_search", Optional.of(query));

        JSONObject resbody = new JSONObject(IoUtils.toUtf8String(response.responseBody().get()));
        JSONArray hits = resbody.getJSONObject("hits").getJSONArray("hits");
        JSONArray articles = new JSONArray();
        JSONObject results = new JSONObject();
        int returned_results = hits.length();

        results.put("returned_results",returned_results);
        results.put("total_results", resbody.getJSONObject("hits").getJSONObject("total").getInt("value"));

        for(int i = 0; i < returned_results; i++) {
            JSONObject hit = hits.getJSONObject(i);
            JSONObject source = hit.getJSONObject("_source");
            articles.put(source);
        }

        results.put("articles", articles);
        request.close();
        response.responseBody().get().close();
        return Response.status(200).type("application/json").entity(results.toString(4))
                // below header is for CORS
                .header("Access-Control-Allow-Origin", "*").build();
    }

}
