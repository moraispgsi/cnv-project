package pt.ulisboa.tecnico.meic.cnv.loadbalancer;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;

import javax.xml.bind.SchemaOutputResolver;
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
/*
criar uma nova thread para guardar, apagar, ...
new Thread(new Runnable() {
         @Override
         public void run() {
             dynamoDBMapper.save(newsItem);
                 // Item saved
         }
     }).start();
 */
    // todo
    private void createTables() {

    }

    public List<TableMetrics> getListValues () {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        try{
            return mapper.scan(TableMetrics.class, scanExpression);

        } catch(Exception exception) {
            System.out.println ("falhei aqui");
            exception.printStackTrace ();
        }
        return null;
    }

    public void writeValues (final TableMetrics instance) {
        final DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        try {
            mapper.save(instance);

        } catch (Exception exception) {
            System.out.println (instance);
            exception.printStackTrace ();
        }
    }

    public TableMetrics getIncompleteMetricByThreadId (long threadId) {
        List<TableMetrics> metrics = getListValues ();
        if (metrics != null) {
            for (TableMetrics metric : metrics) {
                if (metric.getThreadId () == threadId && !metric.getCompleted ()) {
                    System.out.println ("Metric found: " + metric);
                    return metric;
                }
            }
        }
        return null;
    }

    public void deleteIncompleteMetricByThreadId (long threadId) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        TableMetrics metric = getIncompleteMetricByThreadId (threadId);
        try {
            mapper.delete (metric);

        } catch (Exception exception) {
            exception.printStackTrace ();
        }
    }


}
