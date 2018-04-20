package pt.ulisboa.tecnico.meic.cnv.loadbalancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

//The context that both the loadbalancer and the autoscaler share
public class Context {

    private List<InstanceInfo> instanceList;
    private AmazonEC2 ec2;
    private String ec2WebServerImage;
    private String ec2WebServerKeyPairName;
    private String ec2WebServerSecurityGroup;
    private String ec2WebServerInstanceType;

    public Context(String ec2WebServerImage, String ec2WebServerKeyPairName,
                   String ec2WebServerSecurityGroup, String ec2WebServerInstanceType) {

        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = AmazonEC2ClientBuilder.standard().withRegion("eu-west-3").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        this.instanceList = new ArrayList<>();
        this.ec2WebServerImage = ec2WebServerImage;
        this.ec2WebServerKeyPairName = ec2WebServerKeyPairName;
        this.ec2WebServerSecurityGroup = ec2WebServerSecurityGroup;
        this.ec2WebServerInstanceType = ec2WebServerInstanceType;
    }

    public AmazonEC2 getEc2() {
        return ec2;
    }

    public void setEc2(AmazonEC2 ec2) {
        this.ec2 = ec2;
    }

    public String getEc2WebServerImage() {
        return ec2WebServerImage;
    }

    public void setEc2WebServerImage(String ec2WebServerImage) {
        this.ec2WebServerImage = ec2WebServerImage;
    }

    public String getEc2WebServerKeyPairName() {
        return ec2WebServerKeyPairName;
    }

    public void setEc2WebServerKeyPairName(String ec2WebServerKeyPairName) {
        this.ec2WebServerKeyPairName = ec2WebServerKeyPairName;
    }

    public String getEc2WebServerSecurityGroup() {
        return ec2WebServerSecurityGroup;
    }

    public void setEc2WebServerSecurityGroup(String ec2WebServerSecurityGroup) {
        this.ec2WebServerSecurityGroup = ec2WebServerSecurityGroup;
    }

    public String getEc2WebServerInstanceType() {
        return ec2WebServerInstanceType;
    }

    public void setEc2WebServerInstanceType(String ec2WebServerInstanceType) {
        this.ec2WebServerInstanceType = ec2WebServerInstanceType;
    }

    public List<InstanceInfo> getInstanceList() {
        return instanceList;
    }

}
