package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler.Autoscaler;


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

    private static Context context;
    private static Autoscaler autoscaler;
    private static Thread threadAutoscaler;

    public static void main(String[] args) throws Exception {

        context = new Context("ami-7edd6c03", "ec2InstanceKeyPair",
                "launch-wizard-2", "t2.micro");

        System.out.println ("Starting the autoscaler...");
        autoscaler = new Autoscaler(context); //The constructor prepares a min number of instances and blocks until they are running
        //start autoscaler
        threadAutoscaler = new Thread(autoscaler);
        threadAutoscaler.start();

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
            try {

                String serverURL;
                int index;
                synchronized (context.getInstanceList()) {



                    //Todo - use the list to find out which is instance is more available that is NOT queued for removal

                    //This includes connecting to the DynamoDB database and checking the metrics
                    //this should not take too much time because if it does it will slow down the service
                    //instead of augmenting the performance

                    //TODO - Change this to the index of the more available instance
                    index = 0;
                    InstanceInfo instanceInfo = context.getInstanceList().get(index);
                    instanceInfo.requestPending ++;
                    serverURL = instanceInfo.hostIp;
                    System.out.println (instanceInfo);

                    //todo - if no instance is available the response should be an error
                }

                //Forward request
                URL requestURL = new URL("http://" + serverURL + "/mzrun.html?"+t.getRequestURI().getQuery());
                System.out.println (requestURL);
                URLConnection yc = requestURL.openConnection();
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                yc.getInputStream()));

                StringBuilder response = new StringBuilder ();
                String inputLine;
                while ((inputLine = in.readLine()) != null)
                    response.append (inputLine).append ('\n');
                in.close();

                //Return response
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.toString ().getBytes(),0, response.toString ().getBytes().length);
                os.close();

                //The request was fulfilled
                synchronized (context.getInstanceList()) {
                    InstanceInfo instanceInfo = context.getInstanceList().get(index);
                    instanceInfo.requestPending --;
                }
            }catch(Exception e) {
                e.printStackTrace ();
            }
        }

    }

}
