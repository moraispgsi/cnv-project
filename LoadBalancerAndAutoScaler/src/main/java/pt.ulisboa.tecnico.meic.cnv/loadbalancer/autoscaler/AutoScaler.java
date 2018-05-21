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
        System.out.println("AutoScaler Run");
        checkPlusOne();
        checkMinusOne();
    }

    /**
     * Conditions for scaleUp:
     *  1. maxInstances haven't been reached
     *  2. numAvailableInstances are below minInstances
     *   or
     *  3. minAvailableComplexityPower has been reached
     *
     *  Try to reuse a toBeRemove instance if available
     *  otherwise, launch a new instance
     */
    private void checkPlusOne(){

        synchronized (context.getInstanceList()) {
            int numAvailableInstances = getContext().getInstanceList().size() - getToBeDeletedList().size();

            if(numAvailableInstances < WebServer.maxInstances && (
                    WebServer.minInstances > numAvailableInstances ||
                            getClusterComplexity() + WebServer.minAvailableComplexityPower > WebServer.instanceCapacity * numAvailableInstances)) {

                System.out.println("AutoScaler: +1");
                System.out.println("Cluster before: " + getClusterComplexity() + " / " + WebServer.instanceCapacity * numAvailableInstances);


                //try to restore a toBeRemoved instance

                synchronized (AutoScaler.getToBeDeletedList()) {
                    if (AutoScaler.getToBeDeletedList().size() >= 1) {
                        System.out.println("AutoScaler: Restoring: " + getToBeDeletedList().get(0).getId());
                        getToBeDeletedList().get(0).restore();
                        getToBeDeletedList().remove(0);
                        return;
                    }
                }

                addEC2Instance();
                System.out.println("Cluster after: " + getClusterComplexity() + " / " + WebServer.instanceCapacity * numAvailableInstances);

            }
        }

    }

    /**
     * Conditions to scaleDown:
     * 1. numInstances is not at a minimum
     * 2. clusterComplexity e smaller than the maxAvailableComplexityPower x clusterMinusOneMaxLoad
     *
     */
    private void checkMinusOne(){

        synchronized (context.getInstanceList()) {

            int numAvailableInstances = getContext().getInstanceList().size() - getToBeDeletedList().size();
            double numberInstancesMinusOne = numAvailableInstances - 1;
            double clusterMinusOneMaxLoad = WebServer.instanceCapacity * numberInstancesMinusOne;
            double clusterMinusOneThresholdLoad = Math.max(0, clusterMinusOneMaxLoad - WebServer.minAvailableComplexityPower - WebServer.maxAvailableComplexityPower);

            if(numAvailableInstances > WebServer.minInstances &&
                    getClusterComplexity() <= clusterMinusOneThresholdLoad) {

                System.out.println ("AutoScaler: -1 (scheduled)");
                System.out.println("Cluster before: " + getClusterComplexity() + " / " + WebServer.instanceCapacity * numAvailableInstances);

                InstanceInfo instanceInfo = findMinComplexityInstance();
                if(instanceInfo == null){
                    System.out.println("Zero instances found running.");
                    return;
                }

                instanceInfo.remove();
                getToBeDeletedList().add(instanceInfo);

            }
        }
    }

    /**
     * Gets the sum of all instanceInfo's complexity, excluding the ones marked to be removed
     *
     * @return
     */
    private double getClusterComplexity() {

        double clusterComplexity = 0;
        for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
            if(!instanceInfo.toBeRemoved()){
                clusterComplexity += instanceInfo.getComplexity();
            }
        }
        return clusterComplexity;

    }

    /**
     * Find the minimum complexity instance that's live and running
     *
     * @return
     */
    private InstanceInfo findMinComplexityInstance() {
        double minComplexity = 0;
        InstanceInfo complexityCandidateInstanceInfo = null;

        for (InstanceInfo instanceInfo : getContext().getInstanceList()) {
            if (instanceInfo.isRunning() && !instanceInfo.toBeRemoved()) {

                if (minComplexity == 0 || instanceInfo.getComplexity() <= minComplexity) {
                    minComplexity = instanceInfo.getComplexity();
                    complexityCandidateInstanceInfo = instanceInfo;
                }
            }
        }

        return complexityCandidateInstanceInfo;
    }


    /**
     * Add a new instance to AWS.
     * Add newly created instance to
     *
     */
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


        getContext().addInstance(instanceInfo);

        System.out.println ("AutoScaler: added new ec2 instance with id: " + instanceInfo.getId());
    }


    /**
     * Removes an EC2 instance by ID from AWS
     *
     * @param instanceId
     */
    public static void removeEC2Instance(String instanceId) {
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        WebServer.getContext().getEc2().terminateInstances(termInstanceReq);
        System.out.println ("Removed instance with id, " + instanceId + " from aws.");
    }

    /**
     * Terminates all instances from AWS
     */
    public static void removeAll(){
        System.out.println ("Trying to remove all instances from aws");
        for(InstanceInfo instanceInfo : WebServer.getContext().getInstanceList()){
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(instanceInfo.getId());
            WebServer.getContext().getEc2().terminateInstances(termInstanceReq);
        }
    }

}
