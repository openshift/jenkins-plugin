package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.NameValuePair;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftBuilder;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public class OpenShiftBuilder extends TimedOpenShiftBaseStep implements IOpenShiftBuilder {

    protected final String bldCfg;
    protected String commitID;
    protected String buildName;
    protected String showBuildLogs;
    protected String checkForTriggeredDeployments;
    protected List<NameValuePair> envVars = new ArrayList<>();

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuilder(String bldCfg) {
        this.bldCfg = bldCfg;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

    public String getCommitID() {
        return commitID;
    }

    @DataBoundSetter
    public void setCommitID(String commitID) {
        this.commitID = commitID != null ? commitID.trim() : null;
    }

    public String getBuildName() {
        return buildName;
    }

    @DataBoundSetter
    public void setBuildName(String buildName) {
        this.buildName = buildName != null ? buildName.trim() : null;
    }

    public String getShowBuildLogs() {
        return showBuildLogs;
    }

    @DataBoundSetter
    public void setShowBuildLogs(String showBuildLogs) {
        this.showBuildLogs = showBuildLogs != null ? showBuildLogs.trim() : null;
    }

    public String getBldCfg() {
        return bldCfg;
    }

    public List<NameValuePair> getEnv() {
        return envVars;
    }

    @DataBoundSetter
    public void setEnv(List<NameValuePair> v) {
        this.envVars = v;
    }

    public String getCheckForTriggeredDeployments() {
        return checkForTriggeredDeployments;
    }

    @DataBoundSetter
    public void setCheckForTriggeredDeployments(String checkForTriggeredDeployments) {
        this.checkForTriggeredDeployments = checkForTriggeredDeployments != null ? checkForTriggeredDeployments.trim() : null;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            super(OpenShiftBuilderExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftBuild";
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
            if (bldCfg == null || bldCfg.toString().trim().length() == 0)
                bldCfg = arguments.get("bldCfg");
            if (bldCfg == null || bldCfg.toString().trim().length() == 0)
                throw new IllegalArgumentException("need to specify buildConfig");
            OpenShiftBuilder step = new OpenShiftBuilder(bldCfg.toString());
            if (arguments.containsKey("buildName")) {
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

            // Allow env to be specified as a map: env: [ [ name : 'name1', value : 'value2' ], ... ]
            Object envObject = arguments.get("env");
            if (envObject != null) {
                try {
                    ArrayList<NameValuePair> envs = new ArrayList<>();
                    List l = (List) envObject;
                    for (Object o : l) {
                        Map m = (Map) o;
                        Object name = m.get("name");
                        Object value = m.get("value");
                        if (name == null || value == null) {
                            throw new IOException("Missing name or value in entry: " + o.toString());
                        }
                        envs.add(new NameValuePair(name.toString().trim(), value.toString().trim()));
                    }
                    step.setEnv(envs);
                } catch (Throwable t) {
                    throw new UnsupportedOperationException("Environment variables must be specified as follows: env: [ [ name : 'name1', value : 'value2' ], ... ]. Error: " + t.getMessage());
                }
            }

            ParamVerify.updateTimedDSLBaseStep(arguments, step);
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

