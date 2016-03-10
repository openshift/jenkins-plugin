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

import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IBuild;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class OpenShiftBuildVerifier extends OpenShiftBaseStep {
	
	protected final static String DISPLAY_NAME = "Verify OpenShift Build";
	
    protected final String bldCfg;
    protected final String checkForTriggeredDeployments;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuildVerifier(String apiURL, String bldCfg, String namespace, String authToken, String verbose, String checkForTriggeredDeployments) {
    	super(apiURL, namespace, authToken, verbose);
        this.bldCfg = bldCfg;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
    }

	public String getBldCfg() {
		return bldCfg;
	}
	
	public String getBldCfg(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("bldCfg"))
			return overrides.get("bldCfg");
		else return getBldCfg();
	}

	public String getCheckForTriggeredDeployments() {
		return checkForTriggeredDeployments;
	}
	
	public String getCheckForTriggeredDeployments(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("checkForTriggeredDeployments"))
			return overrides.get("checkForTriggeredDeployments");
		else return getCheckForTriggeredDeployments();
	}

	protected List<String> getBuildIDs(IClient client, Map<String,String> overrides) {
		List<IBuild> blds = client.list(ResourceKind.BUILD, getNamespace(overrides));
		List<String> ids = new ArrayList<String>();
		for (IBuild bld : blds) {
			if (bld.getName().startsWith(getBldCfg(overrides))) {
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
			EnvVars env, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
		boolean checkDeps = Boolean.parseBoolean(checkForTriggeredDeployments);
    	listener.getLogger().println(String.format(MessageConstants.START_BUILD_RELATED_PLUGINS, DISPLAY_NAME, getBldCfg(overrides), getNamespace(overrides)));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
			if (chatty)
				listener.getLogger().println("\nOpenShiftBuildVerifier wait " + getDescriptor().getWait());
			List<String> ids = getBuildIDs(client, overrides);
			
			String bldId = getLatestBuildID(ids);
			
			if (!checkDeps)
				listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD_STARTED_ELSEWHERE, bldId));
			else
				listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD_STARTED_ELSEWHERE_PLUS_DEPLOY, bldId));
				
			return this.verifyBuild(System.currentTimeMillis(), getDescriptor().getWait(), client, getBldCfg(overrides), bldId, getNamespace(overrides), chatty, listener, DISPLAY_NAME, checkDeps);
    				        		
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

