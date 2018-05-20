package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler.AutoScaler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;


public class InstanceInfo {

    private Instance awsInstance;

    private boolean isBooting;

    private boolean toBeRemoved = false;

    private List<RequestInfo> executingRequests = new ArrayList<> ();

    private Thread removalThread;


    public InstanceInfo(Instance instance){
        this.awsInstance = instance;
        this.isBooting = true;
        WebServer.instancesBooting.addAndGet(1);
    }

    public boolean isBooting() {
        if (isBooting) {
            DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();

            List<String> instanceIdList = new ArrayList<>();
            instanceIdList.add(getId());

            describeInstancesRequest.setInstanceIds(instanceIdList);

            DescribeInstancesResult describeInstancesResult = WebServer.getContext().getEc2()
                    .describeInstances(describeInstancesRequest);
            List<Reservation> reservations = describeInstancesResult.getReservations();

            for (Reservation reservation : reservations) {
                List<Instance> instanceList = reservation.getInstances();
                for (Instance instance : instanceList) {
                    //if our instance is running
                    if (instance.getInstanceId().equals(getId()) &&
                            instance.getState().getName().equals("running")) { //not sure if it is done this way
                        System.out.println("Instance " + instance.getInstanceId() + " is now running...");
                        awsInstance = instance;

                        isBooting = false;
                        WebServer.instancesBooting.decrementAndGet();
                        WebServer.coresAvailable.addAndGet(WebServer.numberOfCPUs);
                        WebServer.requestsAvailable.addAndGet(WebServer.requestsPerInstance);
                        break;
                    }
                }
            }

        }

        return isBooting;
    }

    /**
     * Check if is booting, updating it's info when it's not.
     * Call a /healthCheck to check for alive response
     *
     * @return true if is running
     */
    public boolean isRunning() {

        boolean alive = false;

        if(!isBooting) {

            URL requestURL;

            try {
                requestURL = new URL("http://"
                        + getHostIp()
                        + "/healthCheck");


                URLConnection urlConnection = requestURL.openConnection();
                urlConnection.setReadTimeout(5000);
                urlConnection.setConnectTimeout(5000);

                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

                if (in.readLine().equals("alive")) {
                    alive = true;
                }

            } catch (IOException e) {
                System.err.println("/healthCheck failed: " + getId());
            }

        }

        return alive;
    }


    public List<RequestInfo> getExecutingRequests() {
        return executingRequests;
    }

    public void addRequest(RequestInfo requestInfo) {
        synchronized (this){
            if(getCpuFreeSlots() > 0)
                WebServer.coresAvailable.decrementAndGet();
        }
        WebServer.requestsAvailable.decrementAndGet();
        executingRequests.add(requestInfo);

    }
    public void removeRequest(RequestInfo requestInfo) {
        System.out.println("Removing request");
        executingRequests.remove(requestInfo);

        if(!toBeRemoved) {
            synchronized (this) {
                if (getCpuFreeSlots() > 0)
                    WebServer.coresAvailable.addAndGet(1);
            }
            WebServer.requestsAvailable.addAndGet(1);
        }
        scheduleThreadToRemove();

    }

    public boolean toBeRemoved() {
        return toBeRemoved;
    }
    public void remove() {
        toBeRemoved = true;
        WebServer.coresAvailable.addAndGet(-WebServer.numberOfCPUs+getCpuOccupiedSlots());
        WebServer.requestsAvailable.addAndGet(-WebServer.requestsPerInstance+executingRequests.size());

        scheduleThreadToRemove();
    }

    public void restore() {
        removalThread.interrupt();
        toBeRemoved = false;
        System.out.println("Restoring instance " + awsInstance.getInstanceId());
        WebServer.coresAvailable.addAndGet(WebServer.numberOfCPUs - getCpuOccupiedSlots());
        WebServer.requestsAvailable.addAndGet(WebServer.requestsPerInstance-executingRequests.size());
    }

    private void scheduleThreadToRemove() {
        synchronized (this){
            if(toBeRemoved && executingRequests.size() == 0){

                removalThread = new Thread(){
                    public void run(){
                        System.out.println("Scheduling id " + awsInstance.getInstanceId() + " is running. Sleeping 30 seconds...");
                        try {
                            Thread.sleep(30000); //TODO
                            synchronized (AutoScaler.getToBeDeletedList()) {
                                AutoScaler.getToBeDeletedList().remove(this);
                                AutoScaler.removeEC2Instance(awsInstance.getInstanceId());
                            }

                        } catch (InterruptedException e){
                            System.out.println("RemovalThread of id" + awsInstance.getInstanceId() + " was interrupted");
                        }

                    }
                };
                removalThread.start();
            }
        }
    }


    public String getId() {
        return awsInstance.getInstanceId();
    }

    public String getHostIp() {
        return awsInstance.getPublicIpAddress() + ":" + WebServer.PORT;
    }

    public double getComplexity() {
        int sum = 0;
        for(RequestInfo requestInfo: this.executingRequests) {
            sum += requestInfo.getEstimatedComplexity();
        }
        return sum;
    }

    @Override public String toString () {
        return "InstanceInfo{" + "id='" + getId() + '\'' + ", hostIp='" + getHostIp() + '\''
                + ", toBeRemoved=" + toBeRemoved + '}';
    }




    public int getCpuFreeSlots(){
        return Math.max(0, WebServer.numberOfCPUs - executingRequests.size());
    }

    public int getCpuOccupiedSlots(){
        return WebServer.numberOfCPUs-getCpuFreeSlots();
    }
}
