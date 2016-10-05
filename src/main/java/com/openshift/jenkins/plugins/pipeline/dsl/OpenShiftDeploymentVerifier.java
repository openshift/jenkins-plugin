package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftTimedStepDescriptor;
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
import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftDeploymentVerification;

public class OpenShiftDeploymentVerifier extends OpenShiftBaseStep implements IOpenShiftDeploymentVerification {
	
	protected final String depCfg;
	protected String replicaCount;
	protected String verifyReplicaCount;
	protected String waitTime;
	protected String waitUnit;

	@DataBoundConstructor public OpenShiftDeploymentVerifier(String depCfg) {
		this.depCfg = depCfg;
	}   

	public String getDepCfg() {
		return depCfg;
	}

	public String getReplicaCount() {
		return replicaCount;
	}
	
	@DataBoundSetter public void setReplicaCount(String replicaCount) {
		this.replicaCount = replicaCount;
	}
	
	public String getVerifyReplicaCount() {
		return verifyReplicaCount;
	}
	
	@DataBoundSetter public void setVerifyReplicaCount(String verifyReplicaCount) {
		this.verifyReplicaCount = verifyReplicaCount;
	}
	
	public String getWaitTime() {
		return waitTime;
	}

	public String getWaitUnit() {
		return waitUnit;
	}

	@DataBoundSetter public void setWaitUnit(String waitUnit) {
		this.waitUnit = waitUnit;
	}
	
	public String getWaitTime(Map<String, String> overrides) {
		String val = getOverride(getWaitTime(), overrides);
		if (val.length() > 0)
			return val;
		return GlobalConfig.getDeployVerifyWait() + GlobalConfig.getBuildVerifyWaitUnits();
	}
	
	@DataBoundSetter public void setWaitTime(String waitTime) {
		this.waitTime = waitTime;
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

	private static final Logger LOGGER = Logger.getLogger(OpenShiftDeploymentVerifier.class.getName());


	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl implements IOpenShiftTimedStepDescriptor {
		private String wait;
		private String waitUnit = "sec";

		public DescriptorImpl() {
			super(OpenShiftDeploymentVerifierExecution.class);
		}

		@Override
		public String getFunctionName() {
			return "openshiftVerifyDeployment";
		}

		@Override
		public String getDisplayName() {
			return DISPLAY_NAME;
		}

		@Override
		public Step newInstance(Map<String, Object> arguments) throws Exception {
			if (!arguments.containsKey("deploymentConfig") && !arguments.containsKey("depCfg"))
				throw new IllegalArgumentException("need to specify deploymentConfig");
			Object depCfg = arguments.get("deploymentConfig");
			if (depCfg == null || depCfg.toString().length() == 0)
				depCfg = arguments.get("depCfg");
			if (depCfg == null || depCfg.toString().length() == 0)
				throw new IllegalArgumentException("need to specify deploymentConfig");
			OpenShiftDeploymentVerifier step = new OpenShiftDeploymentVerifier(depCfg.toString());

			doFillWaitArguments(arguments);
			step.setWaitTime(wait);
			step.setWaitUnit(waitUnit);

			if (arguments.containsKey("replicaCount")) {
				Object replicaCount = arguments.get("replicaCount");
				if (replicaCount != null)
					step.setReplicaCount(replicaCount.toString());
			}
			if (arguments.containsKey("verifyReplicaCount")) {
				Object verifyReplicaCount = arguments.get("verifyReplicaCount");
				if (verifyReplicaCount != null)
					step.setVerifyReplicaCount(verifyReplicaCount.toString());
			}

			ParamVerify.updateDSLBaseStep(arguments, step);
			return step;
		}

		@Override
		public String getWait() {
			return wait;
		}

		@Override
		public void setWait(String wait) {
			this.wait = wait;
		}

		@Override
		public String getWaitUnit() {
			return waitUnit;
		}

		@Override
		public void setWaitUnit(String waitUnit) {
			this.waitUnit = waitUnit;
		}
	}
}
