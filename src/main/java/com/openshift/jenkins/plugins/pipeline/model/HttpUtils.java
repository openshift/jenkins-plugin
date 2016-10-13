package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.internal.restclient.DefaultClient;
import com.openshift.internal.restclient.authorization.AuthorizationContext;
import com.openshift.internal.restclient.okhttp.OpenShiftAuthenticator;
import com.openshift.internal.restclient.okhttp.ResponseCodeInterceptor;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.restclient.http.IHttpConstants;
import com.openshift.restclient.utils.SSLUtils;
import hudson.model.TaskListener;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpUtils {
    static String httpGet(boolean chatty, TaskListener listener, Map<String, String> overrides, String urlString, String authToken, String displayName, DefaultClient client, Auth auth, String apiURL) {
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            if(chatty)
                e.printStackTrace(listener.getLogger());
            return null;
        }
        // our lower level openshift-restclient-java usage here is not agreeable with the TrustManager maintained there,
        // so we set up our own trust manager like we used to do in order to verify the server cert
        try {
            AuthorizationContext authContext = new AuthorizationContext(authToken, null, null);
            ResponseCodeInterceptor responseCodeInterceptor = new ResponseCodeInterceptor();
            OpenShiftAuthenticator authenticator = new OpenShiftAuthenticator();
            Dispatcher dispatcher = new Dispatcher();
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .addInterceptor(responseCodeInterceptor)
                    .authenticator(authenticator)
                    .dispatcher(dispatcher)
                    .readTimeout(IHttpConstants.DEFAULT_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                    .writeTimeout(IHttpConstants.DEFAULT_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                    .connectTimeout(IHttpConstants.DEFAULT_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                    .hostnameVerifier(auth);
            X509TrustManager trustManager = null;
            if (auth.useCert()) {
                trustManager = Auth.createLocalTrustStore(auth, apiURL);
            } else {
                // so okhttp is a bit of a PITA when it comes to enforcing skip tls behavior
                // (it should just allow you to set a null ssl socket factory given how
                // RealConnection/Address/Route work), but stack overflow came to the rescue
                // (http://stackoverflow.com/questions/25509296/trusting-all-certificates-with-okhttp)
                // Create a trust manager that does not validate certificate chains
                trustManager = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                };
            }

            SSLContext sslContext = null;
            try {
                sslContext = SSLUtils.getSSLContext(trustManager);
            } catch (KeyManagementException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
                return null;
            } catch (NoSuchAlgorithmException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
                return null;
            }
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);

            OkHttpClient okClient = builder.build();
            authContext.setClient(client);
            responseCodeInterceptor.setClient(client);
            authenticator.setClient(client);
            authenticator.setOkClient(okClient);
            Request request = client.newRequestBuilderTo(url.toString()).get().build();
            Response result;
            try {
                result = okClient.newCall(request).execute();
                String response = result.body().string();
                return response;
            } catch (IOException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
            }
            return null;
        } finally {
            Auth.resetLocalTrustStore();
        }
    }
}
