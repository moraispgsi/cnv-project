package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.sun.tools.internal.ws.resources.WebserviceapMessages;

import java.util.ArrayList;
import java.util.List;


public class InstanceInfo {

    private Instance awsInstance;

    private boolean isBooting;

    private boolean toBeRemoved = false;

    private List<RequestInfo> executingRequests = new ArrayList<> ();


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


    public List<RequestInfo> getExecutingRequests() {
        return executingRequests;
    }

    public void addRequest(RequestInfo requestInfo) {
        synchronized (this){
            if(getCPUSlots() > 0)
                WebServer.coresAvailable.decrementAndGet();
        }
        WebServer.requestsAvailable.decrementAndGet();
        executingRequests.add(requestInfo);

    }
    public void removeRequest(RequestInfo requestInfo) {
        executingRequests.remove(requestInfo);

        synchronized (this){
            if(getCPUSlots() > 0)
                WebServer.coresAvailable.addAndGet(1);
        }
        WebServer.requestsAvailable.addAndGet(1);
    }

    public boolean toBeRemoved() {
        return toBeRemoved;
    }
    public void remove() {
        toBeRemoved = true;
        WebServer.coresAvailable.addAndGet(-WebServer.numberOfCPUs+getCPUSlots());
        WebServer.requestsAvailable.addAndGet(-WebServer.requestsPerInstance+executingRequests.size());
    }

    public void restore() {
        toBeRemoved = false;
        WebServer.coresAvailable.addAndGet(WebServer.numberOfCPUs+getCPUSlots());
        WebServer.requestsAvailable.addAndGet(-WebServer.requestsPerInstance+executingRequests.size());
    }


    public String getId() {
        return awsInstance.getImageId();
    }

    public String getHostIp() {
        return awsInstance.getPublicIpAddress() + ":" + WebServer.PORT;
    }

    public int getComplexity() {
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




    public int getCPUSlots(){
        return WebServer.numberOfCPUs - executingRequests.size();
    }
}
