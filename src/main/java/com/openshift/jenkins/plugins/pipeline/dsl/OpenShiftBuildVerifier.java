package com.openshift.jenkins.plugins.pipeline.dsl;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuildVerifier;

public class OpenShiftBuildVerifier extends OpenShiftBaseStep implements IOpenShiftBuildVerifier {

    protected final String bldCfg;
    protected String checkForTriggeredDeployments;
    protected String waitTime;
    
    @DataBoundConstructor public OpenShiftBuildVerifier(String bldCfg) {
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

	@DataBoundSetter public void setCheckForTriggeredDeployments(String checkForTriggeredDeployments) {
		this.checkForTriggeredDeployments = checkForTriggeredDeployments;
	}
	
	@Override
	public String getWaitTime() {
		return waitTime;
	}

	@Override
	public String getWaitTime(Map<String, String> overrides) {
		String val = getOverride(getWaitTime(), overrides);
		if (val.length() > 0)
			return val;
		return "60000";
	}

	@DataBoundSetter public void setWaitTime(String waitTime) {
		this.waitTime = waitTime;
	}
	

    private static final Logger LOGGER = Logger.getLogger(OpenShiftBuilder.class.getName());


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
            if (arguments.containsKey("waitTime")) {
                Object waitTime = arguments.get("waitTime");
                if (waitTime != null) {
                    step.setWaitTime(waitTime.toString());
                }
            }
            ParamVerify.updateDSLBaseStep(arguments, step);
            return step;
        }
    }

}
