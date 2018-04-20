package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

public class InstanceInfo {
    public String id;
    public String hostIp;
    public boolean queueRemove = false;
    public int requestPending;
}
