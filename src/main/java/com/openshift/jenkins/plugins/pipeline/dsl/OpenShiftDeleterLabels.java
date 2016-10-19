package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
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
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftDeleterLabels;

public class OpenShiftDeleterLabels extends OpenShiftBaseStep implements IOpenShiftDeleterLabels {

	protected final String types;
	protected final String keys;
	protected final String values;
	
    @DataBoundConstructor public OpenShiftDeleterLabels(String types, String keys, String values) {
    	this.types = types != null ? types.trim() : null;
    	this.keys = keys != null ? keys.trim() : null;
    	this.values = values != null ? values.trim() : null;
	}
    

	public String getTypes() {
		return types;
	}
	
	public String getKeys() {
		return keys;
	}
	
	public String getValues() {
		return values;
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

    private static final Logger LOGGER = Logger.getLogger(OpenShiftDeleterLabels.class.getName());


	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            super(OpenShiftDeleterLabelsExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftDeleteResourceByLabels";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("types") || !arguments.containsKey("keys") || !arguments.containsKey("values"))
            	throw new IllegalArgumentException("need to specify types, keys, and values");
            OpenShiftDeleterLabels step = new OpenShiftDeleterLabels(arguments.get("types").toString(), arguments.get("keys").toString(), arguments.get("values").toString());
            ParamVerify.updateDSLBaseStep(arguments, step);
            return step;
        }
    }


}
