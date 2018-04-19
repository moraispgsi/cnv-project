package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;


@DynamoDBTable(tableName = "metrics")
public class TableMetrics {

    private String key;
    private Boolean completed;
    private int threadId;
    private int initX;
    private int initY;
    private int finalX;
    private int finalY;
    private String maze;
    private String strategy;
    private int velocity;

    @DynamoDBHashKey(attributeName = "key")
    public String getKey () {
        return this.key;
    }
    public void setKey(String key) {
        this.key = key;
    }

    @DynamoDBHashKey(attributeName = "completed")
    public Boolean getCompleted () {
        return completed;
    }
    public void setCompleted (Boolean completed) {
        this.completed = completed;
    }

    @DynamoDBHashKey(attributeName = "threadId")
    public int getThreadId () {
        return threadId;
    }
    public void setThreadId (int threadId) {
        this.threadId = threadId;
    }

    @DynamoDBHashKey(attributeName = "initX")
    public int getInitX () {
        return initX;
    }
    public void setInitX (int initX) {
        this.initX = initX;
    }

    @DynamoDBHashKey(attributeName = "initY")
    public int getInitY () {
        return initY;
    }
    public void setInitY (int initY) {
        this.initY = initY;
    }

    @DynamoDBHashKey(attributeName = "finalX")
    public int getFinalX () {
        return finalX;
    }
    public void setFinalX (int finalX) {
        this.finalX = finalX;
    }

    @DynamoDBHashKey(attributeName = "finalY")
    public int getFinalY () {
        return finalY;
    }
    public void setFinalY (int finalY) {
        this.finalY = finalY;
    }

    @DynamoDBHashKey(attributeName = "maze")
    public String getMaze () {
        return maze;
    }
    public void setMaze (String maze) {
        this.maze = maze;
    }

    @DynamoDBHashKey(attributeName = "strategy")
    public String getStrategy () {
        return strategy;
    }
    public void setStrategy (String strategy) {
        this.strategy = strategy;
    }

    @DynamoDBHashKey(attributeName = "velocity")
    public int getVelocity () {
        return velocity;
    }
    public void setVelocity (int velocity) {
        this.velocity = velocity;
    }

}
