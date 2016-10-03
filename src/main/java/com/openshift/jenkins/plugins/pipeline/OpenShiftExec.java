package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftApiObjHandler;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftExec;
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
import java.util.Map;

public class OpenShiftExec extends OpenShiftBaseStep implements IOpenShiftExec, IOpenShiftApiObjHandler {

    protected final String pod;
    protected final String container;
    protected final String command;
    protected final List<Argument> arguments;
    protected final String waitTime;

    public String getPod() {
        return pod;
    }

    public String getContainer() {
        return container;
    }

    public String getCommand() {
        return command;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public String getWaitTime() {
        return waitTime;
    }

    @DataBoundConstructor
    public OpenShiftExec(String apiURL, String namespace, String authToken, String verbose, String pod, String container, String command, List<Argument>arguments, String waitTime) {
        super( apiURL, namespace, authToken, verbose );
        this.pod = pod.trim();
        this.container = container.trim();
        this.command = command;
        this.arguments = arguments;
        this.waitTime = waitTime.trim();
    }

    // TODO: Copied from other class boilerplate, but I don't think this works. We are passing a user specified timeout string as a map key.
    public String getWaitTime(Map<String,String> overrides) {
        String val = getOverride(getWaitTime(), overrides);
        if (val.length() > 0)
            return val;
        return Long.toString(GlobalConfig.getBuildWait());
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private long wait = GlobalConfig.BUILD_WAIT;

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

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

        public FormValidation doCheckWaitTime(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckCheckForWaitTime(value);
        }

        public FormValidation doCheckAuthToken(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckToken(value);
        }

        public FormValidation doCheckPod(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckCommand(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

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
