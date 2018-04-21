package pt.ulisboa.tecnico.meic.cnv.loadbalancer;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;

import java.util.List;


public final class DynamoDB {

    private static DynamoDB instance = null;


    private AmazonDynamoDB dynamoDB = null;

    public static DynamoDB getInstance () {
        if (instance == null) {
            instance = new DynamoDB ();
            instance.dynamoDB = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(Regions.EU_WEST_3)
                    .build();
        }
        return instance;
    }

    public List<Metric> getMetrics () {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        try{
            return mapper.scan(Metric.class, scanExpression);

        } catch(Exception exception) {
            exception.printStackTrace ();
        }
        return null;
    }

    public void writeValues (Metric instance) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        try {
            mapper.save(instance);

        } catch (Exception exception) {
            System.out.println (instance);
            exception.printStackTrace ();
        }
    }

    public Metric getIncompleteMetricByThreadId (long threadId) {
        List<Metric> metrics = getMetrics();
        if (metrics != null) {
            for (Metric metric : metrics) {
                if (metric.getThreadId () == threadId && !metric.getCompleted ()) {
                    return metric;
                }
            }
        }
        return null;
    }

    public void deleteIncompleteMetricByThreadId (long threadId) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        Metric metric = getIncompleteMetricByThreadId (threadId);
        try {
            mapper.delete (metric);

        } catch (Exception exception) {
            exception.printStackTrace ();
        }
    }


}
