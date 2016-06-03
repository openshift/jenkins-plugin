package com.openshift.jenkins.plugins.pipeline;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.Map;

public class OpenShiftDeployCanceller extends OpenShiftBasePostAction {

	protected static final String DISPLAY_NAME = "Cancel OpenShift Deployment";
	
	public String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	protected final String depCfg;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeployCanceller(String apiURL, String depCfg, String namespace, String authToken, String verbose) {
    	super(apiURL, namespace, authToken, verbose);
        this.depCfg = depCfg;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getDepCfg() {
		return depCfg;
	}

	public String getDepCfg(Map<String,String> overrides) {
		return getOverride(getDepCfg(), overrides);
	}
	
	@Override
	public boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {

    	listener.getLogger().println(String.format(MessageConstants.START_DEPLOY_RELATED_PLUGINS, DISPLAY_NAME, getDepCfg(overrides), getNamespace(overrides)));		
		
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
    		IDeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, getDepCfg(overrides), getNamespace(overrides));
    		
    		if (dc == null) {
		    	listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG, DISPLAY_NAME, getDepCfg(overrides)));
    			return false;
    		}
			
    		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
			IReplicationController rc = getLatestReplicationController(dc, getNamespace(overrides), client, chatty ? listener : null);
				
			if (rc != null) {
				String state = this.getReplicationControllerState(rc);
        		if (state.equalsIgnoreCase("Failed") || state.equalsIgnoreCase("Complete") || state.equalsIgnoreCase("Cancelled")) {
        	    	listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_CANCEL_GOOD_NOOP, rc.getName(), state));
        			return true;
        		}
        		
        		rc.setAnnotation("openshift.io/deployment.cancelled", "true");
        		rc.setAnnotation("openshift.io/deployment.status-reason", "The deployment was cancelled by the user");
				
        		client.update(rc);
        		
    	    	listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_CANCEL_GOOD_DIDIT, rc.getName()));
        		return true;
			} else {
				if (dc.getLatestVersionNumber() > 0) {
					listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_CANCEL_BAD_NO_REPCTR, getDepCfg(overrides)));
					return false;
				} else {
					listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_CANCEL_GOOD_NO_REPCTR, getDepCfg(overrides)));
					return true;
				}
			}					
    		
    	} else {
	    	return false;
    	}
		
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
        	return ParamVerify.doCheckApiURL(value);
        }

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckDepCfg(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckNamespace(value);
        }
        
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return DISPLAY_NAME;
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

