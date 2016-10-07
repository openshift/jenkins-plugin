package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import jenkins.tasks.SimpleBuildStep;

import java.io.Serializable;

public abstract class TimedOpenShiftBaseStep extends OpenShiftBaseStep  implements SimpleBuildStep, Serializable, ITimedOpenShiftPlugin {

    protected final String waitTime;

    protected TimedOpenShiftBaseStep(String apiURL, String namespace, String authToken, String verbose, String waitTime) {
		super( apiURL, namespace, authToken, verbose );
		this.waitTime = waitTime;
    }

    final public String getWaitTime() {
		return waitTime;
	}

}
