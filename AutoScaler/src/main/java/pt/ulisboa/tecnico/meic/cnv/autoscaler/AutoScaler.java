package pt.ulisboa.tecnico.meic.cnv.autoscaler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class AutoScaler {

    public static void main(String[] args) throws Exception {

        //TODO - Connect to DynamoDB.
        //TODO - Work as a service and keep evaluating the metrics every X seconds where X should be one that
        //does not cripples the performance

    }

    public static void addEC2Instance() {
        //TODO - Create a function that creates a new EC2 instance copy
        //(not sure if we have to provision the machine with the webserver software copy or we can simply copy an ec2 image)
        //Once the machine is provisioned, a flag is set to true in DynamoDB that lets the Loadbalancer know that
        //there is another machine available(or the load balancer just query the server URLS and find another one)
    }

    public static void removeEC2Instance() {
        //Marks an instance to be removed and waits for the load balancer to make sure that the instance is not
        //receiving or processing any more requests. After that it removes the instance from EC2.
        //This method requires synchronization between the autoscaler and the load balancer through the DynamoDB database.
    }

}
