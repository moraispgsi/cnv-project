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

    /**
     * Check the real status of the instance.
     * If status == running, return isBooting = false
     * If status == terminated or stopped, dead instance detected. Remove from instanceList
     * @return
     */
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
                    if (instance.getInstanceId().equals(getId())) {
                        if (instance.getState().getName().equals("running")) { //not sure if it is done this way

                            awsInstance = instance;
                            isBooting = false;
                            WebServer.instancesBooting.decrementAndGet();
                            break;
                        } else if(instance.getState().getName().equals("terminated") || instance.getState().getName().equals("stopped")){
                            // dead instance, remove
                            WebServer.instancesBooting.decrementAndGet();

                            //remove it self from the instanceList
                            WebServer.getContext().getInstanceList().remove(this);
                        }
                    }
                }
            }

        }

        return isBooting;
    }

    /**
     * Call a /healthCheck to check for an alive response
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
                //do nothing
            }

        }

        return alive;
    }


    public void addRequest(RequestInfo requestInfo) {
        executingRequests.add(requestInfo);
        System.out.println("AfterAddRequest " + getId() + ": " + getComplexity());
    }
    public void removeRequest(RequestInfo requestInfo) {
        executingRequests.remove(requestInfo);
        System.out.println("AfterRemoveRemove " + getId() + ": " + getComplexity());
        scheduleThreadToRemove();
    }

    public boolean toBeRemoved() {
        return toBeRemoved;
    }
    public void remove() {
        toBeRemoved = true;
        scheduleThreadToRemove();
    }

    public void restore() {
        removalThread.interrupt();
        toBeRemoved = false;
        System.out.println("Restoring instance " + awsInstance.getInstanceId());
    }

    private void scheduleThreadToRemove() {
        synchronized (this){
            if(toBeRemoved && executingRequests.size() == 0){

                removalThread = new Thread(){
                    public void run(){
                        System.out.println("Instance " + getId() + " to be removed in " + WebServer.removeInstanceDelay + " seconds...");
                        try {
                            Thread.sleep(WebServer.removeInstanceDelay * 1000);
                            synchronized (AutoScaler.getToBeDeletedList()) {
                                AutoScaler.getToBeDeletedList().remove(this);
                                AutoScaler.removeEC2Instance(awsInstance.getInstanceId());
                            }

                        } catch (InterruptedException e){
                            System.out.println("RemovalThread of instance " + getId() + " was interrupted");
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
        double sum = 0;
        for(RequestInfo requestInfo: this.executingRequests) {
            sum += requestInfo.getEstimatedComplexity();
        }
        return sum;
    }

    @Override public String toString () {
        return "InstanceInfo{" + "id='" + getId() + '\'' + ", hostIp='" + getHostIp() + '\''
                + ", toBeRemoved=" + toBeRemoved + '}';
    }

}
