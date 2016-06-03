package com.openshift.jenkins.plugins.pipeline.dsl;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuilder;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;


public class OpenShiftBuilder extends OpenShiftBaseStep implements IOpenShiftBuilder {
	
    protected final String bldCfg;
    protected String commitID;
    protected String buildName;
    protected String showBuildLogs;
    protected String checkForTriggeredDeployments;
    protected String waitTime;
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuilder(String bldCfg) {
        this.bldCfg = bldCfg;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getCommitID() {
		return commitID;
	}
	
	@DataBoundSetter public void setCommitID(String commitID) {
		this.commitID = commitID;
	}
	
	public String getBuildName() {
		return buildName;
	}
	
	@DataBoundSetter public void setBuildName(String buildName) {
		this.buildName = buildName;
	}

	public String getShowBuildLogs() {
		return showBuildLogs;
	}
	
	@DataBoundSetter public void setShowBuildLogs(String showBuildLogs) {
		this.showBuildLogs = showBuildLogs;
	}
	
	public String getBldCfg() {
		return bldCfg;
	}
	
	public String getCheckForTriggeredDeployments() {
		return checkForTriggeredDeployments;
	}
	
	@DataBoundSetter public void setCheckForTriggeredDeployments(String checkForTriggeredDeployments) {
		this.checkForTriggeredDeployments = checkForTriggeredDeployments;
	}
	
	public String getWaitTime() {
		return waitTime;
	}
	
	@DataBoundSetter public void setWaitTime(String waitTime) {
		this.waitTime = waitTime;
	}
	
	public String getWaitTime(Map<String,String> overrides) {
		String val = getOverride(getWaitTime(), overrides);
		if (val.length() > 0)
			return val;
		return "300000";
	}
	
	
    private static final Logger LOGGER = Logger.getLogger(OpenShiftBuilder.class.getName());


	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftBuilderExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openShiftBuild";
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
            OpenShiftBuilder step = new OpenShiftBuilder(bldCfg.toString());
            if(arguments.containsKey("buildName")) {
                Object buildName = arguments.get("buildName");
                if (buildName != null) {
                    step.setBuildName(buildName.toString());
                }
            }
            if (arguments.containsKey("checkForTriggeredDeployments")) {
                Object checkForTriggeredDeployments = arguments.get("checkForTriggeredDeployments");
                if (checkForTriggeredDeployments != null) {
                    step.setCheckForTriggeredDeployments(checkForTriggeredDeployments.toString());
                }
            }
            if (arguments.containsKey("commitID")) {
                Object commitID = arguments.get("commitID");
                if (commitID != null) {
                    step.setCommitID(commitID.toString());
                }
            }
            if (arguments.containsKey("showBuildLogs")) {
                Object showBuildLogs = arguments.get("showBuildLogs");
                if (showBuildLogs != null) {
                    step.setShowBuildLogs(showBuildLogs.toString());
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
    
}

