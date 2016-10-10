package com.openshift.jenkins.plugins.pipeline.model;


import com.openshift.jenkins.plugins.pipeline.*;

// this class is storage for Global Jenkins config, accessible via config.jelly and extensions
// to DescriptorImpl extensions to BuildStepDescriptor<Builder> which we only define in the 
// freestyle version of our build steps.  They get executed even if no freestyle jobs are defined,
// so no need to define them twice.  That said, we store the values in this singleton to allow for easy
// access for the DSL versions of our build steps.
public class GlobalConfig {

    private static final long MINUTE = 60 * 1000;

    public static final long UBER_DEFAULT_WAIT = 5 * MINUTE;

    public static final long DEFAULT_BUILD_WAIT = 15 * MINUTE;
    public static final long DEFAULT_BUILD_VERIFY_WAIT = 1 * MINUTE;

    public static final long DEFAULT_DEPLOY_WAIT = 10 * MINUTE;
    public static final long DEFAULT_DEPLOY_VERIFY_WAIT = 3 * MINUTE;

    public static final long DEFAULT_SCALER_WAIT = 3 * MINUTE;
    public static final int DEFAULT_SERVICE_VERIFY_RETRY = 100;

    public static final long DEFAULT_EXEC_WAIT = 3 * MINUTE;

    public static long getBuildWait() {
        return new OpenShiftBuilder.DescriptorImpl().getConfiguredDefaultWaitTime();
    }

    public static long getBuildVerifyWait() {
        return new OpenShiftBuildVerifier.DescriptorImpl().getConfiguredDefaultWaitTime();
    }

    public static long getExecWait() {
        return new OpenShiftExec.DescriptorImpl().getConfiguredDefaultWaitTime();
    }

    public static long getDeployWait() {
        return new OpenShiftDeployer.DescriptorImpl().getConfiguredDefaultWaitTime();
    }

    public static long getDeployVerifyWait() {
        return new OpenShiftDeploymentVerifier.DescriptorImpl().getConfiguredDefaultWaitTime();
    }

    public static long getScalerWait() {
        return new OpenShiftScaler.DescriptorImpl().getConfiguredDefaultWaitTime();
    }

    public static int getServiceVerifyRetry() {
        return new OpenShiftServiceVerifier.DescriptorImpl().getConfiguredRetryCount();
    }


}
