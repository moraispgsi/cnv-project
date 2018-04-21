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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

                Map<String, String> params = queryToMap (t.getRequestURI ().getQuery ());

                int initX = Integer.parseInt (params.get ("x0")); //Point A
                int initY = Integer.parseInt (params.get ("y0")); //Point A
                int finalX = Integer.parseInt (params.get ("x1")); //Point B
                int finalY = Integer.parseInt (params.get ("y1")); //Point B
                int velocity = Integer.parseInt (params.get ("v")); //Velocity
                String strategy = params.get ("s"); //Strategy
                String maze = params.get ("m"); //Maze


                int estimatedComplexity = 5000;
                List<Metric> metrics = DynamoDB.getInstance().getMetrics();
                //TODO - calculate the complexity for the request







                RequestInfo currentRequestInfo;
                InstanceInfo instanceInfoLessComplexity = null;
                int currentComplexity = 0;
                String serverURL;
                synchronized (context.getInstanceList()) {

                    List<InstanceInfo> availableInstanceInfoList = new ArrayList<>();

                    //Filter available instances
                    for(InstanceInfo instanceInfo : context.getInstanceList()) {
                        if(!instanceInfo.queueRemove) {
                            availableInstanceInfoList.add(instanceInfo);
                        }
                    }

                    if(availableInstanceInfoList.size() == 0) {
                        throw new RuntimeException("No instance available to redirect the request to.");
                    }

                    for(InstanceInfo instanceInfo : availableInstanceInfoList) {
                        int sum = instanceInfo.getComplexity();
                        if(instanceInfoLessComplexity == null || currentComplexity > sum) {
                            instanceInfoLessComplexity = instanceInfo;
                            currentComplexity = sum;
                        }
                    }

                    instanceInfoLessComplexity.requestPending ++;
                    serverURL = instanceInfoLessComplexity.hostIp;
                    System.out.println (instanceInfoLessComplexity);

                    //Adding the current request information to the list of current requests of the instance.
                    currentRequestInfo = new RequestInfo();
                    currentRequestInfo.initX = initX;
                    currentRequestInfo.initY = initY;
                    currentRequestInfo.finalX = finalX;
                    currentRequestInfo.finalY = finalY;
                    currentRequestInfo.maze = maze;
                    currentRequestInfo.velocity = velocity;
                    currentRequestInfo.strategy = strategy;
                    currentRequestInfo.estimatedComplexity = estimatedComplexity;
                    instanceInfoLessComplexity.currentRequests.add(currentRequestInfo);
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
                    instanceInfoLessComplexity.requestPending --;
                    instanceInfoLessComplexity.currentRequests.remove(currentRequestInfo);
                }
            }catch(Exception e) {
                e.printStackTrace ();
            }
        }

        private Map<String, String> queryToMap (String query) {
            Map<String, String> result = new HashMap<String, String>();
            for (String param : query.split ("&")) {
                String pair[] = param.split ("=");
                if (pair.length > 1) {
                    result.put (pair[0], pair[1]);
                } else {
                    result.put (pair[0], "");
                }
            }
            return result;
        }

    }

}
