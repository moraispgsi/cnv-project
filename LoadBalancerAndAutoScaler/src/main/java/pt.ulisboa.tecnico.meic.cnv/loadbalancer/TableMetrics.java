package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;


@DynamoDBTable (tableName = "metrics") public class TableMetrics {

    private String key;
    private boolean completed;
    private long threadId;
    // input data
    private int initX;
    private int initY;
    private int finalX;
    private int finalY;
    private String maze;
    private String strategy;
    private int velocity;
    // metrics
    private long numberInstructions;
    private long methodCount;
    private long objectCreationCount;
    private long newArrayCount;


    public TableMetrics (String key, boolean completed, long threadId, int initX, int initY, int finalX, int finalY,
                         String maze, String strategy, int velocity) {
        this.key = key;
        this.completed = completed;
        this.threadId = threadId;
        this.initX = initX;
        this.initY = initY;
        this.finalX = finalX;
        this.finalY = finalY;
        this.maze = maze;
        this.strategy = strategy;
        this.velocity = velocity;
        this.numberInstructions = 0;
        this.methodCount = 0;
        this.objectCreationCount = 0;
        this.newArrayCount = 0;
    }

    @DynamoDBHashKey (attributeName = "key") public String getKey () {
        return this.key;
    }

    public void setKey (String key) {
        this.key = key;
    }

    @DynamoDBHashKey (attributeName = "completed") public boolean getCompleted () {
        return completed;
    }

    public void setCompleted (boolean completed) {
        this.completed = completed;
    }

    @DynamoDBHashKey (attributeName = "threadId") public long getThreadId () {
        return threadId;
    }

    public void setThreadId (long threadId) {
        this.threadId = threadId;
    }

    @DynamoDBHashKey (attributeName = "initX") public int getInitX () {
        return initX;
    }

    public void setInitX (int initX) {
        this.initX = initX;
    }

    @DynamoDBHashKey (attributeName = "initY") public int getInitY () {
        return initY;
    }

    public void setInitY (int initY) {
        this.initY = initY;
    }

    @DynamoDBHashKey (attributeName = "finalX") public int getFinalX () {
        return finalX;
    }

    public void setFinalX (int finalX) {
        this.finalX = finalX;
    }

    @DynamoDBHashKey (attributeName = "finalY") public int getFinalY () {
        return finalY;
    }

    public void setFinalY (int finalY) {
        this.finalY = finalY;
    }

    @DynamoDBHashKey (attributeName = "maze") public String getMaze () {
        return maze;
    }

    public void setMaze (String maze) {
        this.maze = maze;
    }

    @DynamoDBHashKey (attributeName = "strategy") public String getStrategy () {
        return strategy;
    }

    public void setStrategy (String strategy) {
        this.strategy = strategy;
    }

    @DynamoDBHashKey (attributeName = "velocity") public int getVelocity () {
        return velocity;
    }

    public void setVelocity (int velocity) {
        this.velocity = velocity;
    }


    @DynamoDBHashKey (attributeName = "numberInstructions") public long getNumberInstructions () {
        return numberInstructions;
    }

    public void setNumberInstructions (long numberInstructions) {
        this.numberInstructions = numberInstructions;
    }

    @DynamoDBHashKey (attributeName = "methodCount") public long getMethodCount () {
        return methodCount;
    }

    public void setMethodCount (long methodCount) {
        this.methodCount = methodCount;
    }

    @DynamoDBHashKey (attributeName = "objectCreationCount") public long getObjectCreationCount () {
        return objectCreationCount;
    }

    public void setObjectCreationCount (long objectCreationCount) {
        this.objectCreationCount = objectCreationCount;
    }

    @DynamoDBHashKey (attributeName = "newArrayCount") public long getNewArrayCount () {
        return newArrayCount;
    }

    public void setNewArrayCount (long newArrayCount) {
        this.newArrayCount = newArrayCount;
    }
}
