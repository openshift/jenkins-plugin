package com.openshift.jenkins.plugins.pipeline;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IBuild;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class OpenShiftBuildVerifier extends OpenShiftBaseStep {
	
	protected final static String DISPLAY_NAME = "Verify OpenShift Build";
	
    protected String bldCfg = "frontend";
    protected String checkForTriggeredDeployments = "false";
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuildVerifier(String apiURL, String bldCfg, String namespace, String authToken, String verbose, String checkForTriggeredDeployments) {
        this.apiURL = apiURL;
        this.bldCfg = bldCfg;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
    }

	public String getBldCfg() {
		return bldCfg;
	}
    
	public String getCheckForTriggeredDeployments() {
		return checkForTriggeredDeployments;
	}
	
	protected List<String> getBuildIDs(IClient client) {
		List<IBuild> blds = client.list(ResourceKind.BUILD, namespace);
		List<String> ids = new ArrayList<String>();
		for (IBuild bld : blds) {
			if (bld.getName().startsWith(bldCfg)) {
				ids.add(bld.getName());
			}
		}
		return ids;
	}
	
	protected String getLatestBuildID(List<String> ids) {
		String bldId = null;
		if (ids.size() > 0) {
			Collections.sort(ids);
			bldId = ids.get(ids.size() - 1);
		}
		return bldId;
	}
	
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env) {
		boolean chatty = Boolean.parseBoolean(verbose);
		boolean checkDeps = Boolean.parseBoolean(checkForTriggeredDeployments);
    	listener.getLogger().println(String.format("\n\nStarting the \"%s\" step with build config \"%s\" from the project \"%s\".", DISPLAY_NAME, bldCfg, namespace));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME);
    	
    	if (client != null) {
			if (chatty)
				listener.getLogger().println("\nOpenShiftBuildVerifier wait " + getDescriptor().getWait());
			List<String> ids = getBuildIDs(client);
			
			String bldId = getLatestBuildID(ids);
			
			listener.getLogger().println(String.format("  Verifying build \"%s\" and waiting for build completion %s...", bldId, checkDeps ? "followed by a new deployment" : ""));			
				
			return this.verifyBuild(System.currentTimeMillis(), getDescriptor().getWait(), client, bldCfg, bldId, namespace, chatty, listener, DISPLAY_NAME, checkDeps);
    				        		
    	} else {
	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; a client connection to \"%s\" could not be obtained.", DISPLAY_NAME, apiURL));
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
     * Descriptor for {@link OpenShiftBuildVerifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private long wait = 60000;
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

        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckBldCfg(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckNamespace(value);
        }
        
        public FormValidation doCheckCheckForTriggeredDeployments(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckCheckForTriggeredDeployments(value);
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
        
        public long getWait() {
        	return wait;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
        	wait = formData.getLong("wait");
            save();
            return super.configure(req,formData);
        }

    }


}

