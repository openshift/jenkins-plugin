package com.openshift.openshiftjenkinsbuildutils;

//import io.fabric8.kubernetes.api.ExceptionResponseMapper;
//import io.fabric8.kubernetes.api.KubernetesFactory;
//import io.fabric8.utils.cxf.AuthorizationHeaderFilter;
//import io.fabric8.utils.cxf.WebClients;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
//import java.net.URI;
import java.util.ArrayList;
//import java.util.List;




import java.util.Map;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

//import org.apache.cxf.configuration.security.AuthorizationPolicy;
//import org.apache.cxf.jaxrs.client.WebClient;
//import org.apache.cxf.message.Message;
//import org.apache.cxf.transport.http.HTTPConduit;
//import org.apache.cxf.transport.http.auth.HttpAuthSupplier;
//import org.csanchez.jenkins.plugins.kubernetes.BearerTokenCredential;
//import org.csanchez.jenkins.plugins.kubernetes.BearerTokenCredentialImpl;
//
//import com.cloudbees.plugins.credentials.CredentialsScope;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.jaxrs.cfg.Annotations;
//import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

public class Auth {
	private static final String AUTH_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/token";
	private static final String CERT_FILE = "/run/secrets/kubernetes.io/serviceaccount/ca.crt";
	public static final String CERT_ARG = " --certificate-authority=/run/secrets/kubernetes.io/serviceaccount/ca.crt ";
	private static boolean useCert = false;
	
	static {
		File f = new File(CERT_FILE);
		if (f.exists()) {
			useCert = true;
		}
	}
	
	public static boolean useCert() {
		return useCert;
	}

	public static String deriveAuth(AbstractBuild<?, ?> build, String at, TaskListener listener, boolean verbose) {
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
    		if (build != null) {
    			// params from within the job definition, lowest level 
    			Map<String,String> vars = build.getBuildVariables();
    			authToken = vars.get("AUTH_TOKEN");
    			if (authToken != null && authToken.length() > 0) {
    				if (verbose) 
    					listener.getLogger().println("Auth token from build vars " + authToken);
    				return authToken;
    			}
    			
    			// global properties under manage jenkins
        		EnvVars env = null;
        		try {
    				env = build.getEnvironment(listener);
    				authToken = env.get("AUTH_TOKEN");
    				if (authToken != null && authToken.length() > 0) {
    					if (verbose) 
    						listener.getLogger().println("Auth token from global env vars " + authToken);
    					return authToken;
    				}
    			} catch (IOException e1) {
    				if (verbose)
    					e1.printStackTrace(listener.getLogger());
    			} catch (InterruptedException e1) {
    				if (verbose)
    					e1.printStackTrace(listener.getLogger());
    			}
    		}
    		
    		
    		File f = new File(AUTH_FILE);
    		if (verbose)
    			listener.getLogger().println("Auth opened file object " + f);
    		if (f.exists()) {
    			if (verbose)
        			listener.getLogger().println("Auth file exists " + f.getAbsolutePath());
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
    	    			if (authToken == null || authToken.length() == 0) {
    	    				authToken = new String(buf);
    	    			}
    	    		}
    				
    			} catch (FileNotFoundException e) {
					e.printStackTrace(listener.getLogger());
				} catch (IOException e) {
					e.printStackTrace(listener.getLogger());
				} finally {
    				try {
						fis.close();
					} catch (Throwable e) {
					}
    			}
    		} else {
    			listener.getLogger().println("Auth file for auth token " + f.toString() + " does not exist");
    		}
    	}
		return authToken;
	}
	
	public static String deriveCA(String ca, TaskListener listener, boolean verbose) {
		String caCert = ca;
		if (verbose)
			listener.getLogger().println("\n\n");
		
		if (caCert == null) {
			if (verbose)
				listener.getLogger().println("CA Cert null");
		} else {
			if (verbose)
				listener.getLogger().println("CA Cert len " + caCert.length());
		}
		if (caCert == null || caCert.length() == 0) {
			File f = new File(CERT_FILE);
			if (verbose)
				listener.getLogger().println("Cert opened file object " + f);
			if (f.exists()) {
    			if (verbose)
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
					e.printStackTrace(listener.getLogger());
				} catch (IOException e) {
					e.printStackTrace(listener.getLogger());
				} finally {
    				try {
						fis.close();
					} catch (Throwable e) {
					}
    			}
			} else {
				listener.getLogger().println("Cert file " + f.toString() + " does not exist");
			}
		}
		
		return caCert;
	}
	
//	public static WebClient getAuthorizedClient(String svcAddr, String at, String ca, TaskListener listener, boolean verbose) {
//		listener.getLogger().println("GGMGGM use cert " + useCert);
//		String authToken = deriveAuth(at, listener, verbose);
//		String caCertData = deriveCA(ca, listener, verbose);
//        List<Object> providers = createProviders();
//        AuthorizationHeaderFilter authorizationHeaderFilter = new AuthorizationHeaderFilter();
//        providers.add(authorizationHeaderFilter);
//		WebClient webClient = WebClient.create(svcAddr, providers);
//		
//		if (authToken != null) {
//			final BearerTokenCredentialImpl credentials = new BearerTokenCredentialImpl(CredentialsScope.USER, null, null, authToken);
//            final HTTPConduit conduit = WebClient.getConfig(webClient).getHttpConduit();
//            conduit.setAuthSupplier(new HttpAuthSupplier() {
//                @Override
//                public boolean requiresRequestCaching() {
//                    return false;
//                }
//
//                @Override
//                public String getAuthorization(AuthorizationPolicy authorizationPolicy, URI uri, Message message, String s) {
//                    return "Bearer " + ((BearerTokenCredential) credentials).getToken();
//                }
//            });
//		}
//		
////        if (skipTlsVerify) {
////            WebClients.disableSslChecks(webClient);
////        }
//
//        if (caCertData != null) {
//            WebClients.configureCaCert(webClient, caCertData, null);
//        }
//        
//		return webClient;
//	}
//    private static List<Object> createProviders() {
//        List<Object> providers = new ArrayList<Object>();
//        Annotations[] annotationsToUse = JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS;
//        ObjectMapper objectMapper = KubernetesFactory.createObjectMapper();
//        providers.add(new JacksonJaxbJsonProvider(objectMapper, annotationsToUse));
//        providers.add(new KubernetesFactory.PlainTextJacksonProvider(objectMapper, annotationsToUse));
//        providers.add(new ExceptionResponseMapper());
//        //providers.add(new JacksonIntOrStringConfig(objectMapper));
//        return providers;
//    }
}
