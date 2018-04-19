package pt.ulisboa.tecnico.meic.cnv.loadbalancer;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;

import java.util.ArrayList;
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
            //instance.dynamoDB.createTables();
        }
        return instance;
    }

    // todo
    private void createTables() {

    }

    public List<TableMetrics> getListValues () {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        try{
            return mapper.scan(TableMetrics.class, scanExpression);

        } catch(Exception exception) {
            exception.printStackTrace ();
        }
        return new ArrayList<> ();
    }

    public void writeValues (TableMetrics instance) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        mapper.save(instance);
    }

    public TableMetrics getIncompleteMetricByThreadId (long threadId) {
        List<TableMetrics> metrics = getListValues ();
        for (TableMetrics metric : metrics) {
            if (metric.getThreadId () == threadId && !metric.getCompleted ()) {
                return metric;
            }
        }
        return null;
    }

    public void deleteIncompleteMetricByThreadId (long threadId) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        TableMetrics metric = getIncompleteMetricByThreadId (threadId);
        mapper.delete (metric);
    }


}
