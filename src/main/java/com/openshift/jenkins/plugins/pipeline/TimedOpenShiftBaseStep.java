package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.Serializable;

public abstract class TimedOpenShiftBaseStep extends OpenShiftBaseStep implements SimpleBuildStep, Serializable, ITimedOpenShiftPlugin {

    protected final String waitTime;
    protected final String waitUnit;

    protected TimedOpenShiftBaseStep(String apiURL, String namespace, String authToken, String verbose, String waitTime, String waitUnit) {
        super(apiURL, namespace, authToken, verbose);
        this.waitTime = waitTime;
        this.waitUnit = waitUnit;
    }

    final public String getWaitTime() {
        return waitTime;
    }

    final public String getWaitUnit() {
        return TimeoutUnit.normalize(waitUnit);
    }

}
