package com.openshift.jenkins.plugins.pipeline.model;


// this class is storage for Global Jenkins config, accessible via config.jelly and extensions 
// to DescriptorImpl extensions to BuildStepDescriptor<Builder> which we only define in the 
// freestyle version of our build steps.  They get executed even if no freestyle jobs are defined,
// so no need to define them twice.  That said, we store the values in this singleton to allow for easy
// access for the DSL versions of our build steps.
public class GlobalConfig {

	public static final long BUILD_WAIT = 900000;
	public static final long BUILD_VERIFY_WAIT = 60000;
	public static final long DEPLOY_WAIT = 60000;
	public static final long DEPLOY_VERIFY_WAIT = 180000;
	public static final long SCALER_WAIT = 180000;
	public static final int SERVICE_VERIFY_RETRY = 100;
	
	private static long buildWait = BUILD_WAIT;
	private static long buildVerifyWait = BUILD_VERIFY_WAIT;
	private static long deployWait = DEPLOY_WAIT;
	private static long deployVerifyWait = DEPLOY_VERIFY_WAIT;
	private static long scalerWait = SCALER_WAIT;
	private static int serviceVerifyRetry = SERVICE_VERIFY_RETRY;
	public static long getBuildWait() {
		return buildWait;
	}
	public static void setBuildWait(long buildWait) {
		GlobalConfig.buildWait = buildWait;
	}
	public static long getBuildVerifyWait() {
		return buildVerifyWait;
	}
	public static void setBuildVerifyWait(long buildVerifyWait) {
		GlobalConfig.buildVerifyWait = buildVerifyWait;
	}
	public static long getDeployWait() {
		return deployWait;
	}
	public static void setDeployWait(long deployWait) {
		GlobalConfig.deployWait = deployWait;
	}
	public static long getDeployVerifyWait() {
		return deployVerifyWait;
	}
	public static void setDeployVerifyWait(long deployVerifyWait) {
		GlobalConfig.deployVerifyWait = deployVerifyWait;
	}
	public static long getScalerWait() {
		return scalerWait;
	}
	public static void setScalerWait(long scalerWait) {
		GlobalConfig.scalerWait = scalerWait;
	}
	public static int getServiceVerifyRetry() {
		return serviceVerifyRetry;
	}
	public static void setServiceVerifyRetry(int serviceVerifyRetry) {
		GlobalConfig.serviceVerifyRetry = serviceVerifyRetry;
	}
	
	
}
