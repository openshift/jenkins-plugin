package com.openshift.jenkins.plugins.pipeline;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.openshift.restclient.ISSLCertificateCallback;

import hudson.model.TaskListener;

public class Auth implements ISSLCertificateCallback {
    static final Logger LOGGER = Logger.getLogger(Auth.class.getName());

    private static final String AUTH_FILE = "/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String CERT_FILE = "/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    public static final String CERT_ARG = " --certificate-authority=/run/secrets/kubernetes.io/serviceaccount/ca.crt ";

    private Collection<X509Certificate> certs = null;
    private boolean skipTls = false;
    private TaskListener listener = null;
    private static X509TrustManager x509TrustManager;

    private Auth(Collection<X509Certificate> certs, TaskListener listener,
            boolean skipTls) {
        this.certs = certs;
        this.listener = listener;
        this.skipTls = skipTls;
    }

    public static Auth createInstance(TaskListener listener, String apiURL,
            Map<String, String> env) throws RuntimeException {
        Auth auth = null;
        File f = new File(CERT_FILE);
        String skipVal = env.get("SKIP_TLS");
        String certVal = env.get("CA_CERT");
        boolean skip = skipVal != null
                && !skipVal.trim().equalsIgnoreCase("false");
        if ((f.exists() || certVal != null) && !skip) {
            if (listener != null)
                listener.getLogger().println(
                        "Auth - cert file exists - " + f.exists()
                                + ", CA_CERT - " + certVal + "\n skip tls - "
                                + skipVal);
            try {
                auth = new Auth(createCert(f, certVal, listener, apiURL),
                        listener, skip);
            } catch (Exception e) {
                if (listener != null)
                    e.printStackTrace(listener.getLogger());
                throw new RuntimeException(e);
            }
        } else {
            if (skip)
                auth = new Auth(null, listener, skip);
            else
                throw new RuntimeException(
                        "You have not specified SKIP_TLS, but a cert is not accessible.  See https://github.com/openshift/jenkins-plugin#certificates-and-encryption");
        }
        return auth;
    }

    // we use a local trust store when we have to make direct http get's and
    // bypass the openshift-restclient-java
    public static X509TrustManager createLocalTrustStore(Auth auth,
            String apiURL) {
        if (auth.useCert()) {
            try {
                ((X509Certificate) auth.getCerts().toArray()[0])
                        .checkValidity();
                if (auth.listener != null) {
                    auth.listener.getLogger().println(
                            "Auth - x509 created cert is "
                                    + auth.getCerts().toArray()[0].toString());
                }
                URI uri = new URI(apiURL);
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                // need this load to initialize the key store, and allow for the
                // subsequent set certificate entry
                ks.load(null, null);
                int i = 0;
                for (X509Certificate cert : auth.getCerts()) {
                    ks.setCertificateEntry(uri.toASCIIString() + i, cert);
                    i++;
                }
                TrustManagerFactory tmfactory = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmfactory.init(ks);
                x509TrustManager = null;
                for (TrustManager trustManager : tmfactory.getTrustManagers()) {
                    if (trustManager instanceof X509TrustManager) {
                        x509TrustManager = (X509TrustManager) trustManager;
                        if (auth.listener != null) {
                            auth.listener.getLogger().println(
                                    "Auth - x509 trust mgr certs "
                                            + Arrays.toString(x509TrustManager
                                                    .getAcceptedIssuers()));
                        }
                        break;
                    }
                }

            } catch (Throwable t) {
                if (auth.listener != null)
                    t.printStackTrace(auth.listener.getLogger());
            }
        }
        return x509TrustManager;
    }

    public static void resetLocalTrustStore() {
        x509TrustManager = null;
    }

    public boolean allowCertificate(final X509Certificate[] certificateChain) {
        // this will be called if the trustManager.checkServerTrusted call fails
        // in openshift-restclient-java;
        // we are in this path for the "handle untrusted certifcates" or
        // "skip tls verify" path
        if (this.listener != null) {
            listener.getLogger().println(
                    "Auth - allowCertificate with incoming chain of len  "
                            + certificateChain.length);
        }

        // means we are a skip tls equivalent run
        if (skipTls) {
            if (this.listener != null)
                listener.getLogger().println(
                        "Auth - in skip tls mode, returning true");
            return true;
        }

        // we use a local trust store when we have to make direct http get's and
        // bypass the openshift-restclient-java
        if (x509TrustManager != null) {
            try {
                x509TrustManager.checkServerTrusted(certificateChain, "RSA");
                if (listener != null)
                    listener.getLogger().println(
                            "Auth - local trust mgr check server passed");
                return true;
            } catch (Throwable t) {
                if (listener != null)
                    t.printStackTrace(listener.getLogger());
            }
        }
        // TODO
        // the openshift/jboss eclipse plugins dump the cert's contents and
        // prompt the user to
        // accept based on inspecting the contents like a browser does; don't
        // see yet a good way to do something similar within a build step
        // (maybe pattern matching but that seems kludgy)

        return false;
    }

    public boolean allowHostname(String hostname, SSLSession sslSession) {
        // while the old openshift-restclient ISSLCertificateCallback method
        // still has an allowHostname,
        // we added support there so that we could seed the underlying okhttp3
        // code to use its default
        // hostname verifier; as such, this method should only be called if we
        // are in skip tls mode, in
        // which case we will bypass the okhttp3 hostname verification (it does
        // not have a skip tls options)
        if (listener != null)
            listener.getLogger().println(
                    "Auth - allowHostname getting called, skip tls is "
                            + skipTls);
        if (skipTls)
            return true;
        else {
            // should not be getting called if skip tls is false ... err on side
            // of safety, refuse the hostname
            LOGGER.info("allowHostname getting called but we are not skipping tls; returning false; investigate openshift-restclient-java and okhttp3");
            return false;
        }
    }

    public boolean useCert() {
        return certs != null && certs.size() > 0 && !skipTls;
    }

    public Collection<X509Certificate> getCerts() {
        return certs;
    }

    public static String pullTokenFromFile(File f, TaskListener listener) {
        FileInputStream fis = null;
        String authToken = null;
        boolean verbose = listener != null;
        try {
            fis = new FileInputStream(f);
            ArrayList<Integer> al = new ArrayList<Integer>();
            int rawbyte = -1;
            while ((rawbyte = fis.read()) != -1) {
                al.add(rawbyte);
            }

            byte[] buf = new byte[al.size()];
            for (int i = 0; i < al.size(); i++) {
                buf[i] = (byte) al.get(i).intValue();
            }
            synchronized (Auth.class) {
                if (authToken == null || authToken.length() == 0) {
                    authToken = new String(buf);
                }
            }

        } catch (FileNotFoundException e) {
            if (verbose)
                e.printStackTrace(listener.getLogger());
        } catch (IOException e) {
            if (verbose)
                e.printStackTrace(listener.getLogger());
        } finally {
            try {
                fis.close();
            } catch (Throwable e) {
            }
        }
        return authToken;
    }

    public static String deriveBearerToken(String at, TaskListener listener,
            boolean verbose, Map<String, String> env) {
        // the scm path may call this without a listener
        if (listener == null)
            verbose = false;
        String authToken = at;
        // order of precedence is auth token set at the build step, then when
        // set as a parameter to the build;
        // then as a global setting

        if (verbose)
            listener.getLogger().println("\n\n");

        if (authToken == null) {
            if (verbose)
                listener.getLogger().println("Auth authToken null");
        } else {
            if (verbose)
                listener.getLogger().println(
                        "Auth authToken len " + authToken.length());
        }
        if (authToken == null || authToken.length() == 0) {
            if (env != null) {
                // global properties under manage jenkins
                authToken = env.get("AUTH_TOKEN");
                if (authToken != null && authToken.length() > 0) {
                    if (verbose)
                        listener.getLogger().println(
                                "Auth token from global env vars " + authToken);
                    File f = new File(authToken);
                    if (f.exists()) {
                        if (verbose)
                            listener.getLogger().println(
                                    "Auth file exists " + f.getAbsolutePath());
                        authToken = pullTokenFromFile(f, listener);
                    }
                    return authToken;
                }
            }

            File f = new File(AUTH_FILE);
            if (verbose)
                listener.getLogger().println("Auth opened file object " + f);
            if (f.exists()) {
                if (verbose)
                    listener.getLogger().println(
                            "Auth file exists " + f.getAbsolutePath());
                authToken = pullTokenFromFile(f, listener);
            } else {
                if (verbose)
                    listener.getLogger().println(
                            "Auth file for auth token " + f.toString()
                                    + " does not exist");
            }
        }
        return authToken;
    }

    /*
     * public static String deriveBearerToken(AbstractBuild<?, ?> build, String
     * at, TaskListener listener, boolean verbose) { Map<String,String> vars =
     * null; EnvVars env = null; if (build != null) { vars =
     * build.getBuildVariables(); try { env = build.getEnvironment(listener); }
     * catch (IOException e) { if (verbose && listener != null)
     * e.printStackTrace(listener.getLogger()); } catch (InterruptedException e)
     * { if (verbose && listener != null)
     * e.printStackTrace(listener.getLogger()); } } return deriveBearerToken(at,
     * listener, verbose, vars, env); }
     * 
     * public static String deriveBearerToken(Run<?, ?> run, String at,
     * TaskListener listener, boolean verbose) { // no BuildVariables()
     * equivalent with Run obj's Map<String,String> vars = null; EnvVars env =
     * null; if (run != null) { try { env = run.getEnvironment(listener); }
     * catch (IOException e) { if (verbose && listener != null)
     * e.printStackTrace(listener.getLogger()); } catch (InterruptedException e)
     * { if (verbose && listener != null)
     * e.printStackTrace(listener.getLogger()); } } return deriveBearerToken(at,
     * listener, verbose, vars, env); }
     */
    public static String deriveCA(String ca, TaskListener listener,
            boolean verbose) {
        String caCert = ca;
        if (verbose && listener != null)
            listener.getLogger().println("\n\n");

        if (caCert == null) {
            if (verbose && listener != null)
                listener.getLogger().println("CA Cert null");
        } else {
            if (verbose && listener != null)
                listener.getLogger().println("CA Cert len " + caCert.length());
        }
        if (caCert == null || caCert.length() == 0) {
            File f = new File(CERT_FILE);
            if (verbose && listener != null)
                listener.getLogger().println("Cert opened file object " + f);
            if (f.exists()) {
                if (verbose && listener != null)
                    listener.getLogger().println(
                            "Cert file exists " + f.getAbsolutePath());
                FileInputStream fis = null;
                ObjectInputStream ois = null;
                try {
                    fis = new FileInputStream(f);
                    ArrayList<Integer> al = new ArrayList<Integer>();
                    int rawbyte = -1;
                    while ((rawbyte = fis.read()) != -1) {
                        al.add(rawbyte);
                    }

                    byte[] buf = new byte[al.size()];
                    for (int i = 0; i < al.size(); i++) {
                        buf[i] = (byte) al.get(i).intValue();
                    }
                    synchronized (Auth.class) {
                        if (caCert == null || caCert.length() == 0) {
                            caCert = new String(buf);
                        }
                    }

                } catch (FileNotFoundException e) {
                    if (verbose && listener != null)
                        e.printStackTrace(listener.getLogger());
                } catch (IOException e) {
                    if (verbose && listener != null)
                        e.printStackTrace(listener.getLogger());
                } finally {
                    try {
                        fis.close();
                    } catch (Throwable e) {
                    }
                }
            } else {
                if (verbose && listener != null)
                    listener.getLogger().println(
                            "Cert file " + f.toString() + " does not exist");
            }
        }

        return caCert;
    }

    private static InputStream getInputStreamFromDataOrFile(String data,
            File file) throws FileNotFoundException,
            UnsupportedEncodingException {
        // user provided data takes precedence
        if (data != null) {
            // these worked in testing with pasting the contents of
            // /run/secrets/kubernetes.io/serviceaccount/ca.crt:
            // - ByteArrayInputStream(data.getBytes("US-ASCII"));
            // - ByteArrayInputStream(data.getBytes());
            // these did not work:
            // -
            // ByteArrayInputStream(Base64.getEncoder().encode(data.getBytes()));
            // - Base64InputStream(new ByteArrayInputStream(data.getBytes()));
            // ... this one came directly from the k8s plugin
            return new ByteArrayInputStream(data.getBytes());
        }
        if (file != null) {
            return new FileInputStream(file);
        }
        return null;
    }

    private static Collection<X509Certificate> createCert(File caCertFile,
            String certString, TaskListener listener, String apiURL)
            throws Exception {
        if (listener != null && certString != null) {
            listener.getLogger().println(
                    "Auth - using user inputted cert string");
        }
        InputStream pemInputStream = null;
        try {
            pemInputStream = getInputStreamFromDataOrFile(certString,
                    caCertFile);
            CertificateFactory certFactory = CertificateFactory
                    .getInstance("X509");
            return (Collection<X509Certificate>) certFactory
                    .generateCertificates(pemInputStream);
        } finally {
            if (pemInputStream != null) {
                pemInputStream.close();
            }
        }
    }

}
