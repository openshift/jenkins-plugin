package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuilder;
import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import hudson.Extension;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

public class OpenShiftBuilder extends TimedOpenShiftBaseStep implements
        IOpenShiftBuilder, ITimedOpenShiftPlugin {

    protected final String bldCfg;
    protected final String commitID;
    protected final String buildName;
    protected final String showBuildLogs;
    protected final String checkForTriggeredDeployments;
    protected final List<NameValuePair> envVars;

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuilder(String apiURL, String bldCfg, String namespace,
            List<NameValuePair> env, String authToken, String verbose,
            String commitID, String buildName, String showBuildLogs,
            String checkForTriggeredDeployments, String waitTime,
            String waitUnit) {
        super(apiURL, namespace, authToken, verbose, waitTime, waitUnit);
        this.bldCfg = bldCfg != null ? bldCfg.trim() : null;
        this.envVars = env;
        this.commitID = commitID != null ? commitID.trim() : null;
        this.buildName = buildName != null ? buildName.trim() : null;
        this.showBuildLogs = showBuildLogs != null ? showBuildLogs.trim()
                : null;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments != null ? checkForTriggeredDeployments
                .trim() : null;
    }

    // generically speaking, Jenkins will always pass in non-null field values.
    // However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get
    // null for the new fields. Hence,
    // we have introduced the generic convention (even for fields that existed
    // in the initial incarnations of the plugin)
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

    public List<NameValuePair> getEnv() {
        return envVars;
    }

    public String getCheckForTriggeredDeployments() {
        return checkForTriggeredDeployments;
    }

    /**
     * Descriptor for {@link OpenShiftBuilder}. Used as a singleton. The class
     * is marked as public so that it can be accessed from views.
     */
    @Extension
    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    public static final class DescriptorImpl extends
            TimedBuildStepDescriptor<Builder> {

        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckBldCfg(value);
        }

        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        protected long getStaticDefaultWaitTime() {
            return GlobalConfig.DEFAULT_BUILD_WAIT;
        }
    }

}
