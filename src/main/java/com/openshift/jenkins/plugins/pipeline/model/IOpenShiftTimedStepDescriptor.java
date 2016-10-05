package com.openshift.jenkins.plugins.pipeline.model;

import hudson.util.ListBoxModel;

import java.util.Map;

public interface IOpenShiftTimedStepDescriptor {

	// TODO: Use an enum or Time type to standardize units, perhaps a constant

	String getWait();

	void setWait(String wait);

	String getWaitUnit();

	void setWaitUnit(String waitUnit);

	default void doFillWait(String defaultWait) {
		if(getWait() == null) {
			setWait(defaultWait);
		}
	}

	default void doFillWaitUnit(String defaultWaitUnit) {
		if(getWaitUnit() == null) {
			// If the user upgrades from an older version of the plugin, we need to set the units to milliseconds
			if(getWait() != null && getWait().length() > 0) {
				setWaitUnit("milli");
			} else {
				setWaitUnit(defaultWaitUnit);
			}
		}
	}

	default void doFillWaitArguments(Map<String, Object> arguments) {
		if (arguments.containsKey("waitTime")) {
			Object waitTime = arguments.get("waitTime");
			if (waitTime != null) {
				setWait(waitTime.toString());
			}
		}

		if (arguments.containsKey("waitUnit")) {
			Object wUnit = arguments.get("waitUnit");
			if (wUnit != null && wUnit.toString().length() != 0) {
				setWaitUnit(wUnit.toString());
			}
		}

		doFillWaitUnit("sec");
	}

	default ListBoxModel doFillWaitUnitItems() {
		ListBoxModel items = new ListBoxModel();

		switch(getWaitUnit()){
			case "min":
				items.add("Minutes", "min");
				items.add("Milliseconds", "milli");
				items.add("Seconds", "sec");
				break;
			case "sec":
				items.add("Seconds", "sec");
				items.add("Milliseconds", "milli");
				items.add("Minutes", "min");
				break;
			case "milli":
				items.add("Milliseconds", "milli");
				items.add("Seconds", "sec");
				items.add("Minutes", "min");
				break;
			default:
				// We want the default units to be seconds, as opposed to milliseconds.
				items.add("Seconds", "sec");
				items.add("Milliseconds", "milli");
				items.add("Minutes", "min");
		}

		return items;
	}
}
