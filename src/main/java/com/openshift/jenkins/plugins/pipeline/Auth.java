package com.openshift.jenkins.plugins.pipeline;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import java.util.Map;

import javax.net.ssl.SSLSession;

import com.openshift.restclient.ISSLCertificateCallback;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;


public class Auth implements ISSLCertificateCallback {
	private static final String AUTH_FILE = "/run/secrets/kubernetes.io/serviceaccount/token";
	private static final String CERT_FILE = "/run/secrets/kubernetes.io/serviceaccount/ca.crt";
	public static final String CERT_ARG = " --certificate-authority=/run/secrets/kubernetes.io/serviceaccount/ca.crt ";
	
	private X509Certificate cert = null;
	private TaskListener listener = null;
	private Auth(X509Certificate cert, TaskListener listener) {
		this.cert = cert;
		this.listener = listener;
	}
	
	public static Auth createInstance(TaskListener listener, String apiURL, EnvVars env) {
		Auth auth = null;
		File f = new File(CERT_FILE);
		if ((f.exists() || env.get("CA_CERT") != null) && env.get("SKIP_TLS") == null) {
			if (listener != null)
				listener.getLogger().println("Auth - cert file exists - " + f.exists() + ", CA_CERT - " + env.get("CA_CERT") + "\n skip tls - " + env.get("SKIP_TLS"));
			try {
				auth = new Auth(createCert(f, env.get("CA_CERT"), listener, apiURL), listener);
			} catch (Exception e) {
				if (listener != null)
					e.printStackTrace(listener.getLogger());
				auth = new Auth(null, listener);
			}
		} else {
			auth = new Auth(null, listener);
		}
		return auth;
	}
	
	@Override
	public boolean allowCertificate(final X509Certificate[] certificateChain) {
		// this will be called if the trustManager.checkServerTrusted call fails in openshift-restclient-java;
		// we are in this path for the "handle untrusted certifcates" or "skip tls verify" path
		if (this.listener != null) {
			listener.getLogger().println("Auth - allowCertificate with incoming chain of len  " + certificateChain.length);
		}

		// means we are a skip tls equivalent run
		if (this.cert == null) {
			if (this.listener != null)
				listener.getLogger().println("Auth - in skip tls mode, returning true");
			return true;
		}

		
		//TODO
		// the openshift/jboss eclipse plugins dump the cert's contents and prompt the user to 
		// accept based on inspecting the contents like a browser does; don't 
		// see yet a good way to do something similar within a build step 
		// (maybe pattern matching but that seems kludgy)
		
		return false;
	}
	

	@Override
	public boolean allowHostname(String hostname, SSLSession sslSession) {
		//mimic SSLCertificateCallback implementation from jbosstools-openshift repo - we can noop
		//also lines up with what was observed in k8s jennkins plugin
		return true;
	}
	public boolean useCert() {
		return cert != null;
	}
	
	public X509Certificate getCert() {
		return cert;
	}
	
	private static String pullTokenFromFile(File f, TaskListener listener) {
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
				buf[i] = (byte)al.get(i).intValue();
			}
    		synchronized(Auth.class) {
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
	
	public static String deriveBearerToken(String at, TaskListener listener, boolean verbose, Map<String,String> vars, EnvVars env) {
		// the scm path may call this without a listener
		if (listener == null)
			verbose = false;
		String authToken = at;
		// order of precedence is auth token set at the build step, then when set as a parameter to the build;
		// then as a global setting
		
		if (verbose)
			listener.getLogger().println("\n\n");
		
    	if (authToken == null) {
    		if (verbose)
    			listener.getLogger().println("Auth authToken null");
    	} else {
    		if (verbose)
    			listener.getLogger().println("Auth authToken len " + authToken.length());
    	}
    	if (authToken == null || authToken.length() == 0) {
    		if (vars != null) {
    			// params from within the job definition, lowest level 
    			authToken = vars.get("AUTH_TOKEN");
    			if (authToken != null && authToken.length() > 0) {
    				if (verbose) 
    					listener.getLogger().println("Auth token from build vars " + authToken);
    				File f = new File(authToken);
    				if (f.exists()) {
    	    			if (verbose)
    	        			listener.getLogger().println("Auth file exists " + f.getAbsolutePath());
    	    			authToken = pullTokenFromFile(f, listener);    					
    				} else {
    					return authToken;
    				}
    			}
    		}
    		
    		if (env != null) {
    			// global properties under manage jenkins
				authToken = env.get("AUTH_TOKEN");
				if (authToken != null && authToken.length() > 0) {
					if (verbose) 
						listener.getLogger().println("Auth token from global env vars " + authToken);
					File f = new File(authToken);
					if (f.exists()) {
		    			if (verbose)
		        			listener.getLogger().println("Auth file exists " + f.getAbsolutePath());
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
        			listener.getLogger().println("Auth file exists " + f.getAbsolutePath());
    			authToken = pullTokenFromFile(f, listener);
    		} else {
    			if (verbose)
    				listener.getLogger().println("Auth file for auth token " + f.toString() + " does not exist");
    		}
    	}
		return authToken;
	}

	public static String deriveBearerToken(AbstractBuild<?, ?> build, String at, TaskListener listener, boolean verbose) {
		Map<String,String> vars = null;
		EnvVars env = null;
		if (build != null) {
			vars = build.getBuildVariables();
			try {
				env = build.getEnvironment(listener);
			} catch (IOException e) {
				if (verbose && listener != null)
					e.printStackTrace(listener.getLogger());
			} catch (InterruptedException e) {
				if (verbose && listener != null)
					e.printStackTrace(listener.getLogger());
			}
		}
		return deriveBearerToken(at, listener, verbose, vars, env);
	}
	
	public static String deriveBearerToken(Run<?, ?> run, String at, TaskListener listener, boolean verbose) {
		// no BuildVariables() equivalent with Run obj's
		Map<String,String> vars = null;
		EnvVars env = null;
		if (run != null) {
			try {
				env = run.getEnvironment(listener);
			} catch (IOException e) {
				if (verbose && listener != null)
					e.printStackTrace(listener.getLogger());
			} catch (InterruptedException e) {
				if (verbose && listener != null)
					e.printStackTrace(listener.getLogger());
			}
		}
		return deriveBearerToken(at, listener, verbose, vars, env);
	}
	
	public static String deriveCA(String ca, TaskListener listener, boolean verbose) {
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
        			listener.getLogger().println("Cert file exists " + f.getAbsolutePath());
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
    					buf[i] = (byte)al.get(i).intValue();
    				}
    	    		synchronized(Auth.class) {
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
					listener.getLogger().println("Cert file " + f.toString() + " does not exist");
			}
		}
		
		return caCert;
	}
	
    private static InputStream getInputStreamFromDataOrFile(String data, File file) throws FileNotFoundException, UnsupportedEncodingException {
    	// user provided data takes precedence
        if (data != null) {
        	// these worked in testing with pasting the contents of 
        	// /run/secrets/kubernetes.io/serviceaccount/ca.crt:
        	// - ByteArrayInputStream(data.getBytes("US-ASCII"));
        	// - ByteArrayInputStream(data.getBytes());
        	// these did not work:
        	// - ByteArrayInputStream(Base64.getEncoder().encode(data.getBytes()));
        	// - Base64InputStream(new ByteArrayInputStream(data.getBytes())); ... this one came directly from the k8s plugin
            return new ByteArrayInputStream(data.getBytes());
        }
        if (file != null) {
            return new FileInputStream(file);
        }
        return null;
    }

    private static X509Certificate createCert(File caCertFile, String certString, TaskListener listener, String apiURL) throws Exception {
    	if (listener != null && certString != null) {
    		listener.getLogger().println("Auth - using user inputted cert string");
    	}
    	InputStream pemInputStream = getInputStreamFromDataOrFile(certString, caCertFile);
		CertificateFactory certFactory = CertificateFactory.getInstance("X509");
		X509Certificate cert = (X509Certificate) certFactory.generateCertificate(pemInputStream);
		return cert;        
    }
	
}
