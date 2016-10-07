package com.openshift.jenkins.plugins.pipeline.model;


import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

public interface IOpenShiftPluginDescriptorValidation {

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


}
