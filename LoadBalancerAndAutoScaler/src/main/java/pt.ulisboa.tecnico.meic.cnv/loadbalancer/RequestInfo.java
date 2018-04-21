package pt.ulisboa.tecnico.meic.cnv.loadbalancer;


public class RequestInfo {
    public int initX;
    public int initY;
    public int finalX;
    public int finalY;
    public int velocity;
    public String strategy;
    public String maze;
    public int estimatedComplexity;

    public int getSize() {
        return RequestInfo.getSize(this.maze);
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

    public int computeEstimatedComplexity(){
        return this.getSize() * (int)Math.floor(this.getDistance()) / this.velocity;
    }

    public static int computeEstimatedComplexity(int size, double distance, int velocity) {
        return size * (int)Math.floor(distance) / velocity;
    }






}
