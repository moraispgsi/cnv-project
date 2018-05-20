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
    public static final int PORT = 80;
    private static final String CONFIG_FILE = "../resources/config/config.properties";

    public static int maxInstances = 0;
    public static int minInstances = 0;
    public static int thresholdComplexity = 0;
    public static int scaleUpThreshold = 0;
    public static int scaleDownThreshold = 0;
    public static int minMetricSample = 0;


    public static AtomicInteger instancesBooting = new AtomicInteger(0);

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
                if (metric.match(requestInfo)) {
                    matches.add(metric);
                }

                //Find the max complexity
                if(complexity > maxComplexity) {
                    maxComplexity = complexity;
                }

            }

            System.out.println("Metrics " + metrics.size());

            System.out.println("Matches " + matches.size());

            if (matches.size() > minMetricSample) {
                double sum = 0;

                for (Metric metric : matches) {
                    sum += metric.computeComplexity();
                }

                double average = sum / matches.size();
                double normalizedAverage = average * 10 / maxComplexity;

                requestInfo.setEstimatedComplexity(normalizedAverage);
                return normalizedAverage;
            } else {
                //default calculation
                return requestInfo.getEstimatedComplexity();
            }
        }


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

            double minComplexity = 0;


            double newComplexity = getComplexity(requestInfo);
            InstanceInfo chosenCandidate = null;

            while(chosenCandidate == null) {
                boolean notReadyInstanceDetected = false;

                synchronized (getContext().getInstanceList()) {

                    //pick the instance with the minimum complexity
                    for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
                        synchronized (instanceInfo) {

                            // check if booting and not to be removed


                            //Do healthcheck
                            if (instanceInfo.isRunning() && !instanceInfo.toBeRemoved()) {

                                //check if adding this request doesn't pass the threshold
                                if (instanceInfo.getComplexity() + newComplexity <= thresholdComplexity &&
                                        (minComplexity == 0 || instanceInfo.getComplexity() <= minComplexity)) {

                                    minComplexity = instanceInfo.getComplexity();
                                    chosenCandidate = instanceInfo;
                                }


                            } else {
                                notReadyInstanceDetected = true;
                            }
                        }
                    }

                    if(chosenCandidate != null) {
                        chosenCandidate.addRequest(requestInfo);
                        System.out.println("Chosen candidate: " + chosenCandidate.getId());
                        break;
                    }
                }

                // detected a still booting or a to be removed instance
                if(notReadyInstanceDetected) {
                    //auto scale and try again
                    autoscale();
                }

                try {
                    System.out.println("Sleeping 5 seconds...");
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.getStackTrace();
                }
            }

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

            maxInstances = Integer.parseInt(prop.getProperty("numberOfCPUs"));
            minInstances = Integer.parseInt(prop.getProperty("minInstances"));
            thresholdComplexity = Integer.parseInt(prop.getProperty("thresholdComplexity"));
            scaleUpThreshold = Integer.parseInt(prop.getProperty("scaleUpThreshold"));
            scaleDownThreshold = Integer.parseInt(prop.getProperty("scaleDownThreshold"));
            minMetricSample = Integer.parseInt(prop.getProperty("minMetricSample"));

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
