package pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.Context;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.InstanceInfo;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.WebServer;

import java.util.*;


public class AutoScaler implements Runnable {

    private Context context;

    public Context getContext() {
        return context;
    }

    public static List<InstanceInfo> getToBeDeletedList() {
        return toBeDeletedList;
    }

    private static List<InstanceInfo> toBeDeletedList = new ArrayList<>();

    public AutoScaler(Context context) {
        this.context = context;

        //Create the minInstances
        for(int i = 0; i < WebServer.minInstances; i++) {
            addEC2Instance();
        }
    }

    @Override
    public void run() {

        checkPlusOne();
        checkMinusOne();
    }

    private void checkPlusOne(){
        System.out.println ("AutoScaler: checking plus one");

        synchronized (context.getInstanceList()) {
            int numAvailableInstances = getContext().getInstanceList().size() - getToBeDeletedList().size();
            if(numAvailableInstances < WebServer.maxInstances &&
                    getClusterComplexity() + WebServer.scaleUpThreshold > WebServer.thresholdComplexity * numAvailableInstances) {

                System.out.println("AutoScaler: +1");

                //try to restore a toBeRemoved instance

                if (getToBeDeletedList().size() > 1) {
                    System.out.println("AutoScaler: Reusing a scheduled to be removed instance. id: " + getToBeDeletedList().get(0).getId());
                    getToBeDeletedList().get(0).restore();
                    getToBeDeletedList().remove(0);
                    return;
                }

                addEC2Instance();
            }
        }

    }

    private void checkMinusOne(){
        System.out.println ("AutoScaler: checking minus one");

        synchronized (context.getInstanceList()) {
            int numInstances = getContext().getInstanceList().size();
            double numberInstancesMinusOne = context.getInstanceList().size() - 1;
            double clusterMinusOneMaxLoad = WebServer.thresholdComplexity * numberInstancesMinusOne;
            double clusterMinusOneThresholdLoad = clusterMinusOneMaxLoad * WebServer.scaleDownThreshold;

            if(numInstances > WebServer.minInstances &&
                    getClusterComplexity() < clusterMinusOneThresholdLoad) {
                System.out.println ("AutoScaler: -1");

                InstanceInfo instanceInfo = findMinComplexityInstance();
                instanceInfo.remove();
                getToBeDeletedList().add(instanceInfo);

            }
        }
    }


    private double getClusterComplexity() {

        double clusterComplexity = 0;
        for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
            if(!instanceInfo.toBeRemoved()){
                clusterComplexity += instanceInfo.getComplexity();
            }
        }
        return clusterComplexity;

    }



    /*while(true) {
        try {


            Thread.sleep(2000);

            synchronized (context.getInstanceList()) {
                List<InstanceInfo> availableInstanceInfoList = new ArrayList<>();

                //Filter available instances
                for(InstanceInfo instanceInfo : context.getInstanceList()) {
                    if(!instanceInfo.toBeRemoved()) {
                        availableInstanceInfoList.add(instanceInfo);
                    }
                }

                int clusterSize = availableInstanceInfoList.size();
                int clusterComplexity = 0;

                for(InstanceInfo instanceInfo : availableInstanceInfoList) {
                    clusterComplexity += instanceInfo.getComplexity();
                }

                //Decision
                if(clusterSize > 1 && clusterSize * MIN_LOAD_COMPLEXITY > clusterComplexity) {
                    //The cluster has too much nodes for its load
                    queueInstanceRemoval();
                } else if(clusterSize > 1 && clusterSize * MAX_LOAD_COMPLEXITY < clusterComplexity) {
                    //The cluster has too much load for its nodes
                    //TODO - check how many we have to create
                    addEC2Instance();
                }

            }

            synchronized (context.getInstanceList()) {
                List<InstanceInfo> instanceInfoList = context.getInstanceList();
                Iterator<InstanceInfo> iterator = instanceInfoList.iterator();
                while(iterator.hasNext()) {
                    InstanceInfo instanceInfo = iterator.next();
                    if(instanceInfo.toBeRemoved() &&
                            instanceInfo.getExecutingRequests().size() == 0) {

                        removeEC2Instance(context.getEc2(), instanceInfo.getId()); //remove from EC2
                        iterator.remove(); //Remove from the list
                    }
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
*/
    private InstanceInfo findMinComplexityInstance() {

        double minComplexity = 0;
        InstanceInfo complexityCandidateInstanceInfo = null;


        for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
            if (!instanceInfo.isRunning() && !instanceInfo.toBeRemoved()) {

                if (minComplexity == 0 || instanceInfo.getComplexity() <= minComplexity) {
                    minComplexity = instanceInfo.getComplexity();
                    complexityCandidateInstanceInfo = instanceInfo;
                }
            }
        }

        return complexityCandidateInstanceInfo;

    }




    public void queueInstanceRemoval() {
        synchronized (context.getInstanceList()) {
            int availableCount = 0;
            List<InstanceInfo> instanceInfoList = context.getInstanceList();
            for(InstanceInfo instanceInfo: instanceInfoList) {
                if(!instanceInfo.toBeRemoved()) {
                    availableCount ++;
                }
            }

            if(availableCount > WebServer.minInstances) {
                //We can choose a random instance because the load balancer will not redirect any more requests to the
                // instance, making its resource usage eventually 0, without disrupting its previously assign requests.
                Random random = new Random();
                int removeIndex = random.nextInt(instanceInfoList.size() + 1);
                instanceInfoList.get(removeIndex).remove();
            }
        }
    }


    private void addEC2Instance() {


        AmazonEC2 ec2 = getContext().getEc2();

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId(getContext().getEc2WebServerImage())
                .withInstanceType(getContext().getEc2WebServerInstanceType())
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(getContext().getEc2WebServerKeyPairName())
                .withSecurityGroups(getContext().getEc2WebServerSecurityGroup());

        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);

        List<Instance> newInstancesList = runInstancesResult.getReservation().getInstances();

        InstanceInfo instanceInfo = new InstanceInfo(newInstancesList.get(0));

        synchronized (getContext().getInstanceList()) {
            getContext().addInstance(instanceInfo);
        }
        System.out.println ("AutoScaler: added new ec2 instance with id: " + instanceInfo.getId());
    }


/*

        List<String> newInstanceIdList = new ArrayList<>();

        for(Instance i : newInstancesList){
            newInstanceIdList.add(i.getInstanceId());
        }



        //TODO - Test the instance's health by sending a ping.

        System.out.println ("Waiting for instance " + newInstanceId + "  to be available...");

        //While true :/
        while(true) {
            Thread.sleep(2000);
            DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
            describeInstancesRequest.setInstanceIds(instanceIdsList); //Describe this instances
            DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
            List<Reservation> reservations  = describeInstancesResult.getReservations();

            for (Reservation reservation : reservations){
                List <Instance> instanceList = reservation.getInstances();
                for (Instance instance : instanceList){
                    //if our instance is running
                    if(instance.getInstanceId().equals(newInstanceId) &&
                            instance.getState().getName().equals("running")) { //not sure if it is done this way
                        System.out.println ("Instance " + instance.getInstanceId () + " is now running...");
                        return instance;
                    }
                }
            }
        }*/



    //Removes an EC2 instance by ID
    public static void removeEC2Instance(String instanceId) {
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        WebServer.getContext().getEc2().terminateInstances(termInstanceReq);
        System.out.println ("Removed instance with id, " + instanceId + " from aws.");
    }

    public static void removeAll(){
        System.out.println ("Trying to remove all instances from aws");
        for(InstanceInfo instanceInfo : WebServer.getContext().getInstanceList()){
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(instanceInfo.getId());
            WebServer.getContext().getEc2().terminateInstances(termInstanceReq);
        }
    }

}
