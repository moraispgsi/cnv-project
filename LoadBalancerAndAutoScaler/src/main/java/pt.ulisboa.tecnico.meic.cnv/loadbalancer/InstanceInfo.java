package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import java.util.ArrayList;
import java.util.List;


public class InstanceInfo {
    public String id;
    public String hostIp;
    public boolean queueRemove = false;
    public int requestPending;
    public List<RequestInfo> currentRequests = new ArrayList<> ();


    public int getComplexity() {
        int sum = 0;
        for(RequestInfo requestInfo: this.currentRequests) {
            sum += requestInfo.estimatedComplexity;
        }
        return sum;
    }

    @Override public String toString () {
        return "InstanceInfo{" + "id='" + id + '\'' + ", hostIp='" + hostIp + '\'' + ", queueRemove=" + queueRemove +
                ", requestPending=" + requestPending + '}';
    }

}
