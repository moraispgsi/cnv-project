package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler.AutoScaler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
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

        setShutdownHook();

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


            RequestInfo requestInfo = new RequestInfo(httpExchange);

            InstanceInfo chosenCandidate = chooseSlot(requestInfo);

            autoscale();

            forwardRequest(chosenCandidate, httpExchange);

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


                }


        }*/

        private void autoscale(){
            threadAutoscaler.start();
            threadAutoscaler = new Thread(autoscaler);
        }

        private InstanceInfo chooseSlot(RequestInfo requestInfo) {

            int maxCpuSlots = 1;
            int maxRequestsSlots = 1;
            int minComplexity = 0;
            InstanceInfo cpuCandidateInstanceInfo = null;
            InstanceInfo requestsCandidateInstanceInfo = null;
            InstanceInfo complexityCandidateInstanceInfo = null;
            InstanceInfo chosenCandidate = null;


            while(chosenCandidate == null) {
                synchronized (getContext().getInstanceList()) {
                    for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
                        synchronized (instanceInfo) {
                            if (!instanceInfo.isBooting() && !instanceInfo.toBeRemoved()) {

                                if (instanceInfo.getCpuFreeSlots() >= maxCpuSlots
                                        && instanceInfo.getExecutingRequests().size() < requestsPerInstance) {
                                    maxCpuSlots = instanceInfo.getCpuFreeSlots();
                                    cpuCandidateInstanceInfo = instanceInfo;

                                } else if (instanceInfo.getCpuFreeSlots() == 0
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

                    if (cpuCandidateInstanceInfo != null) {
                        chosenCandidate = cpuCandidateInstanceInfo;
                        System.out.println("Chose CpuCandidate " + chosenCandidate.getId());
                    }
                    else if (requestsCandidateInstanceInfo != null) {
                        chosenCandidate = requestsCandidateInstanceInfo;
                        System.out.println("Chose RequestCandidate " + chosenCandidate.getId());
                    }
                    else {
                        chosenCandidate = complexityCandidateInstanceInfo;
                        System.out.println("Chose ComplexityCandidate " + chosenCandidate.getId());
                    }

                }
                if(chosenCandidate == null) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            chosenCandidate.addRequest(requestInfo);
            return chosenCandidate;
        }





        private void forwardRequest(InstanceInfo chosenCandidate, HttpExchange httpExchange) {
            try {
                URL requestURL = new URL("http://"
                        + chosenCandidate.getHostIp()
                        + "/mzrun.html?"
                        + httpExchange.getRequestURI().getQuery());

                waitForWebServerToStart(chosenCandidate);

                System.out.println("Forward to: " + requestURL);

                URLConnection urlConnection = requestURL.openConnection();
                urlConnection.setReadTimeout(0);
                urlConnection.setConnectTimeout(0);



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

                System.out.println(Thread.currentThread().getId() + ": Maze solved.");

            } catch (IOException e) {
                String response = "Thread with id: "+ Thread.currentThread().getId() + " > failed during forward request.";
                System.out.println(response);
                try {
                    httpExchange.sendResponseHeaders(500, response.length());
                    OutputStream os = httpExchange.getResponseBody();
                    os.write(response.toString().getBytes(), 0, response.toString().getBytes().length);
                    os.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                e.printStackTrace();
            }

        }

        private void waitForWebServerToStart(InstanceInfo chosenCandidate) throws MalformedURLException {

            URL healthCheckURL = new URL("http://"
                    + chosenCandidate.getHostIp()
                    + "/healthCheck");

            System.out.println("HealthCheck");
            while(true) {
                try {
                    URLConnection urlConnection = healthCheckURL.openConnection();
                    urlConnection.getInputStream();
                    break;
                } catch (IOException e) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    System.out.println("...");
                }
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
            requestsPerInstance = Integer.parseInt(prop.getProperty("requestsPerInstance"));
            minInstancesFullyAvailable = Integer.parseInt(prop.getProperty("minInstancesFullyAvailable"));
            maxInstances = Integer.parseInt(prop.getProperty("maxInstances"));
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

    private static void setShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.err.println("Shutting down instances... (not)");
                AutoScaler.removeAll();
            }
        });
    }

}
