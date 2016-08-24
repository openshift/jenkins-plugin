package com.openshift.jenkins.plugins.pipeline;
import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuilder;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;


public class OpenShiftBuilder extends OpenShiftBaseStep implements IOpenShiftBuilder {
	
    protected final String bldCfg;
    protected final String commitID;
    protected final String buildName;
    protected final String showBuildLogs;
    protected final String checkForTriggeredDeployments;
    protected final String waitTime;
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuilder(String apiURL, String bldCfg, String namespace, String authToken, String verbose, String commitID, String buildName, String showBuildLogs, String checkForTriggeredDeployments, String waitTime) {
    	super(apiURL, namespace, authToken, verbose);
        this.bldCfg = bldCfg;
        this.commitID = commitID;
        this.buildName = buildName;
        this.showBuildLogs = showBuildLogs;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
        this.waitTime = waitTime;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getCommitID() {
		return commitID;
	}
	
	public String getBuildName() {
		return buildName;
	}

	public String getShowBuildLogs() {
		return showBuildLogs;
	}
	
	public String getBldCfg() {
		return bldCfg;
	}
	
	public String getCheckForTriggeredDeployments() {
		return checkForTriggeredDeployments;
	}
	
	public String getWaitTime() {
		return waitTime;
	}
	
	public String getWaitTime(Map<String,String> overrides) {
		String val = getOverride(getWaitTime(), overrides);
		if (val.length() > 0)
			return val;
		return Long.toString(getDescriptor().getWait());
	}
	
	// Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private long wait = GlobalConfig.BUILD_WAIT;
    	
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
        
        public FormValidation doCheckWaitTime(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckCheckForWaitTime(value);
        }
        
        public FormValidation doCheckAuthToken(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckToken(value);
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
        	GlobalConfig.setBuildWait(wait);
            save();
            return super.configure(req,formData);
        }

    }

}

