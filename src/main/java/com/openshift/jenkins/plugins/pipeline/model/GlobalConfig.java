package com.openshift.jenkins.plugins.pipeline.model;


// this class is storage for Global Jenkins config, accessible via config.jelly and extensions 
// to DescriptorImpl extensions to BuildStepDescriptor<Builder> which we only define in the 
// freestyle version of our build steps.  They get executed even if no freestyle jobs are defined,
// so no need to define them twice.  That said, we store the values in this singleton to allow for easy
// access for the DSL versions of our build steps.
public class GlobalConfig {

	private static final long MINUTE = 60*1000;

	public static final long UBER_DEFAULT_WAIT = 5*MINUTE;

	public static final long DEFAULT_BUILD_WAIT = 15*MINUTE;
	public static final long DEFAULT_BUILD_VERIFY_WAIT = 1*MINUTE;

	public static final long DEFAULT_DEPLOY_WAIT = 10*MINUTE;
	public static final long DEFAULT_DEPLOY_VERIFY_WAIT = 3*MINUTE;

	public static final long DEFAULT_SCALER_WAIT = 3*MINUTE;
	public static final int DEFAULT_SERVICE_VERIFY_RETRY = 100;

	public static final long DEFAULT_EXEC_WAIT = 3*MINUTE;

	private static long buildWait = DEFAULT_BUILD_WAIT;
	private static long buildVerifyWait = DEFAULT_BUILD_VERIFY_WAIT;
	private static long deployWait = DEFAULT_DEPLOY_WAIT;
	private static long deployVerifyWait = DEFAULT_DEPLOY_VERIFY_WAIT;
	private static long scalerWait = DEFAULT_SCALER_WAIT;
	private static long execWait = DEFAULT_EXEC_WAIT;

	private static int serviceVerifyRetry = DEFAULT_SERVICE_VERIFY_RETRY;

	public static void setBuildWait(long defaultBuildWait) {
		GlobalConfig.buildWait = defaultBuildWait;
	}
	public static long getBuildWait() {
		return buildWait;
	}

	public static void setBuildVerifyWait(long defaultBuildVerifyWait) {GlobalConfig.buildVerifyWait = defaultBuildVerifyWait;	}
	public static long getBuildVerifyWait() {
		return buildVerifyWait;
	}

	public static void setExecWait(long defaultExecWait) {GlobalConfig.execWait = defaultExecWait;}
	public static long getExecWait() { return execWait; }

	public static void setDeployWait(long defaultDeployWait) {
		GlobalConfig.deployWait = defaultDeployWait;
	}
	public static long getDeployWait() {
		return deployWait;
	}

	public static void setDeployVerifyWait(long defaultDeployVerifyWait) {	GlobalConfig.deployVerifyWait = defaultDeployVerifyWait;	}
	public static long getDeployVerifyWait() {	return deployVerifyWait; }

	public static void setScalerWait(long defaultScalerWait) {
		GlobalConfig.scalerWait = defaultScalerWait;
	}
	public static long getScalerWait() { return scalerWait; }

	public static void setServiceVerifyRetry(int defaultServiceVerifyRetry) {	GlobalConfig.serviceVerifyRetry = defaultServiceVerifyRetry;}
	public static int getServiceVerifyRetry() {
		return serviceVerifyRetry;
	}

	
}
