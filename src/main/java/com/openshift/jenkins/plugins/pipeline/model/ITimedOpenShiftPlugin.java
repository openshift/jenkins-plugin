package com.openshift.jenkins.plugins.pipeline.model;

import hudson.model.TaskListener;

import java.util.Map;

public interface ITimedOpenShiftPlugin extends IOpenShiftPlugin {

    enum TimeoutUnit {
        MILLISECONDS("milli", 1), SECONDS("sec", 1000), MINUTES("min",
                1000 * 60);

        public final String name;
        public final long multiplier;

        TimeoutUnit(String name, long multiplier) {
            this.name = name;
            this.multiplier = multiplier;
        }

        @Override
        public String toString() {
            return name;
        }

        public boolean matches(String value) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }
            return name.equalsIgnoreCase(value);
        }

        public static TimeoutUnit getByName(String unit) {
            if (unit == null || unit.trim().isEmpty()) {
                // If units are not specified, fallback to millis for backwards
                // compatibility.
                unit = "milli";
            }

            unit = unit.trim();

            for (TimeoutUnit tu : TimeoutUnit.values()) {
                if (tu.name.equalsIgnoreCase(unit)) {
                    return tu;
                }
            }

            throw new IllegalArgumentException("Unexpected time unit name: "
                    + unit);
        }

        public static String normalize(String value) {
            if (value == null || value.trim().isEmpty()) {
                // Default to milliseconds for backwards compatibility
                return MILLISECONDS.toString();
            } else {
                return value.trim().toString();
            }
        }

        public long toMilliseconds(String value, long def) {
            if (value == null || value.trim().isEmpty()) {
                return def;
            }
            long l = Long.parseLong(value);
            l *= multiplier;
            return l;
        }

    }

    String getWaitTime();

    String getWaitUnit();

    long getGlobalTimeoutConfiguration();

    default long getTimeout(TaskListener listener, boolean chatty,
            Map<String, String> overrides) {
        long global = getGlobalTimeoutConfiguration();

        /**
         * We allow user to specify variable names for timeout values, so check
         * overrides map.
         */
        String field = getOverride(getWaitTime(), overrides);
        TimeoutUnit unit = TimeoutUnit.getByName(getWaitUnit());
        long chosen = unit.toMilliseconds(field, global);

        if (chatty) {
            listener.getLogger().println(
                    "Found global job type timeout configuration: " + global
                            + " milliseconds");
            if (field == null || field.trim().isEmpty()) {
                listener.getLogger().println(
                        "No local timeout configured for this step");
            } else {
                listener.getLogger().println(
                        "Local step timeout configuration: " + field + " "
                                + unit);
            }
        }

        listener.getLogger().println(
                "Operation will timeout after " + chosen + " milliseconds");
        return chosen;
    }

}
