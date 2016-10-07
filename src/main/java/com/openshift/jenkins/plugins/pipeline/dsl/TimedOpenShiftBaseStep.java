package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import org.kohsuke.stapler.DataBoundSetter;


public abstract class TimedOpenShiftBaseStep extends OpenShiftBaseStep implements ITimedOpenShiftPlugin {

    protected String waitTime;

    final public String getWaitTime() {
		return waitTime;
	}
    
    @DataBoundSetter final public void setWaitTime(String waitTime) {
    	this.waitTime = waitTime;
    }

}
