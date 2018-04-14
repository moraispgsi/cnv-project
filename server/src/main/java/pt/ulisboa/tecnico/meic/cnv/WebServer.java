package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantGenerateOutputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantReadMazeInputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidCoordinatesException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidMazeRunningStrategyException;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class WebServer {


    static final String  MAZE_DIR = "src/main/resources/mazes/";
    static final String RESULT_DIR = "src/main/resources/results/";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/mzrun.html", new MyHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // creates a non-limited Executor
        server.start();
    }

    static class MyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {

            Map<String, String> params = queryToMap(t.getRequestURI().getQuery());

            String responseFileName = RESULT_DIR + params.hashCode() + ".html";

            String[] solverParams = new String[]{params.get("x0"), params.get("y0"), params.get("x1"),
                    params.get("y1"), params.get("v"), params.get("s"), MAZE_DIR + params.get("m"), responseFileName};

            try {
                System.out.println("Trying to solve: " + t.getRequestURI().getQuery());
                Main.main(solverParams);
                System.out.println("Response at: " + responseFileName);

            } catch (InvalidMazeRunningStrategyException | CantReadMazeInputFileException | CantGenerateOutputFileException | InvalidCoordinatesException e) {
                e.printStackTrace();
            }


            File file = new File(responseFileName);
            byte [] bytearray  = new byte [(int)file.length()];
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            bis.read(bytearray, 0, bytearray.length);

            t.sendResponseHeaders(200, file.length());
            OutputStream os = t.getResponseBody();
            os.write(bytearray,0,bytearray.length);
            os.close();

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
    }

}
