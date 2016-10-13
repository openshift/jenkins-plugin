package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftDeployer;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

public class OpenShiftDeployer extends TimedOpenShiftBaseStep implements IOpenShiftDeployer {
	
	protected final String depCfg;

    @DataBoundConstructor public OpenShiftDeployer(String depCfg) {
    	this.depCfg = depCfg != null ? depCfg.trim() : null;
	}   
    
	public String getDepCfg() {
		return depCfg;
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

    private static final Logger LOGGER = Logger.getLogger(OpenShiftDeployer.class.getName());


	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftDeployerExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftDeploy";
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
            if (depCfg == null || depCfg.toString().trim().length() == 0)
            	depCfg = arguments.get("depCfg");
            if (depCfg == null || depCfg.toString().trim().length() == 0)
            	throw new IllegalArgumentException("need to specify deploymentConfig");
            OpenShiftDeployer step = new OpenShiftDeployer(depCfg.toString());
            
            ParamVerify.updateTimedDSLBaseStep(arguments, step);
            return step;
        }
    }


}
