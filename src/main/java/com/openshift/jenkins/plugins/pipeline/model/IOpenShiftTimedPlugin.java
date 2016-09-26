package com.openshift.jenkins.plugins.pipeline.model;

import java.util.Map;

public interface IOpenShiftTimedPlugin extends IOpenShiftPlugin {
	String getWaitUnit();

	String getWaitTime();

	String getWaitTime(Map<String, String> overrides);

	default String checkWaitUnit(String waitUnit) {
		if (waitUnit == null) {
			// For previous versions of the plugin, the default unit was milliseconds.
			return "milli";
		}

		if(waitUnit.length() == 0) {
			return "sec";
		}

		return waitUnit;
	}

	// Used for if a user specifies a time unit with a wait time. Unitless numbers are considered milliseconds.
	default long convertUnitNotation(String value) {
		if(value.matches("\\d+milli")) {
			return Long.valueOf(value.substring(0,value.length()-5));
		}
		if(value.matches("\\d+sec")) {
			return Long.valueOf(value.substring(0,value.length()-3)) * 1000;
		}
		if(value.matches("\\d+min")) {
			return Long.valueOf(value.substring(0,value.length()-3)) * 60 * 1000;
		}
		return Long.valueOf(value);
	}
}
