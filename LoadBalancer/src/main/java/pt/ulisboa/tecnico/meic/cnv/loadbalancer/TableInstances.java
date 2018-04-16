package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;


@DynamoDBTable(tableName = "instances")
public class TableInstances {

    public String url;
    public Boolean queueStop;
    public Boolean readyToDelete;

    @DynamoDBHashKey(attributeName = "url")
    public String getUrl () {
        return this.url;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    @DynamoDBHashKey(attributeName = "queueStop")
    public Boolean getQueueStop () {
        return this.queueStop;
    }
    public void setQueueStop(Boolean queueStop) {
        this.queueStop = queueStop;
    }

    @DynamoDBHashKey(attributeName = "readyToDelete")
    public Boolean getReadyToDelete () {
        return this.readyToDelete;
    }
    public void setReadyToDelete(Boolean readyToDelete) {
        this.readyToDelete = readyToDelete;
    }

}
