package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RequestInfo {

    private int initX;
    private int initY;
    private int finalX;
    private int finalY;
    private int velocity;
    private String strategy;
    private String maze;
    private int estimatedComplexity;
    private UUID uuid;


    public RequestInfo(HttpExchange httpExchange){

        Map<String, String> params = queryToMap(httpExchange.getRequestURI().getQuery());

        int initX = Integer.parseInt(params.get("x0")); //Point A
        int initY = Integer.parseInt(params.get("y0")); //Point A
        int finalX = Integer.parseInt(params.get("x1")); //Point B
        int finalY = Integer.parseInt(params.get("y1")); //Point B
        int velocity = Integer.parseInt(params.get("v")); //Velocity
        String strategy = params.get("s"); //Strategy
        String maze = params.get("m"); //Maze

        this.initX = initX;
        this.initY = initY;
        this.finalX = finalX;
        this.finalY = finalY;
        this.velocity = velocity;
        this.strategy = strategy;
        this.maze = maze;
        this.estimatedComplexity = getSize() * (int)Math.floor(getDistance()) / velocity;
        this.uuid = UUID.randomUUID();
    }


    public int getSize() {
        return RequestInfo.getSize(this.maze);
    }

    public String getStrategy() {
        return strategy;
    }

    public String getMaze() {
        return maze;
    }

    public static int getSize(String maze) {
        switch(maze) {
            case "Maze50.maze":
                return (int) Math.pow(50, 2);
            case "Maze100.maze":
                return (int) Math.pow(100, 2);
            case "Maze250.maze":
                return (int) Math.pow(250, 2);
            case "Maze300.maze":
                return (int) Math.pow(300, 2);
            case "Maze500.maze":
                return (int) Math.pow(500, 2);
            case "Maze750.maze":
                return (int) Math.pow(750, 2);
            case "Maze1000.maze":
                return (int) Math.pow(1000, 2);
            default:
                return 0;
        }
    }

    public double getDistance() {
        int x = initX - finalX;
        int y = initY - finalY;
        return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
    }

    public static double getDistance(int initX, int initY, int finalX, int finalY) {
        int x = initX - finalX;
        int y = initY - finalY;
        return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
    }

    public int getEstimatedComplexity(){
        return estimatedComplexity;
    }

    public static int computeEstimatedComplexity(int size, double distance, int velocity) {
        return size * (int)Math.floor(distance) / velocity;
    }

    private Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<String, String>();
        for (String param : query.split("&")) {
            String pair[] = param.split("=");
            if (pair.length > 1) {
                result.put(pair[0], pair[1]);
            } else {
                result.put(pair[0], "");
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        RequestInfo that = (RequestInfo) o;

        return new EqualsBuilder()
                .append(uuid, that.uuid)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(uuid)
                .toHashCode();
    }
}
