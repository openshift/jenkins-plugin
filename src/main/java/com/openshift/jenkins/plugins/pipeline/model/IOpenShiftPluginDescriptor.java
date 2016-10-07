package com.openshift.jenkins.plugins.pipeline.model;


import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

public interface IOpenShiftPluginDescriptor {

    default FormValidation doCheckApiURL(@QueryParameter String value)
            throws IOException, ServletException {
        return ParamVerify.doCheckApiURL(value);
    }

    default FormValidation doCheckNamespace(@QueryParameter String value)
            throws IOException, ServletException {
        return ParamVerify.doCheckNamespace(value);
    }

    default FormValidation doCheckAuthToken(@QueryParameter String value) {
        return ParamVerify.doCheckToken(value);
    }

    default FormValidation doCheckWaitTime(@QueryParameter String value) {
        return ParamVerify.doCheckForWaitTime(value);
    }

    default ListBoxModel doFillWaitUnitItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("Seconds", "sec");
        items.add("Minutes", "min");
        items.add("Milliseconds", "milli");
        return items;
    }


}
