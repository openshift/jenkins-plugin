package com.openshift.openshiftjenkinsbuildutils;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.internal.restclient.model.ImageStream;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ISSLCertificateCallback;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.ICapability;
import com.openshift.restclient.model.IReplicationController;

import javax.net.ssl.SSLSession;
import javax.servlet.ServletException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * OpenShift {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link OpenShiftImageTagger} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Gabe Montero
 */
public class OpenShiftImageTagger extends Builder implements ISSLCertificateCallback {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String testTag = "origin-nodejs-sample:latest";
    private String prodTag = "origin-nodejs-sample:prod";
    private String nameSpace = "test";
    private String authToken = "";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftImageTagger(String apiURL, String testTag, String prodTag, String nameSpace, String authToken) {
        this.apiURL = apiURL;
        this.testTag = testTag;
        this.nameSpace = nameSpace;
        this.prodTag = prodTag;
        this.authToken = authToken;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApiURL() {
		return apiURL;
	}

	public String getTestTag() {
		return testTag;
	}

	public String getNameSpace() {
		return nameSpace;
	}
	
	public String getProdTag() {
		return prodTag;
	}
	
	public String getAuthToken() {
		return authToken;
	}

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	System.setProperty(ICapability.OPENSHIFT_BINARY_LOCATION, Constants.OC_LOCATION);
    	listener.getLogger().println("OpenShiftImageTagger in perform");
    	
    	// obtain auth token from defined spot in OpenShift Jenkins image
    	authToken = Auth.deriveAuth(authToken, listener);
    	    	
    	// get oc client (sometime REST, sometimes Exec of oc command
    	IClient client = new ClientFactory().create(apiURL, this);
    	
    	if (client != null) {
    		// seed the auth
        	client.setAuthorizationStrategy(new TokenAuthorizationStrategy(this.authToken));
        	
        	//tag image
			boolean tagDone = false;
//			BinaryTagImageInvocation runner = new BinaryTagImageInvocation(testTag, prodTag, nameSpace, client);
//			InputStream logs = null;
//			// create stream and copy bytes
//			try {
//				logs = new BufferedInputStream(runner.getLogs(true));
//				int b;
//				while ((b = logs.read()) != -1) {
//					listener.getLogger().write(b);
//				}
//				tagDone = true;
//			} catch (Throwable e) {
//				e.printStackTrace(listener.getLogger());
//			} finally {
//				runner.stop();
//				try {
//					if (logs != null)
//						logs.close();
//				} catch (Throwable e) {
//					e.printStackTrace(listener.getLogger());
//				}
//			}
			StringTokenizer st = new StringTokenizer(prodTag, ":");
			String imageStreamName = null;
			String tagName = null;
			if (st.countTokens() > 1) {
				imageStreamName = st.nextToken();
				tagName = st.nextToken();
				ImageStream isImpl = client.get(ResourceKind.IMAGE_STREAM, imageStreamName, nameSpace);
				ModelNode isNode = isImpl.getNode();
				listener.getLogger().println("OpenShiftImageTagger isNode " + isNode.asString());
				ModelNode isSpec = isNode.get("spec");
				ModelNode isTags = isSpec.get("tags");
				String tagStr="{\"name\": \""+tagName+"\",\"from\": {\"kind\": \"ImageStreamTag\",\"name\": \""+testTag+"\"}}";
				ModelNode isTag = ModelNode.fromJSONString(tagStr);
				isTags.add(isTag);
				listener.getLogger().println("OpenShiftImageTagger isTags after " + isTags.asString());
	        	// do the REST / HTTP PUT call
	        	URL url = null;
	        	try {
	        		listener.getLogger().println("OpenShiftImageTagger PUT URI " + "/oapi/v1/namespaces/"+nameSpace+"/imagestreams/" + imageStreamName);
	    			url = new URL(apiURL + "/oapi/v1/namespaces/"+ nameSpace+"/imagestreams/" + imageStreamName);
	    		} catch (MalformedURLException e1) {
	    			e1.printStackTrace(listener.getLogger());
	    		}
	    		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
	    				null, "application/json", null, this, null, null);
	    		urlClient.setAuthorizationStrategy(new TokenAuthorizationStrategy(authToken));
	    		String response = null;
	    		try {
	    			response = urlClient.put(url, 10 * 1000, isImpl);
	    			listener.getLogger().println("OpenShiftImageTagger REST PUT response " + response);
	    			tagDone = true;
	    		} catch (SocketTimeoutException e1) {
	    			e1.printStackTrace(listener.getLogger());
	    		} catch (HttpClientException e1) {
	    			e1.printStackTrace(listener.getLogger());
	    		}
			}
			
			
			if (tagDone) {
				listener.getLogger().println("OpenShiftImageTagger image tagged");
				return true;
			}
			
    	} else {
    		listener.getLogger().println("OpenShiftImageTagger could not get oc client");
    		return false;
    	}

    	return false;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftImageTagger}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the various fields.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set apiURL");
            return FormValidation.ok();
        }

        public FormValidation doCheckTestTag(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set testTag");
            return FormValidation.ok();
        }

        public FormValidation doCheckProdTag(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set prodTag");
            return FormValidation.ok();
        }

        public FormValidation doCheckNameSpace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set nameSpace");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Tag an image in OpenShift";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
            save();
            return super.configure(req,formData);
        }

    }

	@Override
	public boolean allowCertificate(X509Certificate[] chain) {
		return true;
	}

	@Override
	public boolean allowHostname(String hostname, SSLSession session) {
		return true;
	}
}

