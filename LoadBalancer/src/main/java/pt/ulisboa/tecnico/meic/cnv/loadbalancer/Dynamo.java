package pt.ulisboa.tecnico.meic.cnv.loadbalancer;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;

import java.util.ArrayList;
import java.util.List;


public class Dynamo {

    public static AmazonDynamoDB dynamoDB;

    public static List<TableMetrics> getListValues () {
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.EU_WEST_3)
                .build();
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        try{
            return mapper.scan(TableMetrics.class, scanExpression);

        } catch(Exception exception) {
            exception.printStackTrace ();
        }
        return new ArrayList<> ();
    }

    public static void writeValues (TableMetrics instance) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        mapper.save(instance);
    }

    public static TableMetrics getIncompleteInstanceByThreadId (int threadId) {
        List<TableMetrics> instances = getListValues ();
        for (TableMetrics instance : instances) {
            if (instance.getThreadId () == threadId && !instance.getCompleted ()) {
                return instance;
            }
        }
        return null;
    }
}
