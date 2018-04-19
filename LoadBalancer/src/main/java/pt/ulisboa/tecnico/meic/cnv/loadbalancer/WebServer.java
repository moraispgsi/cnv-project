package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.Executors;

public class WebServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/mzrun.html", new MyHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // creates a non-limited Executor
        server.start();
    }

    static class MyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {
            
            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(Regions.EU_WEST_3)
                    .build();
            DynamoDBMapper mapper = new DynamoDBMapper(client);
            DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
            try{
                List<TableInstances> result = mapper.scan(TableInstances.class, scanExpression);
                System.out.println(result.toString());

                for (TableInstances instance : result) {
                    System.out.println(instance.url);
                }

            } catch(Exception exception) {
                System.out.println(exception);
            }



            //TODO Choose an available EC2 webserver
            //This includes connecting to the DynamoDB database and checking the metrics
            //this should not take too much time because if it does it will slow down the service
            //instead of augmenting the performance

            String serverURL = "google.pt";

            //Forward request
            URL requestURL = new URL("http://" + serverURL + "/mzrun.html"+t.getRequestURI().getQuery());
            URLConnection yc = requestURL.openConnection();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            yc.getInputStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null)
                System.out.println(inputLine);
            in.close();

            //Return response
            t.sendResponseHeaders(200, inputLine.length());
            OutputStream os = t.getResponseBody();
            os.write(inputLine.getBytes(),0,inputLine.getBytes().length);
            os.close();
            //

        }

    }

}
