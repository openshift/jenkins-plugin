package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftApiObjHandler;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftExec;
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
import java.util.List;

public class OpenShiftExec extends TimedOpenShiftBaseStep implements IOpenShiftExec, IOpenShiftApiObjHandler {

    protected final String pod;
    protected final String container;
    protected final String command;
    protected final List<Argument> arguments;

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

    @DataBoundConstructor
    public OpenShiftExec(String apiURL, String namespace, String authToken, String verbose, String pod, String container, String command, List<Argument>arguments, String waitTime) {
        super( apiURL, namespace, authToken, verbose, waitTime );
        this.pod = pod.trim();
        this.container = container.trim();
        this.command = command;
        this.arguments = arguments;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> implements IOpenShiftPluginDescriptorValidation {
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

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            GlobalConfig.setBuildWait(formData.getLong("wait"));
            save();
            return super.configure(req,formData);
        }

    }


}
