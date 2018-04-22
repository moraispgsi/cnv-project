package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler.AutoScaler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WebServer {
    public static final int PORT = 8000;
    private static final String CONFIG_FILE = "../resources/config/config.properties";


    public static int numberOfCPUs = 0;
    public static int requestsPerInstance = 0;
    public static int minInstancesFullyAvailable = 0;
    public static int maxInstances = 0;
    public static int minInstances = 0;

    public static AtomicInteger coresAvailable= new AtomicInteger(0);
    public static AtomicInteger instancesBooting = new AtomicInteger(0);
    public static AtomicInteger requestsAvailable = new AtomicInteger(0);

    public static Context getContext() {
        return context;
    }

    private static Context context;
    private static AutoScaler autoscaler;
    private static Thread threadAutoscaler;

    public static void main(String[] args) throws Exception {

        setShutdownHook(); //TODO

        loadConfigFile();


        context = new Context("ami-e8dc6d95", "ec2InstanceKeyPair",
                "launch-wizard-2", "t2.micro");

        System.out.println ("Starting the autoscaler...");
        autoscaler = new AutoScaler(context); //The constructor prepares a min number of instances and blocks until they are running
        //start autoscaler
        threadAutoscaler = new Thread(autoscaler);
        //threadAutoscaler.start();

        System.out.println ("Init load balancer web server...");
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/mzrun.html", new MyHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // creates a non-limited Executor
        server.start();
        System.out.println ("ILoad balancer listening on port " + PORT);


    }



    static class MyHandler implements HttpHandler {



        @Override
        public void handle(HttpExchange httpExchange) throws IOException {

/*
                getAndAssignAvailableSlot()
                slot not null?
                    new Thread autoScale
                    forwardRequest to slot
                    new thread( remove slot and autoscale
                    return
                else slot is null
                    isInstanceBooting?
                        sleep 2000 and continue
                    else no
                        is maxInstanceReached?
                            no -> autoScale and continue
                            yes ->
                                getLessComplexityInstance()
                                assignSlot()
*/


            RequestInfo requestInfo = new RequestInfo(httpExchange);

            InstanceInfo chosenCandidate = chooseSlot(requestInfo);

            autoscale();

            forwardRequest(requestInfo, chosenCandidate, httpExchange);

            chosenCandidate.removeRequest(requestInfo);

            autoscale();

        }






/*

                List<Metric> metrics = DynamoDB.getInstance().getMetrics();
                List<Metric> matches = new ArrayList<>();
                for (Metric metric : metrics) {
                    if (metric.match(requestInfo)) {
                        matches.add(metric);
                    }
                }

                System.out.println("Metrics " + metrics.size());

                System.out.println("Matches " + matches.size());

                if (matches.size() != 0) {
                    double sum = 0;
                    for (Metric metric : matches) {
                        double ratio = metric.calculateRatio();
                        sum += ratio;
                    }
                    double averageRatio = sum / matches.size();
                    requestInfo.estimatedComplexity = (int) (requestInfo.getEstimatedComplexity() * averageRatio);
                } else {
                    //default calculation
                    requestInfo.estimatedComplexity = requestInfo.getEstimatedComplexity();
                }

                InstanceInfo instanceInfoLessComplexity = null;
                int currentComplexity = 0;
                String serverURL;
                synchronized (context.getInstanceList()) {

                    List<InstanceInfo> availableInstanceInfoList = new ArrayList<>();

                    //Filter available instances
                    for (InstanceInfo instanceInfo : context.getInstanceList()) {
                        if (!instanceInfo.toBeRemoved()) {
                            availableInstanceInfoList.add(instanceInfo);
                        }
                    }

                    if (availableInstanceInfoList.size() == 0) {
                        throw new RuntimeException("No instance available to redirect the request to.");
                    }

                    for (InstanceInfo instanceInfo : availableInstanceInfoList) {
                        int sum = instanceInfo.getComplexity();
                        if (instanceInfoLessComplexity == null || currentComplexity > sum) {
                            instanceInfoLessComplexity = instanceInfo;
                            currentComplexity = sum;
                        }
                    }

                    serverURL = instanceInfoLessComplexity.getHostIp();
                    System.out.println(instanceInfoLessComplexity);

                    //Adding the current request information to the list of current requests of the instance.
                    instanceInfoLessComplexity.addRequest(requestInfo);
                }

                //Forward request
                URL requestURL = new URL("http://" + serverURL + "/mzrun.html?" + httpExchange.getRequestURI().getQuery());
                System.out.println(requestURL);
                URLConnection yc = requestURL.openConnection();
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                yc.getInputStream()));

                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null)
                    response.append(inputLine).append('\n');
                in.close();

                //Return response
                httpExchange.sendResponseHeaders(200, response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.toString().getBytes(), 0, response.toString().getBytes().length);
                os.close();

                //The request was fulfilled
                synchronized (context.getInstanceList()) {
                    instanceInfoLessComplexity.removeRequest(requestInfo);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }*/

        private void autoscale(){
            threadAutoscaler.start();
        }

        private InstanceInfo chooseSlot(RequestInfo requestInfo) {

            int maxCpuSlots = 1;
            int maxRequestsSlots = 1;
            int minComplexity = 0;
            InstanceInfo cpuCandidateInstanceInfo = null;
            InstanceInfo requestsCandidateInstanceInfo = null;
            InstanceInfo complexityCandidateInstanceInfo = null;
            InstanceInfo chosenCandidate;


            synchronized (getContext().getInstanceList()) {
                for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
                    synchronized (instanceInfo) { //TODO
                        if (!instanceInfo.isBooting() && !instanceInfo.toBeRemoved()) {

                            if (instanceInfo.getCPUSlots() >= maxCpuSlots
                                    && instanceInfo.getExecutingRequests().size() < requestsPerInstance) {
                                maxCpuSlots = instanceInfo.getCPUSlots();
                                cpuCandidateInstanceInfo = instanceInfo;

                            } else if (instanceInfo.getCPUSlots() == 0
                                    && requestsPerInstance - instanceInfo.getExecutingRequests().size() >= maxRequestsSlots) {
                                maxRequestsSlots = instanceInfo.getExecutingRequests().size();
                                requestsCandidateInstanceInfo = instanceInfo;

                            } else if (minComplexity == 0 || instanceInfo.getComplexity() <= minComplexity) {
                                minComplexity = instanceInfo.getComplexity();
                                complexityCandidateInstanceInfo = instanceInfo;
                            }


                        }
                    }
                }

                if (cpuCandidateInstanceInfo != null)
                    chosenCandidate = cpuCandidateInstanceInfo;
                else if (requestsCandidateInstanceInfo != null)
                    chosenCandidate = requestsCandidateInstanceInfo;
                else
                    chosenCandidate = complexityCandidateInstanceInfo;

            }

            chosenCandidate.addRequest(requestInfo);
            return chosenCandidate;
        }





        private static void forwardRequest(RequestInfo currentRequestInfo, InstanceInfo choosenCandidate, HttpExchange httpExchange) {
            try {
                URL requestURL = new URL("http://"
                        + choosenCandidate.getHostIp()
                        + "/mzrun.html?"
                        + httpExchange.getRequestURI().getQuery());

                System.out.println(requestURL);

                URLConnection urlConnection = requestURL.openConnection();

                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null)
                    response.append(inputLine).append('\n');
                in.close();

                //Return response
                httpExchange.sendResponseHeaders(200, response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.toString().getBytes(), 0, response.toString().getBytes().length);
                os.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private static void loadConfigFile() {

        Properties prop = new Properties();
        InputStream input = null;

        try {

            input = new FileInputStream(CONFIG_FILE);

            // load a properties file
            prop.load(input);

            numberOfCPUs = Integer.parseInt(prop.getProperty("numberOfCPUs"));
            requestsPerInstance = Integer.parseInt(prop.getProperty("numberOfCPUs"));
            minInstancesFullyAvailable = Integer.parseInt(prop.getProperty("numberOfCPUs"));
            maxInstances = Integer.parseInt(prop.getProperty("numberOfCPUs"));
            minInstances = Integer.parseInt(prop.getProperty("minInstances"));

        } catch (IOException ex) {
            throw new RuntimeException("Error loading config file.");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    //TODO
    private static void setShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                AutoScaler.removeAll();
                System.out.println("Shutting down instances... (not)");
            }
        });
    }

}
