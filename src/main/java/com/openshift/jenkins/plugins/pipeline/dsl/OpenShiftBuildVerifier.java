package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuildVerifier;
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

public class OpenShiftBuildVerifier extends TimedOpenShiftBaseStep implements IOpenShiftBuildVerifier {

    protected final String bldCfg;
    protected String checkForTriggeredDeployments;

    @DataBoundConstructor
    public OpenShiftBuildVerifier(String bldCfg) {
        this.bldCfg = bldCfg;
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

    @Override
    public String getBldCfg() {
        return bldCfg;
    }

    @Override
    public String getCheckForTriggeredDeployments() {
        return checkForTriggeredDeployments;
    }

    @DataBoundSetter
    public void setCheckForTriggeredDeployments(String checkForTriggeredDeployments) {
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftBuildVerifierExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftVerifyBuild";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("buildConfig") && !arguments.containsKey("bldCfg"))
                throw new IllegalArgumentException("need to specify buildConfig");
            Object bldCfg = arguments.get("buildConfig");
            if (bldCfg == null || bldCfg.toString().length() == 0)
                bldCfg = arguments.get("bldCfg");
            if (bldCfg == null || bldCfg.toString().length() == 0)
                throw new IllegalArgumentException("need to specify buildConfig");
            OpenShiftBuildVerifier step = new OpenShiftBuildVerifier(bldCfg.toString());
            if (arguments.containsKey("checkForTriggeredDeployments")) {
                Object checkForTriggeredDeployments = arguments.get("checkForTriggeredDeployments");
                if (checkForTriggeredDeployments != null) {
                    step.setCheckForTriggeredDeployments(checkForTriggeredDeployments.toString());
                }
            }

            ParamVerify.updateTimedDSLBaseStep(arguments, step);
            return step;
        }
    }

}
