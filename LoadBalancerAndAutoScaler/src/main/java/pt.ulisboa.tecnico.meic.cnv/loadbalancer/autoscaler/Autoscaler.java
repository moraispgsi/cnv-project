package pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.Context;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.InstanceInfo;

import java.util.*;


public class Autoscaler implements Runnable {

    //TODO - adjust the values
    private final int MIN_LOAD_COMPLEXITY = 100;
    private final int MAX_LOAD_COMPLEXITY = 4000;

    private int minInstances =  1;
    private int maxInstance = 3;
    private Context context;

    public Autoscaler(Context context) {
        this.context = context;

        //Create the minInstances
        for(int i = 0; i < minInstances; i++) {
            this.addEC2Instance();
        }
    }

    @Override
    public void run() {

        while(true) {
            try {
                Thread.sleep(2000);

                synchronized (context.getInstanceList()) {
                    List<InstanceInfo> availableInstanceInfoList = new ArrayList<>();

                    //Filter available instances
                    for(InstanceInfo instanceInfo : context.getInstanceList()) {
                        if(!instanceInfo.queueRemove) {
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
                        if(instanceInfo.queueRemove &&
                                instanceInfo.requestPending == 0) {

                            removeEC2Instance(context.getEc2(), instanceInfo.id); //remove from EC2
                            iterator.remove(); //Remove from the list
                        }
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void addEC2Instance() {
        try {
            Instance instance = Autoscaler.addEC2Instance(context.getEc2(), context.getEc2WebServerImage(),
                    context.getEc2WebServerKeyPairName(), context.getEc2WebServerSecurityGroup(),
                    context.getEc2WebServerInstanceType());
            //Now that we know that the instance is running we make it available to the load balancer
            InstanceInfo instanceInfo = new InstanceInfo();
            instanceInfo.id = instance.getInstanceId();
            instanceInfo.hostIp = instance.getPublicIpAddress() + ":8000";

            synchronized (context.getInstanceList()) {
                context.getInstanceList().add(instanceInfo);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void queueInstanceRemoval() {
        synchronized (context.getInstanceList()) {
            int availableCount = 0;
            List<InstanceInfo> instanceInfoList = context.getInstanceList();
            for(InstanceInfo instanceInfo: instanceInfoList) {
                if(!instanceInfo.queueRemove) {
                    availableCount ++;
                }
            }

            if(availableCount > minInstances) {
                //We can choose a random instance because the load balancer will not redirect any more requests to the
                // instance, making its resource usage eventually 0, without disrupting its previously assign requests.
                Random random = new Random();
                int removeIndex = random.nextInt(instanceInfoList.size() + 1);
                instanceInfoList.get(removeIndex).queueRemove = true;
            }
        }
    }


    public static Instance addEC2Instance(AmazonEC2 ec2, String image, String keyPairName, String securityGroup, String instanceType) throws InterruptedException {

        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(image)
                .withInstanceType(instanceType)
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(keyPairName)
                .withSecurityGroups(securityGroup);

        RunInstancesResult runInstancesResult = ec2.runInstances(
                runInstancesRequest);

        String newInstanceId = runInstancesResult.getReservation().getInstances()
                .get(0).getInstanceId();


        List<String> instanceIdsList = new ArrayList<>();
        instanceIdsList.add(newInstanceId);

        //Verify if the instance is running

        //TODO - Test the instance's health by sending a ping.

        System.out.println ("Waiting for instance " + newInstanceId + "  to be available...");

        //While true :/
        while(true) {
            Thread.sleep(2000);
            DescribeInstancesRequest describeInstancesRequest =new DescribeInstancesRequest();
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
        }
    }

    //Removes an EC2 instance by ID
    public static void removeEC2Instance(AmazonEC2 ec2, String instanceId) {
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
    }

}
