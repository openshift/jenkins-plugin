package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptorValidation;
import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuilder;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;


public class OpenShiftBuilder extends TimedOpenShiftBaseStep implements IOpenShiftBuilder, ITimedOpenShiftPlugin {
	
    protected final String bldCfg;
    protected final String commitID;
    protected final String buildName;
    protected final String showBuildLogs;
    protected final String checkForTriggeredDeployments;
    protected final List<NameValuePair> envVars;
    

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuilder(String apiURL, String bldCfg, String namespace, List<NameValuePair> env, String authToken, String verbose, String commitID, String buildName, String showBuildLogs, String checkForTriggeredDeployments, String waitTime) {
    	super(apiURL, namespace, authToken, verbose, waitTime);
        this.bldCfg = bldCfg;
        this.envVars = env;
        this.commitID = commitID;
        this.buildName = buildName;
        this.showBuildLogs = showBuildLogs;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
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

    public List<NameValuePair>  getEnv() {
        return envVars;
    }
	
	public String getCheckForTriggeredDeployments() {
		return checkForTriggeredDeployments;
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
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> implements IOpenShiftPluginDescriptorValidation {

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


        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckBldCfg(value);
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
            // pull info from formData, set appropriate instance field
            // (which should have a getter), and call save().
        	GlobalConfig.setBuildWait( formData.getLong("wait") );
            save();
            return super.configure(req,formData);
        }

    }

}

