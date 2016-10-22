package com.openshift.jenkins.plugins.pipeline.model;

import java.util.Map;

public interface IOpenShiftParameterOverrides {

    default String pruneKey(String key) {
        if (key == null)
            key = "";
        if (key.startsWith("$"))
            return key.substring(1, key.length()).trim();
        return key.trim();
    }

    default String getOverride(String key, Map<String, String> overrides) {
        String val = pruneKey(key);
        // try override when the key is the entire parameter ... we don't just use
        // replaceMacro cause we also support PARM with $ or ${}
        if (overrides != null && overrides.containsKey(val)) {
            val = overrides.get(val);
        } else {
            // see if it is a mix used key (i.e. myapp-${VERSION}) or ${val}
            String tmp = hudson.Util.replaceMacro(key, overrides);
            if (tmp != null && tmp.length() > 0)
                val = tmp;
        }
        return val;
    }

}
