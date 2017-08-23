package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import com.openshift.jenkins.plugins.pipeline.model.ITimedOpenShiftPlugin;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Extending this descriptor imbues subclasses with a global Jenkins timeout
 * setting.
 *
 * Theory of operation: 1. Each timed operation which exposes a global timeout
 * extends this descriptor. 2. The descriptor will persist/restore a global
 * wait/waitUnit for the operation type. 3. Classes wishing to share the same
 * timeout value (e.g. DSL steps) should access the timeout value through
 * GlobalConfig. 4. GlobalConfig will load the correct descriptor to read the
 * currently configured timeout value. 5. The host for the global config option
 * must have a global.jelly for setting the value from Jenkins Configure. See
 * examples like:
 * src/main/resources/com/openshift/jenkins/plugins/pipeline/OpenShiftExec
 * /global.jelly
 */
public abstract class TimedBuildStepDescriptor<T extends BuildStep & Describable<T>>
        extends BuildStepDescriptor<T> implements IOpenShiftPluginDescriptor {

    protected String wait;
    protected String waitUnit;

    TimedBuildStepDescriptor() {
        load();
    }

    @Override
    public synchronized void load() {
        super.load();
        if (wait == null || wait.trim().isEmpty()) {
            wait = "" + getStaticDefaultWaitTime();
        }

        long w = Long.parseLong(wait);

        if (waitUnit == null || waitUnit.trim().isEmpty()) {
            if (w > 1000
                    && (w
                            % ITimedOpenShiftPlugin.TimeoutUnit.SECONDS.multiplier == 0)) {
                // We are loading a new or an existing config without time units
                waitUnit = ITimedOpenShiftPlugin.TimeoutUnit.SECONDS.name;
                // Convert existing timeout to seconds
                w /= ITimedOpenShiftPlugin.TimeoutUnit.SECONDS.multiplier;
            } else {
                waitUnit = ITimedOpenShiftPlugin.TimeoutUnit.MILLISECONDS.name;
            }
        }

        wait = "" + w;
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        // Indicates that this builder can be used with all kinds of project
        // types
        return true;
    }

    @Override
    public synchronized boolean configure(StaplerRequest req,
            JSONObject formData) throws FormException {
        wait = formData.getString("wait");
        waitUnit = ITimedOpenShiftPlugin.TimeoutUnit.normalize(formData
                .getString("waitUnit"));
        if (wait == null || wait.isEmpty()) {
            // If someone clears the value, go back to default and use seconds
            wait = "" + getStaticDefaultWaitTime()
                    / ITimedOpenShiftPlugin.TimeoutUnit.SECONDS.multiplier;
            waitUnit = ITimedOpenShiftPlugin.TimeoutUnit.SECONDS.name;
        }
        wait = wait.trim();
        save();
        return true;
    }

    public synchronized long getConfiguredDefaultWaitTime() {
        ITimedOpenShiftPlugin.TimeoutUnit unit = ITimedOpenShiftPlugin.TimeoutUnit
                .getByName(waitUnit);
        return unit.toMilliseconds("" + wait, getStaticDefaultWaitTime());
    }

    public synchronized String getWait() {
        return wait;
    }

    public synchronized String getWaitUnit() {
        return waitUnit;
    }

    /**
     * @return Return the non-configurable default for this build step. This
     *         will populate the global default wait time for the operation the
     *         first time Jenkins loads this plugin. Once a global configuration
     *         with a value exists, this value will no longer be used. However,
     *         this value will be re-populated if the user clears the global
     *         timeout form and saves the configuration.
     */
    protected abstract long getStaticDefaultWaitTime();

}
