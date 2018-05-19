package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.DynamoDB;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.Metric;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantGenerateOutputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantReadMazeInputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidCoordinatesException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidMazeRunningStrategyException;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;


public class WebServer {

    public static final int PORT = 80;

    static final String MAZE_DIR = "../resources/mazes/";
    static final String RESULT_DIR = "../resources/results/";


    public static void main (String[] args) throws Exception {
        System.out.println ("Init web server...");
        HttpServer server = HttpServer.create (new InetSocketAddress (PORT), 0);
        server.createContext ("/mzrun.html", new MyHandler ());
        server.createContext ("/healthCheck", new HealthCheckHandler ());
        server.setExecutor (Executors.newCachedThreadPool ()); // creates a non-limited Executor
        server.start ();
        System.out.println ("Maze Web server listening on port " + PORT);
    }

    static class HealthCheckHandler implements HttpHandler {

        @Override public void handle (HttpExchange t) throws IOException {
            String response = "alive";
            t.sendResponseHeaders (200, response.length ());
            OutputStream os = t.getResponseBody ();
            os.write (response.getBytes ());
            os.close ();
        }
    }


    static class MyHandler implements HttpHandler {

        @Override public void handle (HttpExchange t) throws IOException {
            long threadId = Thread.currentThread ().getId ();
            Map<String, String> params = queryToMap (t.getRequestURI ().getQuery ());

            String responseFileName = RESULT_DIR + params.hashCode () + ".html";

            String[] solverParams =
                    new String[] { params.get ("x0"), params.get ("y0"), params.get ("x1"), params.get ("y1"),
                            params.get ("v"), params.get ("s"), MAZE_DIR + params.get ("m"), responseFileName };

            try {
                // generate key
                String key = new Date ().toString () + "-" + threadId;
                // save request input data on dynamo
                DynamoDB.getInstance ()
                        .writeValues (new Metric (key, false, threadId, Integer.parseInt (params.get ("x0")),
                                Integer.parseInt (params.get ("y0")), Integer.parseInt (params.get ("x1")),
                                Integer.parseInt (params.get ("y1")), params.get ("m"), params.get ("s"),
                                Integer.parseInt (params.get ("v"))));

                System.out.println (
                        "Thread with id: '" + threadId + "' > Trying to solve: " + t.getRequestURI ().getQuery ());
                // solve maze
                Main.main (solverParams);
                System.out.println ("Thread with id: '" + threadId + "' > Response at: " + responseFileName);

            } catch (InvalidMazeRunningStrategyException | CantReadMazeInputFileException | CantGenerateOutputFileException | InvalidCoordinatesException | NumberFormatException e) {
                e.printStackTrace ();

                // write error message to output
                String response = e.getMessage();
                t.sendResponseHeaders (200, response.length ());
                OutputStream os = t.getResponseBody ();
                os.write (response.getBytes(), 0, response.getBytes().length);
                os.close ();


                // delete record pre created by the thread, because the request wasn't finished
                DynamoDB.getInstance ().deleteIncompleteMetricByThreadId (threadId);

                return;
            } catch (Exception e) {
                e.printStackTrace ();
                return;
                //TODO
            }

            File file = new File (responseFileName);
            byte[] bytearray = new byte[(int) file.length ()];
            FileInputStream fis = new FileInputStream (file);
            BufferedInputStream bis = new BufferedInputStream (fis);
            bis.read (bytearray, 0, bytearray.length);

            t.sendResponseHeaders (200, file.length ());
            OutputStream os = t.getResponseBody ();
            os.write (bytearray, 0, bytearray.length);
            os.close ();

        }

        private Map<String, String> queryToMap (String query) {
            Map<String, String> result = new HashMap<String, String> ();
            for (String param : query.split ("&")) {
                String pair[] = param.split ("=");
                if (pair.length > 1) {
                    result.put (pair[0], pair[1]);
                } else {
                    result.put (pair[0], "");
                }
            }
            return result;
        }
    }

}
