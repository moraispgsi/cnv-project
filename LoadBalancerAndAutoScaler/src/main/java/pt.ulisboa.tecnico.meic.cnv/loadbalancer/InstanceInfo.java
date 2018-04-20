package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import java.util.ArrayList;
import java.util.List;


public class InstanceInfo {
    public String id;
    public String hostIp;
    public boolean queueRemove = false;
    public int requestPending;
    public List<RequestInfo> currentRequests = new ArrayList<> ();


    @Override public String toString () {
        return "InstanceInfo{" + "id='" + id + '\'' + ", hostIp='" + hostIp + '\'' + ", queueRemove=" + queueRemove +
                ", requestPending=" + requestPending + '}';
    }

}
