package edu.northwestern.ssa;

import org.json.JSONObject;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.io.IOException;


public class ElasticSearch extends AwsSignedRestRequest {
    /**
     * @param serviceName would be "es" for Elasticsearch
     */
    ElasticSearch(String serviceName) {
        super(serviceName);
    }


    public void createIndex() throws IOException {
        HttpExecuteResponse response = this.restRequest(SdkHttpMethod.PUT,System.getenv("ELASTIC_SEARCH_HOST"),
                System.getenv("ELASTIC_SEARCH_INDEX"),java.util.Optional.empty());
        response.responseBody().get().close();


    }

    public void postDocument(JSONObject body) throws IOException {
        HttpExecuteResponse response = this.restRequest(SdkHttpMethod.POST,System.getenv("ELASTIC_SEARCH_HOST"),
                System.getenv("ELASTIC_SEARCH_INDEX")+"/_doc/",java.util.Optional.empty(),
                java.util.Optional.of(body));
        response.responseBody().get().close();
    }

    public void deleteIndex() throws IOException {
        this.restRequest(SdkHttpMethod.DELETE,System.getenv("ELASTIC_SEARCH_HOST"),
                "my-index",java.util.Optional.empty());
    }

}
