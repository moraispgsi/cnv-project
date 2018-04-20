package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class WebServer {
    private static final int PORT = 8000;
    private static List<Instance> instances = new ArrayList<> ();

    public static void main(String[] args) throws Exception {
        // Autoscaler -> inicia uma instância baseada numa imagem!!!
        // autoscaler.initMachines(1);  // algo do genero
        System.out.println ("Init load balancer web server...");
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/mzrun.html", new MyHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // creates a non-limited Executor
        server.start();
        System.out.println ("ILoad balancer listening on port " + PORT);
    }

    static class MyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {


            //TODO Choose an available EC2 webserver
            //This includes connecting to the DynamoDB database and checking the metrics
            //this should not take too much time because if it does it will slow down the service
            //instead of augmenting the performance

            // change the url for one of the machines
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

        }

    }

}
