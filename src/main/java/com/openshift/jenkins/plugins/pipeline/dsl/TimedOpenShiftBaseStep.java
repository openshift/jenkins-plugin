package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import org.kohsuke.stapler.DataBoundSetter;

public abstract class TimedOpenShiftBaseStep extends OpenShiftBaseStep
        implements ITimedOpenShiftPlugin {

    protected String waitTime;

    protected String waitUnit;

    final public String getWaitTime() {
        return waitTime;
    }

    final public String getWaitUnit() {
        return TimeoutUnit.normalize(waitUnit);
    }

    @DataBoundSetter
    final public void setWaitTime(String waitTime) {
        this.waitTime = waitTime != null ? waitTime.trim() : null;
    }

    @DataBoundSetter
    final public void setWaitUnit(String waitUnit) {
        this.waitUnit = waitUnit != null ? waitUnit.trim() : null;
    }

}
