package pt.ulisboa.tecnico.meic.cnv.loadbalancer.threads;

import com.amazonaws.services.xray.model.Http;
import com.sun.net.httpserver.HttpExchange;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.InstanceInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class SolveMazeThread extends Thread {
    private InstanceInfo chosenCandidate;
    private HttpExchange httpExchange;

    public SolveMazeThread(InstanceInfo chosenCandidate, HttpExchange httpExchange) {
        this.chosenCandidate = chosenCandidate;
        this.httpExchange = httpExchange;
        this.setName("SolveMazeThread-"+Thread.currentThread().getId());
    }

    @Override
    public void run() {
        URL requestURL = null;

        try {
            requestURL = new URL("http://"
                    + chosenCandidate.getHostIp()
                    + "/mzrun.html?"
                    + httpExchange.getRequestURI().getQuery());
        } catch (MalformedURLException e){
            e.printStackTrace();
            //TODO
        }

        System.out.println(requestURL);

        try {
            URLConnection urlConnection = requestURL.openConnection();
            urlConnection.setReadTimeout(1000*60*30); //30 min
            urlConnection.setConnectTimeout(0);

            // Get current time
            long start = System.currentTimeMillis();

            // un-blockable operation
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                response.append(inputLine).append('\n');
            in.close();

            httpRespose(200, response);

            // Get elapsed time in milliseconds
            long elapsedTimeMillis = System.currentTimeMillis()-start;

            // Get elapsed time in minutes
            float elapsedTimeMin = elapsedTimeMillis/(60*1000F);

            System.out.println("Time: " +  elapsedTimeMin + "minutes.");

        } catch (IOException e) {
            System.err.println("SolveMazeThread: Timeout on: " + requestURL);
            try {
                httpRespose(200, new StringBuilder("Your request failed, for unknown reasons. (probably a timeout)"));
            } catch (IOException e1) {
                //do nothing
            }
        }
    }

    private void httpRespose(int statusCode, StringBuilder response) throws IOException {
        //Return response
        httpExchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = httpExchange.getResponseBody();
        os.write(response.toString().getBytes(), 0, response.toString().getBytes().length);
        os.close();
    }
}
