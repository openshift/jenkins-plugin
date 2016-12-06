package com.openshift.jenkins.plugins.pipeline;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.ExtensionPoint;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * This lovely piece of code arose from the need to address the following set of conditions when attempting to set the triggeredBy field in OpenShift
 * Builds to the publicly accessible URL for the Jenkins Job Build that initiated the Build:
 * - the route for the Jenkins deployment is in a different namespace than the namespace of operating over, or Jenkins is running external from OpenShift
 * - Jenkins.getInstance().getRootURL() only works when either a) the url is specifically configured in Jenkins (can't depend on this), or b) the caller is running on a Jenkins internal http thread (which build steps do not run on)
 * 
 * So a servlet filter has been inserted to fetch the root URL by intercepting HTTP requests to Jenkins 
 * 
 * @author gmontero
 *
 */

@Extension
public class JenkinsServletFilter implements Filter, ExtensionPoint {
    
    static final Logger LOGGER = Logger.getLogger(JenkinsServletFilter.class.getName());
    private static String rootURL = null;
    
    static {
        try {
            hudson.util.PluginServletFilter.addFilter(new JenkinsServletFilter());
        } catch (ServletException e) {
            LOGGER.log(Level.FINE, "ctor", e);
        }
        
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    // code borrowed from jenkins.model.Jenkins
    private  String getXForwardedHeader(HttpServletRequest req, String header, String defaultValue) {
        String value = req.getHeader(header);
        if (value != null) {
            int index = value.indexOf(',');
            return index == -1 ? value.trim() : value.substring(0,index).trim();
        }
        return defaultValue;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest req = (HttpServletRequest)request; 
            if (rootURL == null) {
                // code borrowed from jenkins.model.Jenkins
                StringBuilder buf = new StringBuilder();
                String scheme = getXForwardedHeader(req, "X-Forwarded-Proto", req.getScheme());
                buf.append(scheme).append("://");
                String host = getXForwardedHeader(req, "X-Forwarded-Host", req.getServerName());
                int index = host.indexOf(':');
                int port = req.getServerPort();
                if (index == -1) {
                    // Almost everyone else except Nginx put the host and port in separate headers
                    buf.append(host);
                } else {
                    // Nginx uses the same spec as for the Host header, i.e. hostanme:port
                    buf.append(host.substring(0, index));
                    if (index + 1 < host.length()) {
                        try {
                            port = Integer.parseInt(host.substring(index + 1));
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    // but if a user has configured Nginx with an X-Forwarded-Port, that will win out.
                }
                String forwardedPort = getXForwardedHeader(req, "X-Forwarded-Port", null);
                if (forwardedPort != null) {
                    try {
                        port = Integer.parseInt(forwardedPort);
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                if (port != ("https".equals(scheme) ? 443 : 80)) {
                    buf.append(':').append(port);
                }
                buf.append(req.getContextPath()).append('/');
                rootURL = buf.toString();
                LOGGER.info("OpenShift Pipeline: derived root URL: " + rootURL);
            }
        } finally {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {

    }

    public static final String getJenkinsRootURL() {
        return rootURL;
    }
    
}
