package com.openshift.jenkins.plugins.pipeline.dsl;


import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;

public class OpenShiftExecExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

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
    protected Void run() throws Exception {
    	boolean success = step.doItCore(listener, envVars, runObj, null, launcher);
    	if (!success) {
    		throw new AbortException("\"" + step.getDescriptor().getDisplayName() + "\" failed");
    	}
    	return null;
    }
}