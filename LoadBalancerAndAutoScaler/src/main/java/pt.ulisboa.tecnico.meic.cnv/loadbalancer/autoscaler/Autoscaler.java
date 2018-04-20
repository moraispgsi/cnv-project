package pt.ulisboa.tecnico.meic.cnv.loadbalancer.autoscaler;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.Context;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.InstanceInfo;

import java.util.*;

import static com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolver.iterator;

public class Autoscaler implements Runnable {

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

    public void addEC2Instance() {
        Instance instance = null;
        try {
            instance = Autoscaler.addEC2Instance(context.getEc2(), context.getEc2WebServerImage(),
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


    @Override
    public void run() {

        while(true) {
            try {
                Thread.sleep(2000);

                //------
                // Add necessary things that need to run in the thread
                //------

                //todo - check metrics
                //todo - make decisions
                //If the global load can be distributed to fewer machines, the autoscaler should remove one machine.
                //If the load is too much for the instances, a new should be made
                //There should be a concern for the number of min/max instances
                //------

                //This should be running in the background, the autoscaler should be looking for instances that
                //are ready to be deleted.
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

    public void queueInstanceRemoval() {

        //todo - test if we can remove the instance instances > minInstances

        //todo - find one instance to be removed, not sure if we take the metrics into account
        // (probably not because the load balancer will not redirect any more requests to the instance making its
        // resource usage eventually 0)
        //todo - queue the deletion
        //todo - wait for the instance to be cleared.


        List<InstanceInfo> instanceInfoList = context.getInstanceList();
        Random random = new Random();
        int removeIndex = random.nextInt(instanceInfoList.size() + 1);

        synchronized (instanceInfoList) {
            instanceInfoList.get(removeIndex).queueRemove = true;
        }

    }

    //Image: don't know yet
    //KeyPairName: ec2InstanceKeyPair
    //Security group: launch-wizard-2
    //Instance Type: t2.micro
    public static Instance addEC2Instance(AmazonEC2 ec2, String image, String keyPairName, String securityGroup, String instanceType) throws InterruptedException {
        //How to get the availability zone for paris
        //DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();

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
