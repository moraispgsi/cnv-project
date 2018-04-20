package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
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
    private long instructionsCount;
    private long methodInvocationCount;
    private long objectAllocationCount;
    private long arrayAllocationCount;


    // necessary for the mapper scan
    public TableMetrics () {

    }

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
        this.instructionsCount = 0;
        this.methodInvocationCount = 0;
        this.objectAllocationCount = 0;
        this.arrayAllocationCount = 0;
    }

    @DynamoDBHashKey (attributeName = "key") public String getKey () {
        return this.key;
    }

    public void setKey (String key) {
        this.key = key;
    }

    @DynamoDBAttribute (attributeName = "completed") public boolean getCompleted () {
        return completed;
    }

    public void setCompleted (boolean completed) {
        this.completed = completed;
    }

    @DynamoDBAttribute (attributeName = "threadId") public long getThreadId () {
        return threadId;
    }

    public void setThreadId (long threadId) {
        this.threadId = threadId;
    }

    @DynamoDBAttribute (attributeName = "initX") public int getInitX () {
        return initX;
    }

    public void setInitX (int initX) {
        this.initX = initX;
    }

    @DynamoDBAttribute (attributeName = "initY") public int getInitY () {
        return initY;
    }

    public void setInitY (int initY) {
        this.initY = initY;
    }

    @DynamoDBAttribute (attributeName = "finalX") public int getFinalX () {
        return finalX;
    }

    public void setFinalX (int finalX) {
        this.finalX = finalX;
    }

    @DynamoDBAttribute (attributeName = "finalY") public int getFinalY () {
        return finalY;
    }

    public void setFinalY (int finalY) {
        this.finalY = finalY;
    }

    @DynamoDBAttribute (attributeName = "maze") public String getMaze () {
        return maze;
    }

    public void setMaze (String maze) {
        this.maze = maze;
    }

    @DynamoDBAttribute (attributeName = "strategy") public String getStrategy () {
        return strategy;
    }

    public void setStrategy (String strategy) {
        this.strategy = strategy;
    }

    @DynamoDBAttribute (attributeName = "velocity") public int getVelocity () {
        return velocity;
    }

    public void setVelocity (int velocity) {
        this.velocity = velocity;
    }

    @DynamoDBAttribute (attributeName = "instructionsCount") public long getInstructionsCount () {
        return instructionsCount;
    }

    public void setInstructionsCount (long instructionsCount) {
        this.instructionsCount = instructionsCount;
    }

    @DynamoDBAttribute (attributeName = "methodInvocationCount") public long getMethodInvocationCount () {
        return methodInvocationCount;
    }

    public void setMethodInvocationCount (long methodInvocationCount) {
        this.methodInvocationCount = methodInvocationCount;
    }

    @DynamoDBAttribute (attributeName = "objectAllocationCount") public long getObjectAllocationCount () {
        return objectAllocationCount;
    }

    public void setObjectAllocationCount (long objectAllocationCount) {
        this.objectAllocationCount = objectAllocationCount;
    }

    @DynamoDBAttribute (attributeName = "arrayAllocationCount") public long getArrayAllocationCount () {
        return arrayAllocationCount;
    }

    public void setArrayAllocationCount (long arrayAllocationCount) {
        this.arrayAllocationCount = arrayAllocationCount;
    }

    @Override public String toString () {
        return "TableMetrics{" + "key='" + key + '\'' + ", completed=" + completed + ", threadId=" + threadId +
                ", initX=" + initX + ", initY=" + initY + ", finalX=" + finalX + ", finalY=" + finalY + ", maze='" +
                maze + '\'' + ", strategy='" + strategy + '\'' + ", velocity=" + velocity + ", instructionsCount=" +
                instructionsCount + ", methodInvocationCount=" + methodInvocationCount + ", objectAllocationCount=" +
                objectAllocationCount + ", arrayAllocationCount=" + arrayAllocationCount + '}';
    }
}
