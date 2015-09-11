package com.openshift.openshiftjenkinsbuildutils;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.OpenShiftAPIVersion;
import com.openshift.internal.restclient.URLBuilder;
import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ISSLCertificateCallback;
import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.ICapability;
import com.openshift.restclient.capability.resources.IBuildTriggerable;
import com.openshift.restclient.capability.resources.IPodLogRetrieval;
import com.openshift.restclient.http.IHttpClient;
import com.openshift.restclient.model.IBuild;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IProject;
import com.openshift.restclient.model.IReplicationController;
import com.openshift.restclient.model.IResource;

import javax.net.ssl.SSLSession;
import javax.servlet.ServletException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenShift {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link OpenShiftDeploymentVerifier} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Gabe Montero
 */
public class OpenShiftDeploymentVerifier extends Builder implements ISSLCertificateCallback {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String depCfg = "frontend";
    private String nameSpace = "test";
    private String replicaCount = "0";
    private String authToken = "";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeploymentVerifier(String apiURL, String bldCfg, String nameSpace, String replicaCount, String authToken) {
        this.apiURL = apiURL;
        this.depCfg = bldCfg;
        this.nameSpace = nameSpace;
        this.replicaCount = replicaCount;
        this.authToken = authToken;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApiURL() {
		return apiURL;
	}

	public String getDepCfg() {
		return depCfg;
	}

	public String getNameSpace() {
		return nameSpace;
	}
	
	public String getReplicaCount() {
		return replicaCount;
	}
	
	public String getAuthToken() {
		return authToken;
	}

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	System.setProperty(ICapability.OPENSHIFT_BINARY_LOCATION, Constants.OC_LOCATION);
    	listener.getLogger().println("OpenShiftDeploymentVerifier in perform");
    	
    	// obtain auth token from defined spot in OpenShift Jenkins image
    	authToken = Auth.deriveAuth(authToken, listener);
    	
    	// get oc client (sometime REST, sometimes Exec of oc command
    	IClient client = new ClientFactory().create(apiURL, this);
    	
    	if (client != null) {
    		// seed the auth
        	client.setAuthorizationStrategy(new TokenAuthorizationStrategy(this.authToken));
        	
        	// explicitly set replica count, save that
        	int count = -1;
        	if (replicaCount.length() > 0)
        		count = Integer.parseInt(replicaCount);
        		
            						
        	// if the deployment config for this app specifies a desired replica count of 
        	// of greater than zero, let's also confirm the deployment occurs;
        	// first, get the deployment config
        	Map<String,IDeploymentConfig> dcs = Deployment.getDeploymentConfigs(client, nameSpace, listener);
        	boolean dcWithReplicas = false;
        	boolean haveDep = false;
        	boolean scaledAppropriately = false;
        	for (String key : dcs.keySet()) {
        		if (key.equals(depCfg)) {
					haveDep = true;
        			IDeploymentConfig dc = dcs.get(key);
        			
        			// if replicaCount not set, get it from config
        			if (count == -1)
        				count = dc.getReplicas();
        			
        			if (count > 0) {
        				dcWithReplicas = true;
        				
        				listener.getLogger().println("OpenShiftDeploymentVerifier checking if deployment out there");
        				
        				// confirm the deployment has kicked in from completed build;
        	        	// in testing with the jenkins-ci sample, the initial deploy after
        	        	// a build is kinda slow ... gotta wait more than one minute
        				long currTime = System.currentTimeMillis();
        				IReplicationController rc = null;
        				while (System.currentTimeMillis() < (currTime + 180000)) {
        					Map<String, IReplicationController> rcs = Deployment.getDeployments(client, nameSpace, listener);
        					for (String rckey : rcs.keySet()) {
        						if (rckey.startsWith(depCfg)) {
        							rc = rcs.get(rckey);
        							listener.getLogger().println("OpenShiftDeploymentVerifier found rc " + rckey + ":  " + rc);
        							break;
        							
        						}
        					}
        					
        					if (rc != null) {
        						listener.getLogger().println("OpenShiftDeploymentVerifier current count " + rc.getCurrentReplicaCount() + " desired count " + rc.getDesiredReplicaCount());
    			        		if (rc.getCurrentReplicaCount() >= rc.getDesiredReplicaCount()) {
    			        			scaledAppropriately = true;
    			        			break;
    			        		}
    							
        					}
        					
        					listener.getLogger().println("OpenShiftDeploymentVerifier don't have rc at right replica count, wait a second, check again, rc = " + rc);
        					
			        		try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
							}

        				}
        			} else {
            			//TODO we could check if 0 cfg reps are in fact 0, but I believe
            			// there is currently not a given that we'll scale down quickly
    					listener.getLogger().println("OpenShiftDeploymentVerifier dc has zero replicas, moving on");
        			}
        			
        		}
        		
        		if (haveDep)
        			break;
        	}
        	
        	
        	if (dcWithReplicas && scaledAppropriately )
        		return true;
        	
        	if (!dcWithReplicas)
        		return true;
    				
        		
        		
    	} else {
    		listener.getLogger().println("OpenShiftDeploymentVerifier could not get oc client");
    		return false;
    	}

    	return false;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftDeploymentVerifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the various fields.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set apiURL");
            return FormValidation.ok();
        }

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set depCfg");
            return FormValidation.ok();
        }

        public FormValidation doCheckNameSpace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set nameSpace");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckReplicaCount(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.ok();
            try {
            	Integer.decode(value);
            } catch (NumberFormatException e) {
            	return FormValidation.error("Please specify an integer for replicaCount");
            }
            return FormValidation.ok();
        }


        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Verify deployments in OpenShift";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
            save();
            return super.configure(req,formData);
        }

    }

	@Override
	public boolean allowCertificate(X509Certificate[] chain) {
		return true;
	}

	@Override
	public boolean allowHostname(String hostname, SSLSession session) {
		return true;
	}
}

