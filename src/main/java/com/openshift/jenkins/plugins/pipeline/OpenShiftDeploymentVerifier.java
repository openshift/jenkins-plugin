package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftDeploymentVerification;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptorValidation;
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

public class OpenShiftDeploymentVerifier extends TimedOpenShiftBaseStep implements IOpenShiftDeploymentVerification {

    protected final String depCfg;
    protected final String replicaCount;
    protected final String verifyReplicaCount;

    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeploymentVerifier(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose, String verifyReplicaCount, String waitTime) {
    	super(apiURL, namespace, authToken, verbose, waitTime);
        this.depCfg = depCfg;
        this.replicaCount = replicaCount;
        this.verifyReplicaCount = verifyReplicaCount;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getDepCfg() {
		return depCfg;
	}

	public String getReplicaCount() {
		return replicaCount;
	}
	
	public String getVerifyReplicaCount() {
		return verifyReplicaCount;
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

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckDepCfg(value);
        }

        public FormValidation doCheckReplicaCount(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckReplicaCount(value);
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
        	GlobalConfig.setDeployVerifyWait(formData.getLong("wait"));
            save();
            return super.configure(req,formData);
        }

    }

}

