package com.openshift.openshiftjenkinsbuildutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;

import hudson.model.TaskListener;

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

	public static String deriveAuth(String at, TaskListener listener) {
		String authToken = at;
		
    	if (authToken == null) {
    		listener.getLogger().println("Auth authToken null");
    	} else {
    		listener.getLogger().println("Auth authToken len " + authToken.length());
    	}
    	if (authToken == null || authToken.length() == 0) {
    		File f = new File(AUTH_FILE);
    		listener.getLogger().println("Auth opened file object " + f);
    		if (f.exists()) {
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
    			listener.getLogger().println(" Auth file for auth token " + f.toString() + " does not exist");
    		}
    	}
		return authToken;
	}
}
