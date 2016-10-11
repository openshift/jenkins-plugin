package com.openshift.jenkins.plugins.pipeline.model;


import com.openshift.internal.restclient.DefaultClient;
import com.openshift.internal.restclient.authorization.AuthorizationContext;
import com.openshift.internal.restclient.okhttp.OpenShiftAuthenticator;
import com.openshift.internal.restclient.okhttp.ResponseCodeInterceptor;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.http.IHttpConstants;
import com.openshift.restclient.utils.SSLUtils;
import hudson.EnvVars;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

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

    default FormValidation doTestConnection(@QueryParameter String apiURL, @QueryParameter String authToken) {


        if (StringUtils.isEmpty(apiURL)) {
            if(!EnvVars.masterEnvVars.containsKey(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY) &&
                    !StringUtils.isEmpty(EnvVars.masterEnvVars.get(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY))) {
                return FormValidation.error("Required fields not provided");
            }

            apiURL = EnvVars.masterEnvVars.get(IOpenShiftPlugin.KUBERNETES_SERVICE_HOST_ENV_KEY);
        }

        try {

            URL url = new URL(apiURL); // Test to make sure we have a good URL

            Auth auth = Auth.createInstance(null, apiURL, EnvVars.masterEnvVars);

            DefaultClient client = (DefaultClient) new ClientBuilder(apiURL).
                    sslCertificateCallback(auth).
                    withConnectTimeout(5, TimeUnit.SECONDS).
                    usingToken(Auth.deriveBearerToken(authToken, null, false, EnvVars.masterEnvVars)).
                    sslCertificate(apiURL, auth.getCert()).
                    build();

            if (client == null) {
                return FormValidation.error("Connection unsuccessful");
            }

            client.getServerReadyStatus();

            HttpUtils.httpGet(false, null, EnvVars.masterEnvVars, apiURL, authToken, "", client, auth, apiURL);
        } catch (MalformedURLException e ) {
            return FormValidation.error("Connection Unsuccessful: Bad URL");
        } catch ( Exception e ) {
            return FormValidation.error("Connection unsuccessful");
        }

        return FormValidation.ok("Connection successful");
    }
}
