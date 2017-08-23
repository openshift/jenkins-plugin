package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.internal.restclient.DefaultClient;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.restclient.ClientBuilder;

import hudson.EnvVars;
import hudson.model.JobProperty;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletException;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

public interface IOpenShiftPluginDescriptor extends
        IOpenShiftParameterOverrides {

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

    default EnvVars buildEnvVars() {
        EnvVars allOverrides = new EnvVars(EnvVars.masterEnvVars);
        // when running outside of an openshift pod, global env vars like
        // SKIP_TLS will not exist in master env vars
        if (Jenkins.getInstance().getGlobalNodeProperties() != null) {
            if (Jenkins.getInstance().getGlobalNodeProperties()
                    .get(hudson.slaves.EnvironmentVariablesNodeProperty.class) != null) {
                if (Jenkins
                        .getInstance()
                        .getGlobalNodeProperties()
                        .get(hudson.slaves.EnvironmentVariablesNodeProperty.class)
                        .getEnvVars() != null) {
                    allOverrides
                            .putAll(Jenkins
                                    .getInstance()
                                    .getGlobalNodeProperties()
                                    .get(hudson.slaves.EnvironmentVariablesNodeProperty.class)
                                    .getEnvVars());
                }
            }
        }
        String[] reqPieces = Stapler.getCurrentRequest().getRequestURI()
                .split("/");
        if (reqPieces != null && reqPieces.length > 2) {
            for (Job j : Jenkins.getInstance().getAllItems(Job.class)) {
                if (j.getName().equals(reqPieces[2])) {
                    List<JobProperty> jps = j.getAllProperties();
                    for (JobProperty jp : jps) {
                        if (jp instanceof ParametersDefinitionProperty) {
                            ParametersDefinitionProperty prop = (ParametersDefinitionProperty) jp;
                            for (ParameterDefinition param : prop
                                    .getParameterDefinitions()) {
                                allOverrides.put(param.getName(), param
                                        .getDefaultParameterValue().getValue()
                                        .toString());
                            }
                        }
                    }
                }
            }
        }
        return allOverrides;
    }

    static final Logger LOGGER = Logger
            .getLogger(IOpenShiftPluginDescriptor.class.getName());

    default FormValidation doTestConnection(@QueryParameter String apiURL,
            @QueryParameter String authToken) {

        EnvVars allOverrides = buildEnvVars();

        if (apiURL == null || StringUtils.isEmpty(apiURL)) {
            if (allOverrides
                    .containsKey(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY)
                    && !StringUtils
                            .isEmpty(allOverrides
                                    .get(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY))) {
                apiURL = "https://"
                        + allOverrides
                                .get(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY);

                if (allOverrides
                        .containsKey(IOpenShiftPlugin.KUBERNETES_SERVICE_PORT_ENV_KEY)) {
                    apiURL = apiURL
                            + ":"
                            + allOverrides
                                    .get(IOpenShiftPlugin.KUBERNETES_SERVICE_PORT_ENV_KEY);
                }
            } else if (allOverrides
                    .containsKey(IOpenShiftPlugin.KUBERNETES_MASTER_ENV_KEY)
                    && !StringUtils.isEmpty(allOverrides
                            .get(IOpenShiftPlugin.KUBERNETES_MASTER_ENV_KEY))) {
                // this one already has the https:// prefix
                apiURL = allOverrides
                        .get(IOpenShiftPlugin.KUBERNETES_MASTER_ENV_KEY);
            } else {
                return FormValidation.error("Required fields not provided");
            }
        }

        try {

            Auth auth = Auth.createInstance(null,
                    getOverride(apiURL, allOverrides), allOverrides);

            ClientBuilder cb = new ClientBuilder(getOverride(apiURL,
                    allOverrides))
                    .sslCertificateCallback(auth)
                    .usingToken(
                            Auth.deriveBearerToken(
                                    getOverride(authToken, allOverrides), null,
                                    false, allOverrides))
                    .sslCertificateCollection(
                            getOverride(apiURL, allOverrides), auth.getCerts());

            if (auth.useCert())
                cb.sslCertCallbackWithDefaultHostnameVerifier(true);

            DefaultClient client = (DefaultClient) cb.build();

            if (client == null) {
                return FormValidation.error("Connection unsuccessful");
            }

            String status = client.getServerReadyStatus();
            if (status == null || !status.equalsIgnoreCase("ok")) {
                return FormValidation
                        .error("Connection made but server status is:  "
                                + status);
            }

            // the ready status is good for verifying the apiURL, but not for a
            // bad token;
            // making this REST call against the api server to validate the
            // token
            Request request = client
                    .newRequestBuilderTo(
                            getOverride(apiURL, allOverrides) + "/apis").get()
                    .build();
            Response result = client.adapt(OkHttpClient.class).newCall(request)
                    .execute();

        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "doTestConnection", e);
            // note, the UnauthorizedException from the restclient/okhttp3 had a
            // misleading suggestion of using Basic authentication,
            // so going with just the exception's class name
            return FormValidation.error("Connection unsuccessful: "
                    + e.getClass().getName());
        }

        return FormValidation.ok("Connection successful");
    }
}
