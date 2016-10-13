package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftApiObjHandler;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftExec;
import hudson.Extension;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

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
    public OpenShiftExec(String apiURL, String namespace, String authToken, String verbose, String pod, String container, String command, List<Argument>arguments, String waitTime, String waitUnit) {
        super( apiURL, namespace, authToken, verbose, waitTime, waitUnit );
        this.pod = pod != null ? pod.trim() : null;
        this.container = container != null ? container.trim() : null;
        this.command = command != null ? command.trim() : null;
        this.arguments = arguments;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends TimedBuildStepDescriptor<Builder> {

        public FormValidation doCheckPod(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckCommand(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public String getDisplayName() {
            return DISPLAY_NAME;
        }


        @Override
        protected long getStaticDefaultWaitTime() {
            return GlobalConfig.DEFAULT_EXEC_WAIT;
        }

    }


}
