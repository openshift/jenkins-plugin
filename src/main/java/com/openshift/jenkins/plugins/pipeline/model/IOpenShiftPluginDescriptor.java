package com.openshift.jenkins.plugins.pipeline.model;


import com.openshift.internal.restclient.DefaultClient;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.restclient.ClientBuilder;

import hudson.EnvVars;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    static final Logger LOGGER = Logger.getLogger(IOpenShiftPluginDescriptor.class.getName());
    default FormValidation doTestConnection(@QueryParameter String apiURL, @QueryParameter String authToken) {

        if (apiURL == null || StringUtils.isEmpty(apiURL)) {
            if(EnvVars.masterEnvVars.containsKey(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY) &&
                    !StringUtils.isEmpty(EnvVars.masterEnvVars.get(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY))) {
                apiURL = "https://" + EnvVars.masterEnvVars.get(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY);

                if(EnvVars.masterEnvVars.containsKey(IOpenShiftPlugin.KUBERNETES_SERVICE_PORT_ENV_KEY)) {
                    apiURL = apiURL + ":" + EnvVars.masterEnvVars.get(IOpenShiftPlugin.KUBERNETES_SERVICE_PORT_ENV_KEY);
                }
            } else if(EnvVars.masterEnvVars.containsKey(IOpenShiftPlugin.KUBERNETES_MASTER_ENV_KEY) &&
                    !StringUtils.isEmpty(EnvVars.masterEnvVars.get(IOpenShiftPlugin.KUBERNETES_MASTER_ENV_KEY))) {
                // this one already has the https:// prefix
                apiURL = EnvVars.masterEnvVars.get(IOpenShiftPlugin.KUBERNETES_MASTER_ENV_KEY);
            } else {
                return FormValidation.error("Required fields not provided");
            }
        }

        try {
            authToken = Auth.deriveBearerToken(authToken, null, false, EnvVars.masterEnvVars);

            Auth auth = Auth.createInstance(null, apiURL, EnvVars.masterEnvVars);

            DefaultClient client = (DefaultClient) new ClientBuilder(apiURL).
                    sslCertificateCallback(auth).
                    withConnectTimeout(5, TimeUnit.SECONDS).
                    usingToken(authToken).
                    sslCertificate(apiURL, auth.getCert()).
                    build();

            if (client == null) {
                return FormValidation.error("Connection unsuccessful");
            }

            String status = client.getServerReadyStatus();
            if (status == null || !status.equalsIgnoreCase("ok")) {
                return FormValidation.error("Connection made but server status is:  " + status);
            }

        } catch ( Throwable e ) {
            LOGGER.log(Level.SEVERE, "doTestConnection", e);
            return FormValidation.error("Connection unsuccessful: " + e.getMessage());
        }

        return FormValidation.ok("Connection successful");
    }
}
