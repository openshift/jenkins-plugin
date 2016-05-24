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

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftCreator;

public class OpenShiftCreator extends OpenShiftBaseStep implements IOpenShiftCreator {

    protected final String jsonyaml;
    
    @DataBoundConstructor public OpenShiftCreator(String jsonyaml) {
    	this.jsonyaml = jsonyaml;
	}

	@Override
	public String getJsonyaml() {
		return jsonyaml;
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

    private static final Logger LOGGER = Logger.getLogger(OpenShiftCreator.class.getName());


	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftCreatorExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openShiftCreateResource";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
        	Object json = arguments.get("json");
        	Object yaml = arguments.get("yaml");
            if (json == null && yaml == null)
            	throw new IllegalArgumentException("need to specify json or yaml");
            OpenShiftCreator step = null;
            if (json != null)
            	step = new OpenShiftCreator(json.toString());
            else 
            	step = new OpenShiftCreator(yaml.toString());
            ParamVerify.updateDSLBaseStep(arguments, step);
            return step;
        }
    }


}
