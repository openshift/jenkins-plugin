package com.openshift.jenkins.plugins.pipeline.model;

import java.util.Map;

public interface ITimedOpenShiftPlugin extends IOpenShiftPlugin {

	default long getDefaultWaitTime() {
		return GlobalConfig.UBER_DEFAULT_WAIT;
	}

	String getWaitTime();

	default long getTimeout(Map<String,String> overrides) {
		/**
		 * We allow user to specify variable names for
		 * timeout values, so evaluate overrides.
		 */
		String field = getOverride( getWaitTime(), overrides );

		if ( field != null && !field.trim().isEmpty() ) {
			return Long.parseLong( field );
		}

		return getDefaultWaitTime();
	}

}
