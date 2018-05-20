package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RequestInfo {

    private static final int MAX_COMPLEXITY = 4000000; // 2000 * 1 * 2000
    private static final int MAX_VELOCITIY = 100;
    private int initX;
    private int initY;
    private int finalX;
    private int finalY;
    private int velocity;
    private String strategy;
    private String maze;
    private double estimatedComplexity = 0;
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
        this.uuid = UUID.randomUUID();

        System.out.println (
                "Thread with id: '" + Thread.currentThread().getId() + "' > Request: " + httpExchange.getRequestURI ().getQuery ());
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

    public void setEstimatedComplexity(double estimatedComplexity) {
        this.estimatedComplexity = estimatedComplexity;
    }

    /**
     *
     * Calculates the size of the maze, summing the sizes of the maze
     *
     * @param maze
     * @return
     */
    public static int getSize(String maze) {
        switch(maze) {
            case "Maze50.maze":
                return 50*2;
            case "Maze100.maze":
                return 100*2;
            case "Maze250.maze":
                return 250*2;
            case "Maze300.maze":
                return 300*2;
            case "Maze500.maze":
                return 500*2;
            case "Maze750.maze":
                return 750*2;
            case "Maze1000.maze":
                return 1000*2;
            default:
                return 1;
        }
    }


    public double getDistance() {
        return RequestInfo.getDistance(initX, initY, finalX, finalY);
    }

    /**
     * Gets the euclidean distance between (initX, initY) and (finalX, finalY)
     *
     * @return
     */
    public static double getDistance(int initX, int initY, int finalX, int finalY) {
        int x = initX - finalX;
        int y = initY - finalY;
        return Math.abs(x) + Math.abs(y);
    }

    public double getEstimatedComplexity(){
        if(estimatedComplexity == 0) {
            return computeEstimatedComplexity(getSize(), getDistance(), velocity);
        }
        return this.estimatedComplexity;
    }

    /**
     * Uses the euclidean distance, a fraction of the velocity (between 0 and 1), and the size of the maze
     *
     * @param size
     * @param distance
     * @param velocity
     * @return a value between 0 and 10
     */
    public static double computeEstimatedComplexity(int size, double distance, int velocity) {
        return (distance * (MAX_VELOCITIY/velocity) * size) * 10 / MAX_COMPLEXITY;
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
