package com.openshift.jenkins.plugins.pipeline.model;


// this class is storage for Global Jenkins config, accessible via config.jelly and extensions 
// to DescriptorImpl extensions to BuildStepDescriptor<Builder> which we only define in the
// freestyle version of our build steps.  They get executed even if no freestyle jobs are defined,
// so no need to define them twice.  That said, we store the values in this singleton to allow for easy
// access for the DSL versions of our build steps.
public class GlobalConfig {

	// TODO: Create a constant for wait units
	public static final String BUILD_WAIT = "900sec";
	public static final String BUILD_VERIFY_WAIT = "60sec";
	public static final String DEPLOY_WAIT = "600sec";
	public static final String DEPLOY_VERIFY_WAIT = "180sec";
	public static final String SCALER_WAIT = "180sec";
	public static final int SERVICE_VERIFY_RETRY = 100;
	
	private static String buildWait = BUILD_WAIT;
	private static String buildVerifyWait = BUILD_VERIFY_WAIT;
	private static String deployWait = DEPLOY_WAIT;
	private static String deployVerifyWait = DEPLOY_VERIFY_WAIT;
	private static String scalerWait = SCALER_WAIT;
	private static int serviceVerifyRetry = SERVICE_VERIFY_RETRY;

	public static String getBuildWait() {
		return buildWait.replaceAll("(milli|sec|min)", "");
	}
	public static void setBuildWait(String buildWait) {
		GlobalConfig.buildWait = buildWait;
	}
	public static String getBuildWaitUnits() { 
		return buildWait.replaceAll("\\d+", "");
	}

	public static String getBuildVerifyWait() {
		return buildVerifyWait.replaceAll("(milli|sec|min)", "");
	}
	public static void setBuildVerifyWait(String buildVerifyWait) {
		GlobalConfig.buildVerifyWait = buildVerifyWait;
	}
	public static String getBuildVerifyWaitUnits() {
		return buildVerifyWait.replaceAll("\\d+", "");
	}

	public static String getDeployWait() {
		return deployWait.replaceAll("(milli|sec|min)", "");
	}
	public static void setDeployWait(String deployWait) {
		GlobalConfig.deployWait = deployWait;
	}
	public static String getDeployWaitUnits() {
		return deployWait.replaceAll("\\d+", "");
	}

	public static String getDeployVerifyWait() {
		return deployVerifyWait.replaceAll("(milli|sec|min)", "");
	}
	public static void setDeployVerifyWait(String deployVerifyWait) {
		GlobalConfig.deployVerifyWait = deployVerifyWait;
	}
	public static String getDeployVerifyWaitUnits() {
		return deployVerifyWait.replaceAll("\\d+", "");
	}

	public static String getScalerWait() {
		return scalerWait.replaceAll("(milli|sec|min)", "");
	}
	public static void setScalerWait(String scalerWait) {
		GlobalConfig.scalerWait = scalerWait;
	}
	public static String getScalerWaitUnits() {
		return scalerWait.replaceAll("\\d+", "");
	}

	public static int getServiceVerifyRetry() {
		return serviceVerifyRetry;
	}
	public static void setServiceVerifyRetry(int serviceVerifyRetry) {
		GlobalConfig.serviceVerifyRetry = serviceVerifyRetry;
	}
}
