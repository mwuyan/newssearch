package edu.northwestern.ssa;

import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.*;
import java.time.Duration;
import java.net.SocketTimeoutException;

/*
* Code help from:
* https://stackoverflow.com/questions/309424/how-do-i-read-convert-an-inputstream-into-a-string-in-java
* https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-objects.html
* https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-buckets.html
* https://stackoverflow.com/questions/20117148/how-to-create-json-object-using-string
* https://stackoverflow.com/questions/8027265/how-to-list-all-aws-s3-objects-in-a-bucket-using-java
* */

public class App {
    public static void main(String[] args) throws IOException {

        System.out.println("Hello world!");
        downloadWarc();
        parseWarc();
        File warc = new File("crawldata.warc");
        warc.delete();
    }

    public static void downloadWarc() {
        Region region = Region.US_EAST_1;
        Duration duration = Duration.ofMinutes(30);
        S3Client s3 = S3Client.builder()
                .region(region)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(duration).build())
                .build();

        String bucketName = "commoncrawl";
        String key = System.getenv("COMMON_CRAWL_FILENAME");

        if (System.getenv("COMMON_CRAWL_FILENAME") == null || System.getenv("COMMON_CRAWL_FILENAME").equals("")) {
            key = latestWarc(s3,"commoncrawl");

        }

        checkWarc(key);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        File f = new File("crawldata.warc");
        //System.out.println("filed");
        s3.getObject(getObjectRequest, ResponseTransformer.toFile(f));
        s3.close();
    }

    public static void checkWarc(String key)  {
        File lastwarc = new File("lastwarc.txt");
        if(lastwarc.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(lastwarc));
                String filename = br.readLine();
                if(filename.equals(key)) {
                    System.out.println("Latest Warc has already been uploaded");
                    System.exit(0);
                } else {
                    FileWriter fw = new FileWriter(lastwarc,false);
                    fw.write(key);
                    fw.close();
                    System.out.println("Overwrote file with text inside: " + key);
                }
            } catch (IOException e) {
                System.out.println("Error occurred when opening file");
            }

        } else {
            try {
                lastwarc.createNewFile();
                FileWriter fw = new FileWriter(lastwarc.getAbsoluteFile());
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(key);
                bw.close();
                System.out.println("Created file with text inside: " + key);
            } catch (IOException e) {
                System.out.println("Error occured when creating file");
            }
        }
        return;
    }


    public static void parseWarc() throws IOException {
        File f = new File("crawldata.warc");
        ElasticSearch search = new ElasticSearch("es");
        //search.deleteIndex();
        search.createIndex();
        ArchiveReader archiveReader = WARCReaderFactory.get(f);
        //System.out.println("parsing");
        for (ArchiveRecord record : archiveReader) {
            if (record.getHeader().getHeaderValue("WARC-Type").equals("response")) {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                while(record.available() > 0) {
                    int length = record.read(buffer);
                    result.write(buffer,0, length);
                }

                String converted = result.toString("UTF-8");
                converted = converted.substring(converted.indexOf("\r\n\r\n")+4);
                converted = converted.replace("\0","");
                JSONObject post = parseHTML(converted,record.getHeader().getUrl(),
                        record.getHeader().getDate().substring(0,10));

                try {
                    search.postDocument(post);
                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                    retryPost(search, post);
                }
            }
        }
        search.close();
    }

    public static JSONObject parseHTML(String html, String url, String date) {
        JSONObject post = new JSONObject();
        try {
            Document doc = Jsoup.parse(html);
            post.put("title",doc.title());
            post.put("txt",doc.text());
            post.put("url",url);
            post.put("date", date);
            Element tag = doc.select("html").first();
            post.put("lang",tag.attr("lang"));
        } catch (Exception e) {
            System.out.println(e);
            post.put("title"," ");
            post.put("txt"," ");
            post.put("url", url);
            post.put("date","");
            post.put("lang","");
        }
        return post;
    }

    public static String latestWarc(S3Client s3, String bucketName) {

        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName)
                .prefix("crawl-data/CC-NEWS/").build();
        ListObjectsV2Iterable response = s3.listObjectsV2Paginator(request);

        String latest = "";

        for (ListObjectsV2Response page : response) {
            for (S3Object object : page.contents()) {
                latest = object.key();
            }
        }
        return latest;
    }

    public static void retryPost(ElasticSearch search, JSONObject post) { //try to repost twice
        try {
            search.wait(1000);
            search.postDocument(post);
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                search.wait(1000);
                search.postDocument(post);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("failed to repost");
            }
        }

    }

}


