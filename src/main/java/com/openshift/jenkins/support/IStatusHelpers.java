package com.openshift.jenkins.support;

public interface IStatusHelpers {

    static final String STATE_COMPLETE = "Complete";
    static final String STATE_CANCELLED = "Cancelled";
    static final String STATE_ERROR = "Error";
    public static final String STATE_FAILED = "Failed";

    
    default boolean isBuildFinished(String bldState) {
        if (bldState != null && (bldState.equals(STATE_COMPLETE) || bldState.equals(STATE_FAILED) || bldState.equals(STATE_ERROR) || bldState.equals(STATE_CANCELLED)))
            return true;
        return false;
    }
    
    default boolean isDeployFinished(String deployState) {
        if (deployState != null && (deployState.equals(STATE_FAILED) || deployState.equals(STATE_COMPLETE)))
            return true;
        return false;
    }
}
