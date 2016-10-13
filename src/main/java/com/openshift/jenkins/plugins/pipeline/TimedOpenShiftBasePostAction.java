package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import jenkins.tasks.SimpleBuildStep;

import java.io.Serializable;

public abstract class TimedOpenShiftBasePostAction extends OpenShiftBasePostAction implements SimpleBuildStep, Serializable, ITimedOpenShiftPlugin {

    protected final String waitTime;
    protected final String waitUnit;

    public TimedOpenShiftBasePostAction(String apiURL, String namespace, String authToken, String verbose, String waitTime, String waitUnit) {
        super(apiURL, namespace, authToken, verbose);
        this.waitTime = waitTime != null ? waitTime.trim() : null;
        this.waitUnit = waitUnit != null ? waitUnit.trim() : null;
    }

    final public String getWaitTime() {
        return waitTime;
    }

    final public String getWaitUnit() {
        return TimeoutUnit.normalize(waitUnit);
    }

}
