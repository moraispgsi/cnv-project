package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.exceptions.DeadInstanceException;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.threads.SolveMazeThread;

import java.io.*;
import java.net.InetSocketAddress;
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


        context = new Context("ami-cd7bcab0", "ec2InstanceKeyPair",
                "launch-wizard-2", "t2.micro");

        System.out.println ("Starting the autoscaler...");
        autoscaler = new AutoScaler(context); //The constructor prepares a min number of instances and blocks until they are running
        threadAutoscaler = new Thread(autoscaler);

        System.out.println ("Starting load balancer web server...");
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/mzrun.html", new MyHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // creates a non-limited Executor
        server.start();
        System.out.println ("Load balancer listening on port " + PORT);
    }



    static class MyHandler implements HttpHandler {


        private static final int MAX_RETRIES = 30;

        @Override
        public void handle(HttpExchange httpExchange)  {

            RequestInfo requestInfo = new RequestInfo(httpExchange);

            // autoScale at start
            autoscale();


            int retryCount = 0;
            while(retryCount < MAX_RETRIES){

                // loadBalancer
                InstanceInfo chosenCandidate = loadBalancerPicker(requestInfo);

                // forward and solve request
                try {
                    forwardRequest(chosenCandidate, httpExchange);

                    chosenCandidate.removeRequest(requestInfo);
                    break;
                } catch (DeadInstanceException e) {
                    System.out.println("Failed to solve request on instance " + chosenCandidate.getId() + ".");

                    //remove request from dead isntance
                    chosenCandidate.removeRequest(requestInfo);

                    //remove dead instance
                    getContext().getInstanceList().remove(chosenCandidate);

                    retryCount++;
                    System.out.println("Retry number: " + retryCount);
                }
            }

            //autoScale at exit
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
            threadAutoscaler = new Thread(autoscaler);
        }

        /**
         * Pick the best instance to run the following request.
         * If necessary, autoScale and hold the request until a slot is available
         *
         * @param requestInfo
         * @return the instance where the requestInfo is going to be ran
         */
        private InstanceInfo loadBalancerPicker(RequestInfo requestInfo) {

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
                            if (instanceInfo.isRunning() && !instanceInfo.toBeRemoved()) {

                                //TODO insert better logic

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

                    if (cpuCandidateInstanceInfo != null)
                        chosenCandidate = cpuCandidateInstanceInfo;
                    else if (requestsCandidateInstanceInfo != null)
                        chosenCandidate = requestsCandidateInstanceInfo;
                    else
                        chosenCandidate = complexityCandidateInstanceInfo;

                }
                if(chosenCandidate == null) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            chosenCandidate.addRequest(requestInfo);
            System.out.println ("Choose candidate with id: " + chosenCandidate.getId());
            return chosenCandidate;
        }


        private void forwardRequest(InstanceInfo chosenCandidate, HttpExchange httpExchange) throws DeadInstanceException {

            System.out.println("Forward requesting to: " + chosenCandidate.getId());

            Thread solveMazeThread = new SolveMazeThread(chosenCandidate, httpExchange);
            solveMazeThread.start();

            while(solveMazeThread.isAlive()){
                if(!chosenCandidate.isRunning()) {
                    solveMazeThread.interrupt();
                    solveMazeThread.stop(); //trying to kill the thread...
                    System.out.println("Dead instance: " + chosenCandidate.getId());
                    throw new DeadInstanceException("Instance " + chosenCandidate.getId() + " died");
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    System.out.println("MonitorConnectionThread for instance " + chosenCandidate.getId() + " was interrupted.");
                }
            }

            System.out.println(Thread.currentThread().getId() + ": Maze solved.");
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

    private static void setShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.err.println("Shutting down instances... (not)");
                AutoScaler.removeAll();
            }
        });
    }

}
