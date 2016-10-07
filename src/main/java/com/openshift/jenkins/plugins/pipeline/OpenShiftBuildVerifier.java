package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuildVerifier;
import hudson.Extension;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;


public class OpenShiftBuildVerifier extends TimedOpenShiftBaseStep implements IOpenShiftBuildVerifier {

    protected final String bldCfg;
    protected final String checkForTriggeredDeployments;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuildVerifier(String apiURL, String bldCfg, String namespace, String authToken, String verbose, String checkForTriggeredDeployments, String waitTime, String waitUnit) {
        super(apiURL, namespace, authToken, verbose, waitTime, waitUnit);
        this.bldCfg = bldCfg;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

    public String getBldCfg() {
        return bldCfg;
    }

    public String getCheckForTriggeredDeployments() {
        return checkForTriggeredDeployments;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftBuildVerifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends TimedBuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        protected long getStaticDefaultWaitTime() {
            return GlobalConfig.DEFAULT_BUILD_VERIFY_WAIT;
        }

        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckBldCfg(value);
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return DISPLAY_NAME;
        }


    }


}

