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

import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ISSLCertificateCallback;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.ICapability;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import javax.net.ssl.SSLSession;
import javax.servlet.ServletException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * OpenShift {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link OpenShiftDeployer} is created. The created
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
public class OpenShiftDeployer extends Builder implements ISSLCertificateCallback {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String depCfg = "frontend";
    private String nameSpace = "test";
    private String authToken = "";
    private String verbose = "false";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeployer(String apiURL, String depCfg, String nameSpace, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.depCfg = depCfg;
        this.nameSpace = nameSpace;
        this.authToken = authToken;
        this.verbose = verbose;
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
	
	public String getAuthToken() {
		return authToken;
	}

    public String getVerbose() {
		return verbose;
	}

	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
    	System.setProperty(ICapability.OPENSHIFT_BINARY_LOCATION, Constants.OC_LOCATION);
    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftDeployer in perform for " + depCfg);
    	
    	// obtain auth token from defined spot in OpenShift Jenkins image
    	authToken = Auth.deriveAuth(authToken, listener, chatty);
    	    	
    	// get oc client (sometime REST, sometimes Exec of oc command
    	IClient client = new ClientFactory().create(apiURL, this);
    	
    	if (client != null) {
    		// seed the auth
        	client.setAuthorizationStrategy(new TokenAuthorizationStrategy(this.authToken));
        	
        	
        	// do the oc deploy ... may need to retry
        	long currTime = System.currentTimeMillis();
        	boolean deployDone = false;
        	while (System.currentTimeMillis() < (currTime + 60000)) {
        		DeploymentConfig dcImpl = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, nameSpace);
				int latestVersion = -1;
        		if (dcImpl != null) {
        			ModelNode dcNode = dcImpl.getNode();
        			if (chatty)
        				listener.getLogger().println("\nOpenShiftDeployer dc json " + dcNode.asString());
        			ModelNode dcStatus = dcNode.get("status");
        			if (chatty)
        				listener.getLogger().println("\nOpenShiftDeployer status json " + dcStatus.asString());
        			ModelNode dcLatestVersion = dcStatus.get("latestVersion");
        			if (chatty)
        				listener.getLogger().println("\nOpenShiftDeployer version json " + dcStatus.asString());
        			if (dcLatestVersion != null) {
        				try {
        					latestVersion = dcLatestVersion.asInt();
        				} catch (Throwable t) {
        					
        				}
        			}
        			
        			// oc deploy gets the rc after the dc prior to putting the dc;
        			// we'll do the same
        			if (latestVersion != -1) {
        				IReplicationController rc = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg + "-" + latestVersion, nameSpace);
        				
        				// now lets update the latest version of the dc
        				dcLatestVersion.set(latestVersion + 1);
        				
        				// and now lets PUT the updated dc
        				URL url = null;
    					try {
							url = new URL(apiURL + "/oapi/v1/namespaces/"+nameSpace+"/deploymentconfigs/" + depCfg);
						} catch (MalformedURLException e) {
							e.printStackTrace(listener.getLogger());
							return false;
						}
    		    		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
    		    				null, "application/json", null, this, null, null);
    		    		urlClient.setAuthorizationStrategy(new TokenAuthorizationStrategy(authToken));
    		    		String response = null;
    		    		try {
    		    			response = urlClient.put(url, 10 * 1000, dcImpl);
    		    			listener.getLogger().println("\n\nOpenShiftDeployer REST PUT response " + response);
    		    			deployDone = true;
    		    		} catch (SocketTimeoutException e1) {
    		    			if (chatty) e1.printStackTrace(listener.getLogger());
    		    		} catch (HttpClientException e1) {
    		    			if (chatty) e1.printStackTrace(listener.getLogger());
    		    		}
    					
    					if (deployDone) {
    						break;
    					} else {
    						if (chatty)
    	        				listener.getLogger().println("\nOpenShiftDeployer wait 10 seconds, then try oc scale again");
    						try {
    							Thread.sleep(10000);
    						} catch (InterruptedException e) {
    						}
    					}
        				
        			}
        		}
        	}
        	
        	if (!deployDone) {
        		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeployer could not get oc deploy executed");
        		return false;
        	}
        	
        	
    	} else {
    		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeployer could not get oc client");
    		return false;
    	}

    	listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeployer exit successfully");
    	return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftDeployer}. Used as a singleton.
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

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Start a deployment in OpenShift";
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

