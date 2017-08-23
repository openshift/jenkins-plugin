package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftServiceVerifier;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

public class OpenShiftServiceVerifier extends OpenShiftBaseStep implements
        IOpenShiftServiceVerifier {

    protected final String svcName;
    protected String retryCount;

    @DataBoundConstructor
    public OpenShiftServiceVerifier(String svcName) {
        this.svcName = svcName != null ? svcName.trim() : null;
    }

    public String getSvcName() {
        return svcName;
    }

    public String getSvcName(Map<String, String> overrides) {
        return getSvcName();
    }

    public String getRetryCount() {
        return retryCount;
    }

    public String getRetryCount(Map<String, String> overrides) {
        String val = getOverride(getRetryCount(), overrides);
        if (val.length() > 0)
            return val;
        return Integer.toString(GlobalConfig.getServiceVerifyRetry());
    }

    @DataBoundSetter
    public void setRetryCount(String retryCount) {
        this.retryCount = retryCount != null ? retryCount.trim() : null;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        return true;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(
            AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return null;
    }

    private static final Logger LOGGER = Logger
            .getLogger(OpenShiftServiceVerifier.class.getName());

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl
            implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            super(OpenShiftServiceVerifierExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftVerifyService";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("serviceName")
                    && !arguments.containsKey("svcName"))
                throw new IllegalArgumentException(
                        "need to specify serviceName");
            Object svcName = arguments.get("serviceName");
            if (svcName == null || svcName.toString().trim().length() == 0)
                svcName = arguments.get("svcName");
            if (svcName == null || svcName.toString().trim().length() == 0)
                throw new IllegalArgumentException(
                        "need to specify serviceName");
            OpenShiftServiceVerifier step = new OpenShiftServiceVerifier(
                    svcName.toString());

            if (arguments.containsKey("retryCount")) {
                Object retryCount = arguments.get("retryCount");
                if (retryCount != null)
                    step.setRetryCount(retryCount.toString());
            }

            ParamVerify.updateDSLBaseStep(arguments, step);
            return step;
        }
    }

}
