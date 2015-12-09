package com.openshift.jenkins.plugins.pipeline;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import javax.servlet.ServletException;

import java.io.IOException;

public class OpenShiftDeployCanceller extends OpenShiftBasePostAction {

    protected String depCfg = "frontend";

    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeployCanceller(String apiURL, String depCfg, String namespace, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
        this.depCfg = depCfg;
    }

	public String getDepCfg() {
		return depCfg;
	}

	
	@Override
	protected boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env, Result result) {
		boolean chatty = Boolean.parseBoolean(verbose);
		// in theory, success should mean that the builds completed successfully,
		// at this time, we'll scan the builds either way to clean up rogue builds
		if (result != null &&result.isWorseThan(Result.SUCCESS)) {
			if (chatty)
				listener.getLogger().println("\nOpenShiftDeployCanceller build did not succeed");
		} else {
			if (chatty)
				listener.getLogger().println("\nOpenShiftDeployCanceller build succeeded / result " + result);			
		}

    	// get oc client (sometime REST, sometimes Exec of oc command
    	IClient client = new ClientFactory().create(apiURL, auth);
    	
    	if (client != null) {
    		// seed the auth
        	client.setAuthorizationStrategy(bearerToken);
        	
        	if (depCfg == null)
        		depCfg = "frontend";
			
    		IDeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, namespace);
    		
    		if (dc == null) {
    			listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentCanceller no valid deployment config found for " + depCfg);
    			return false;
    		}

			if (chatty) listener.getLogger().println("\nOpenShiftDeploymentCanceller checking if deployment out there for " + depCfg);
			
			int latestVersion = dc.getLatestVersionNumber();
			IReplicationController rc = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg + "-" + latestVersion, namespace);
				
			if (rc != null) {
				String state = rc.getAnnotation("openshift.io/deployment.phase");
        		if (state.equalsIgnoreCase("Failed") || state.equalsIgnoreCase("Complete")) {
        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller deployment " + rc.getName() + " done");
        			return true;
        		}
        		
        		rc.setAnnotation("openshift.io/deployment.cancelled", "true");
        		rc.setAnnotation("openshift.io/deployment.status-reason", "The deployment was cancelled by the user");
				
        		client.update(rc);
        		
			} else {
    			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller could not get resource controller with key:  " + depCfg + "-" + latestVersion);
				return false;
			}					
    		
    	}			
		listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentCanceller completed");
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

