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
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftDeleterList;

public class OpenShiftDeleterList extends OpenShiftBaseStep implements IOpenShiftDeleterList {

	protected final String types;
	protected final String keys;
	
    @DataBoundConstructor public OpenShiftDeleterList(String types, String keys) {
    	this.types = types;
    	this.keys = keys;
	}
    

	public String getTypes() {
		return types;
	}
	
	public String getKeys() {
		return keys;
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

    private static final Logger LOGGER = Logger.getLogger(OpenShiftDeleterList.class.getName());


	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftDeleterListExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openShiftDeleteResourceByKey";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("types") || !arguments.containsKey("keys"))
            	throw new IllegalArgumentException("need to specify types, keys, and values");
            OpenShiftDeleterList step = new OpenShiftDeleterList(arguments.get("types").toString(), arguments.get("keys").toString());
            ParamVerify.updateDSLBaseStep(arguments, step);
            return step;
        }
    }


}
