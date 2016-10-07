package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import jenkins.tasks.SimpleBuildStep;

import java.io.Serializable;

public abstract class TimedOpenShiftBasePostAction extends OpenShiftBasePostAction implements SimpleBuildStep, Serializable, ITimedOpenShiftPlugin {

    protected final String waitTime;

    public TimedOpenShiftBasePostAction(String apiURL, String namespace, String authToken, String verbose, String waitTime) {
		super( apiURL, namespace, authToken, verbose );
		this.waitTime = waitTime;
	}

	final public String getWaitTime() {
		return waitTime;
	}

}
