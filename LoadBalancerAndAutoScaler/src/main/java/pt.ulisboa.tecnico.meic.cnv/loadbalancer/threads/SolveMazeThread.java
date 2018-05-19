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
            urlConnection.setReadTimeout(1000*60*10); //10 min
            urlConnection.setConnectTimeout(0);

            // un-blockable operation
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                response.append(inputLine).append('\n');
            in.close();

            //Return response
            httpExchange.sendResponseHeaders(200, response.length());
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.toString().getBytes(), 0, response.toString().getBytes().length);
            os.close();

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}
