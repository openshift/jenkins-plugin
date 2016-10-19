package com.openshift.jenkins.plugins.pipeline.dsl;


import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.Serializable;

public class OpenShiftExecExecution extends AbstractSynchronousNonBlockingStepExecution<OpenShiftExecExecution.ExecResult> {

    private static final long serialVersionUID = 1L;

    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Launcher launcher;
    @StepContextParameter
    private transient EnvVars envVars;
    @StepContextParameter
    private transient Run<?, ?> runObj;
    @StepContextParameter
    private transient FilePath filePath; // included as ref of what can be included for future use
    @StepContextParameter
    private transient Executor executor; // included as ref of what can be included for future use
    @StepContextParameter
    private transient Computer computer; // included as ref of what can be included for future use

    @Inject
    private transient OpenShiftExec step;

    @Override
    protected ExecResult run() throws Exception {
        boolean success = step.doItCore(listener, envVars, runObj, null, launcher);
        if (!success) {
            throw new AbortException("\"" + step.getDescriptor().getDisplayName() + "\" failed");
        }
        return step.getExecResult();
    }


    public interface ExecResult extends Serializable {
        @Whitelisted
        String getStdout();

        @Whitelisted
        String getStderr();

        @Whitelisted
        String getError();

        @Whitelisted
        String getFailure();
    }
}