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

    public static int maxInstances;
    public static int minInstances;
    public static double instanceCapacity;
    public static double minAvailableComplexityPower;
    public static double maxAvailableComplexityPower;
    public static int minMetricSample;
    public static int removeInstanceDelay;


    public static AtomicInteger instancesBooting = new AtomicInteger(0);

    public static Context getContext() {
        return context;
    }

    private static Context context;
    private static AutoScaler autoscaler;
    private static Thread threadAutoscaler;

    public static void main(String[] args) throws Exception {

        loadConfigFile();


        context = new Context("ami-cd7bcab0", "ec2InstanceKeyPair",
                "launch-wizard-2", "t2.micro");

        autoscaler = new AutoScaler(context); //The constructor prepares a min number of instances and blocks until they are running

        setShutdownHook();

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
                    System.out.println(e.getMessage());
                    retryCount++;

                    //remove dead instance
                    getContext().getInstanceList().remove(chosenCandidate);
                    AutoScaler.getToBeDeletedList().remove(chosenCandidate);

                    //dead instance detected, it's wise to run the autoscaler
                    autoscale();
                }
            }

            //autoScale at exit
            autoscale();

        }

        /**
         * Starts a new AutoScaler Thread
         */
        private synchronized void autoscale(){
            threadAutoscaler = new Thread(autoscaler);
            threadAutoscaler.start();
        }


        /**
         * Pick the best instance to run the following request.
         * If necessary, autoScale and hold the request until a slot is available
         *
         * @param requestInfo
         * @return the instance where the requestInfo is going to be ran
         */
        private InstanceInfo loadBalancerPicker(RequestInfo requestInfo) {

            double minComplexity = 11; //outside of scope, to force the next number to be set to minimum
            InstanceInfo chosenCandidate = null;


            double newComplexity = getComplexity(requestInfo);


            while(chosenCandidate == null) {
                InstanceInfo toBeRemovedInstanceDetected = null;

                synchronized (getContext().getInstanceList()) {

                    //pick the instance with the minimum complexity
                    for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
                        synchronized (instanceInfo) {

                            // perform a healthCheck
                            if (instanceInfo.isRunning()) {

                                if (!instanceInfo.toBeRemoved()) {

                                    //check if adding this request doesn't pass the threshold
                                    if (instanceInfo.getComplexity() + newComplexity <= instanceCapacity &&
                                            instanceInfo.getComplexity() < minComplexity) {

                                        minComplexity = instanceInfo.getComplexity();
                                        chosenCandidate = instanceInfo;
                                    }

                                } else {
                                    toBeRemovedInstanceDetected = instanceInfo;
                                }
                            } else {
                                if(instanceInfo.isBooting()){
                                    System.out.println(instanceInfo.getId() + " still booting...");
                                }
                            }
                        }
                    }

                    if(chosenCandidate != null) {
                        chosenCandidate.addRequest(requestInfo);
                        break;
                    }
                }

                // non instance chosen, but a toBeremoved is available. restore
                if(toBeRemovedInstanceDetected != null) {
                    toBeRemovedInstanceDetected.restore();
                    continue;
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.getStackTrace();
                }
            }

            return chosenCandidate;

        }

        /**
         * Receives a requestinfo, then it tries to find matching metrics that are similar enough to the request.
         * If a minimum sample of metrics match the given requestInfo parameters, the sample is used to calculate the
         * complexity of the request. If the metrics are used, the complexity is the average of the matched metrics complexity
         * and its normalized to a scale between 0 and 10 using the max complexity taken from all the metrics.
         *
         * @param requestInfo the request info to evaluate the complexity
         * @return the calculated complexity
         */
        public double getComplexity(RequestInfo requestInfo) {
            List<Metric> metrics = DynamoDB.getInstance().getMetrics();
            List<Metric> matches = new ArrayList<>();
            Double maxComplexity = 0.0;

            for (Metric metric : metrics) {

                Double complexity = (double) metric.computeComplexity();
                if(metric.getCompleted()) {
                    if (metric.match(requestInfo)) {
                        matches.add(metric);
                    }

                    //Find the max complexity with the same strategy
                    if (metric.getStrategy().equals(requestInfo.getStrategy())
                            && complexity > maxComplexity) {
                        maxComplexity = complexity;
                    }
                }

            }

            System.out.println("Found " + matches.size() +" / " + metrics.size() + " matches");

            if (matches.size() >= minMetricSample) {
                double sum = 0;

                for (Metric metric : matches) {
                    sum += metric.computeComplexity();
                }

                double average = sum / matches.size();
                double normalizedAverage = average * 10 / maxComplexity;

                requestInfo.setEstimatedComplexity(normalizedAverage);
                System.out.println("DynamoDB-Assist complexity: " + normalizedAverage);
                return normalizedAverage;
            } else {
                //default calculation
                System.out.println("Default complexity: " + requestInfo.getEstimatedComplexity());
                return requestInfo.getEstimatedComplexity();
            }
        }


        private void forwardRequest(InstanceInfo chosenCandidate, HttpExchange httpExchange) throws DeadInstanceException {

            System.out.println("Forward requesting to: " + chosenCandidate.getId());

            Thread solveMazeThread = new SolveMazeThread(chosenCandidate, httpExchange);
            solveMazeThread.start();

            while(solveMazeThread.isAlive()){
                if(!chosenCandidate.isRunning()) {
                    solveMazeThread.interrupt();
                    solveMazeThread.stop(); //trying to kill the thread...
                    throw new DeadInstanceException("DEAD instance detected: " + chosenCandidate.getId());
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    System.out.println("MonitorConnectionThread for instance " + chosenCandidate.getId() + " was interrupted.");
                }
            }

            System.out.println(Thread.currentThread().getId() + ": MazeSolver responded.");
        }


    }


    private static void loadConfigFile() {
        System.out.println("Loading config file");

        Properties prop = new Properties();
        InputStream input = null;

        try {

            input = new FileInputStream(CONFIG_FILE);

            // load a properties file
            prop.load(input);

            minInstances = Integer.parseInt(prop.getProperty("minInstances"));
            maxInstances = Integer.parseInt(prop.getProperty("maxInstances"));
            instanceCapacity = Double.parseDouble(prop.getProperty("instanceCapacity"));
            minAvailableComplexityPower = Double.parseDouble(prop.getProperty("minAvailableComplexityPower"));
            maxAvailableComplexityPower = Double.parseDouble(prop.getProperty("maxAvailableComplexityPower"));
            minMetricSample = Integer.parseInt(prop.getProperty("minMetricSample"));
            removeInstanceDelay = Integer.parseInt(prop.getProperty("removeInstanceDelay"));

            //Check value ranges
            if(minInstances > maxInstances)
                throw new RuntimeException("minInstance must be bigger than maxInstance");
            if(instanceCapacity < 0 || instanceCapacity > 10)
                throw new RuntimeException("instanceCapacity must be between 0 and 10");
            if(minAvailableComplexityPower < 0 || minAvailableComplexityPower > 10)
                throw new RuntimeException("minAvailableComplexityPower must be between 0 and 10");
            if(maxAvailableComplexityPower < 0 || maxAvailableComplexityPower > 10 - minAvailableComplexityPower)
                throw new RuntimeException("maxAvailableComplexityPower must be between 0 and (10-minAvailableComplexityPower)");



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
                System.err.println("Shutting down instances...");
                AutoScaler.removeAll();
            }
        });
    }

}
