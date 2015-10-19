package com.openshift.openshiftjenkinsbuildutils;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;

import jenkins.tasks.SimpleBuildStep;

/**
 * OpenShift {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link OpenShiftDeployCanceller} is created. The created
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
public class OpenShiftDeployCanceller extends Recorder implements SimpleBuildStep, Serializable {
	
    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String namespace = "test";
    private String authToken = "";
    private String verbose = "false";
    private String depCfg ="frontend";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeployCanceller(String apiURL, String namespace, String authToken, String verbose, String deployConfig) {
        this.apiURL = apiURL;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
        this.depCfg = deployConfig;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApiURL() {
		return apiURL;
	}

	public String getNamespace() {
		return namespace;
	}
	
	public String getAuthToken() {
		return authToken;
	}
	
    public String getVerbose() {
		return verbose;
	}

	public void setVerbose(String verbose) {
		this.verbose = verbose;
	}

    public void setApiURL(String apiURL) {
		this.apiURL = apiURL;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}


	public String getDepCfg() {
		return depCfg;
	}

	public void setDepCfg(String deployConfig) {
		this.depCfg = deployConfig;
	}

	// Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    
    
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return true;
	}
	
	protected boolean coreLogic(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
		Result result = build.getResult();
		
		// in theory, success should mean that the builds completed successfully,
		// at this time, we'll scan the builds either way to clean up rogue builds
		if (result.isWorseThan(Result.SUCCESS)) {
			if (chatty)
				listener.getLogger().println("\nOpenShiftDeployCanceller build did not succeed");
		} else {
			if (chatty)
				listener.getLogger().println("\nOpenShiftDeployCanceller build succeeded");			
		}

    	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, chatty));
    	Auth auth = Auth.createInstance(chatty ? listener : null);
		
    	// get oc client (sometime REST, sometimes Exec of oc command
    	IClient client = new ClientFactory().create(apiURL, auth);
    	
    	if (client != null) {
    		// seed the auth
        	client.setAuthorizationStrategy(bearerToken);
			
    		DeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, namespace);
    		
    		if (dc == null) {
    			listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentCanceller no valid deployment config found for " + depCfg);
    			return false;
    		}

			if (chatty) listener.getLogger().println("\nOpenShiftDeploymentCanceller checking if deployment out there for " + depCfg);
			
			// confirm the deployment has kicked in from completed build;
        	// in testing with the jenkins-ci sample, the initial deploy after
        	// a build is kinda slow ... gotta wait more than one minute
			long currTime = System.currentTimeMillis();
			while (System.currentTimeMillis() < (currTime + 180000)) {
				int latestVersion = -1;
				try {
					latestVersion = Deployment.getDeploymentConfigLatestVersion(dc, chatty ? listener : null).asInt();
				} catch (Throwable t) {
					latestVersion = 0;
				}
				ReplicationController rc = null;
				try {
					rc = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg + "-" + latestVersion, namespace);
				} catch (Throwable t) {
					
				}
					
				if (rc != null) {
					String state = Deployment.getReplicationControllerState(rc, chatty ? listener : null);
	        		if (state.equalsIgnoreCase("Failed") || state.equalsIgnoreCase("Complete")) {
	        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller deployment " + rc.getName() + " done");
	        			return false;
	        		}
	        		
	        		Deployment.updateReplicationControllerAnnotiation(rc, listener, "openshift.io/deployment.cancelled", "true");
	        		Deployment.updateReplicationControllerAnnotiation(rc, listener, "openshift.io/deployment.status-reason", "The deployment was cancelled by the user");
					
	        		
	        		// create stream and do put
	    	    	URL url = null;
	    	    	try {
	    				url = new URL(apiURL + "/oapi/v1/namespaces/"+namespace+"/replicationcontrollers/" + depCfg + "-" + latestVersion);
	    			} catch (MalformedURLException e1) {
	    				if (chatty) e1.printStackTrace(listener.getLogger());
	        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller error constructing URL");
	    				return false;
	    			}
	    			UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
	    					null, "application/json", null, auth, null, null);
	    			urlClient.setAuthorizationStrategy(bearerToken);
	    			String response = null;
	    			try {
	    				response = urlClient.put(url, 2 * 60 * 1000, rc);
	    				if (chatty)
	    					listener.getLogger().println("\nOpenShiftDeployCanceller response " + response);
	    			} catch (SocketTimeoutException e1) {
	    				if (chatty) e1.printStackTrace(listener.getLogger());
	        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller deployment error connecting to HTTP resource");
	    				return false;
	    			} catch (HttpClientException e1) {
	    				if (chatty) e1.printStackTrace(listener.getLogger());
	        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller deployment error from HTTP request");
	    				return false;
	    			}
	    			// if optimistic update conflict, retry
					if (response != null && response.contains("Conflict")) {
		        		try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					} else {
	        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller deployment unexpected result from replication controller update:  " + response);
						return false;
					}
				} else {
        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller could not get resource controller with key:  " + depCfg + "-" + latestVersion);
					return false;
				}					

			}
    		
    	}			
		return true;
		
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		coreLogic(null, launcher, listener);
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		return coreLogic(build, launcher, listener);
	}



	/**
     * Descriptor for {@link OpenShiftDeployCanceller}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
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

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set namespace");
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
            return "Cancel deployments in OpenShift";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
            save();
            return super.configure(req,formData);
        }

    }


}

