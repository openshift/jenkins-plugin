package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftScaler;
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

public class OpenShiftScaler extends TimedOpenShiftBaseStep implements
        IOpenShiftScaler {

    protected final String depCfg;
    protected final String replicaCount;
    protected String verifyReplicaCount;

    @DataBoundConstructor
    public OpenShiftScaler(String depCfg, String replicaCount) {
        this.depCfg = depCfg != null ? depCfg.trim() : null;
        this.replicaCount = replicaCount != null ? replicaCount.trim() : null;
    }

    public String getDepCfg() {
        return depCfg;
    }

    public String getReplicaCount() {
        return replicaCount;
    }

    public String getVerifyReplicaCount() {
        return verifyReplicaCount;
    }

    @DataBoundSetter
    public void setVerifyReplicaCount(String verifyReplicaCount) {
        this.verifyReplicaCount = verifyReplicaCount != null ? verifyReplicaCount
                .trim() : null;
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

    private static final Logger LOGGER = Logger.getLogger(OpenShiftScaler.class
            .getName());

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl
            implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            super(OpenShiftScalerExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftScale";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("deploymentConfig")
                    && !arguments.containsKey("depCfg"))
                throw new IllegalArgumentException(
                        "need to specify deploymentConfig");
            Object depCfg = arguments.get("deploymentConfig");
            if (depCfg == null || depCfg.toString().trim().length() == 0)
                depCfg = arguments.get("depCfg");
            if (depCfg == null || depCfg.toString().trim().length() == 0)
                throw new IllegalArgumentException(
                        "need to specify deploymentConfig");
            if (!arguments.containsKey("replicaCount"))
                throw new IllegalArgumentException(
                        "need to specif replicaCount");
            OpenShiftScaler step = new OpenShiftScaler(depCfg.toString(),
                    arguments.get("replicaCount").toString());

            if (arguments.containsKey("verifyReplicaCount")) {
                Object verifyReplicaCount = arguments.get("verifyReplicaCount");
                if (verifyReplicaCount != null)
                    step.setVerifyReplicaCount(verifyReplicaCount.toString());
            }

            ParamVerify.updateTimedDSLBaseStep(arguments, step);
            return step;
        }
    }

}
