package pt.ulisboa.tecnico.meic.cnv.loadbalancer.exceptions;

public class DeadInstanceException extends Exception {
    public DeadInstanceException(String message) {
        super(message);
    }
}
