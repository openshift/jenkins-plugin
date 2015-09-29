package com.openshift.openshiftjenkinsbuildutils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLSession;
import javax.servlet.ServletException;
//import javax.ws.rs.core.Response;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

//import org.apache.cxf.jaxrs.client.WebClient;
import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.restclient.ISSLCertificateCallback;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.FormValidation;

public class OpenShiftImageStreams extends SCM implements ISSLCertificateCallback {

	private String imageStreamName = "nodejs-010-centos7";
	private String tag = "latest";
    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String nameSpace = "test";
    private String authToken = "";
    private String verbose = "false";
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
	public OpenShiftImageStreams(String imageStreamName, String tag, String apiURL, String nameSpace, String authToken, String verbose) {
    	this.imageStreamName = imageStreamName;
    	this.tag = tag;
    	this.apiURL = apiURL;
    	this.nameSpace = nameSpace;
    	this.authToken = authToken;
    	this.verbose = verbose;
	}

	public String getApiURL() {
		return apiURL;
	}

	public void setApiURL(String apiURL) {
		this.apiURL = apiURL;
	}

	public String getNameSpace() {
		return nameSpace;
	}

	public void setNameSpace(String nameSpace) {
		this.nameSpace = nameSpace;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}
	
	public String getImageStreamName() {
		return imageStreamName;
	}

	public void setImageStreamName(String imageStreamName) {
		this.imageStreamName = imageStreamName;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public String getVerbose() {
		return verbose;
	}

	public void setVerbose(String verbose) {
		this.verbose = verbose;
	}

	private String getCommitId(TaskListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
		
    	// get oc client (sometime REST, sometimes Exec of oc command
    	URL url = null;
    	try {
			url = new URL(apiURL + "/oapi/v1/namespaces/"+nameSpace+"/imagestreams/" + imageStreamName);
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
			return null;
		}
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, this, null, null);
    	// obtain auth token from defined spot in OpenShift Jenkins image
    	authToken = Auth.deriveAuth(authToken, listener, Boolean.parseBoolean(verbose));
		urlClient.setAuthorizationStrategy(new TokenAuthorizationStrategy(authToken));
		String response = null;
		try {
			response = urlClient.get(url, 2 * 60 * 1000);
		} catch (SocketTimeoutException e1) {
			e1.printStackTrace(listener.getLogger());
			return null;
		} catch (HttpClientException e1) {
			e1.printStackTrace(listener.getLogger());
			return null;
		}
		
		//TODO see io.fabric8.kubernetes.api.KubernetesClient.doTriggerBuild(String, String, String, String) for simple invocation form WebClient
//		WebClient webClient = Auth.getAuthorizedClient(apiURL + "/oapi/v1/namespaces/"+nameSpace+"/imagestreams/" + imageStreamName, authToken, null, listener, chatty);
//		Response resp = webClient.get();
//		listener.getLogger().println("\n\n\n GGMGGMGGM entity from web client resp is " + resp.getEntity() + " \n\n\n\n");
		
		if (chatty)
			listener.getLogger().println("\n\nOpenShiftImageStreams response from rest call " + response);
		// we will treat the OpenShiftImageStream "imageID" as the Jenkins "commitId"
		String commitId = null;
		if (response != null) {
			ModelNode node = ModelNode.fromJSONString(response);
			if (chatty)
				listener.getLogger().println("\n\nOpenShiftImageStreams after json processing " + node);
			if (node != null) {
				ModelNode status = node.get("status");
				if (chatty)
					listener.getLogger().println("\n\nOpenShiftImageStreams status element " + status);
			
				if (status != null) {
					ModelNode tags = status.get("tags");
					if (chatty)
						listener.getLogger().println("\n\nOpenShiftImageStreams tags element " + tags);
					if (tags != null) {
						List<ModelNode> tagWrappers = tags.asList();
						if (chatty)
							listener.getLogger().println("\n\nOpenShiftImageStreams tag wrappers " + tagWrappers);
						if (tagWrappers != null) {
							for (ModelNode tagWrapper : tagWrappers) {
								if (chatty)
									listener.getLogger().println("\n\nOpenShiftImageStreams tag wrapper " + tagWrapper);
								ModelNode tag = tagWrapper.get("tag");
								ModelNode items = tagWrapper.get("items");
								if (chatty)
									listener.getLogger().println("\n\nOpenShiftImageStreams tag  " + tag.asString() + ", comparing to " + this.tag + ",  items " + items);
								if (tag != null && tag.asString().equals(this.tag) && items != null) {
									List<ModelNode> itemWrappers = items.asList();
									for (ModelNode itemWrapper : itemWrappers) {
										ModelNode created = itemWrapper.get("created");
										ModelNode dockerImageReference = itemWrapper.get("dockerImageReference");
										ModelNode image = itemWrapper.get("image");
										if (chatty)
											listener.getLogger().println("\n\nOpenShiftImageStreams created " + created + " dockerImg " + dockerImageReference +
												" image " + image);
										if (image != null) {
											commitId = image.asString();
											break;
										}
									}
								}
								if (commitId != null)
									break;
							}
						}
					}
				}
			}
			
		}
		
		// always print this
		listener.getLogger().println("\n\nOpenShiftImageStreams image ID used for Jenkins 'commitId' is " + commitId);
		return commitId;
    	
	}
	
	

	@Override
	public void checkout(Run<?, ?> build, Launcher launcher,
			FilePath workspace, TaskListener listener, File changelogFile,
			SCMRevisionState baseline) throws IOException, InterruptedException {
		String bldName = null;
		if (build != null)
			bldName = build.getDisplayName();
		listener.getLogger().println("\n\nOpenShiftImageStreams checkout called for " + bldName);
		// don't need to check out into jenkins workspace ... our bld/slave images/pods deal with that 
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return null;
	}

	@Override
	public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build,
			FilePath workspace, Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {
		String bldName = null;
		if (build != null)
			bldName = build.getDisplayName();
		listener.getLogger().println("\n\nOpenShiftImageStreams calcRevisionsFromBuild for " + bldName);
    	
    	String commitId = this.getCommitId(listener);
			
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null)
			currIMSState = new ImageStreamRevisionState(commitId);
		
		listener.getLogger().println("\n\nOpenShiftImageStreams calcRevisionsFromBuild returning " + currIMSState);
		
		return currIMSState;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
		listener.getLogger().println("\n\nOpenShiftImageStreams compareRemoteRevisionWith");
    	
    	String commitId = this.getCommitId(listener);
		
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null)
			currIMSState = new ImageStreamRevisionState(commitId);
		
		listener.getLogger().println("\n\nOpenShiftImageStreams compareRemoteRevisionWith comparing baseline " + baseline +
				" with lastest " + currIMSState);
		boolean changes = false;
		if (baseline != null && baseline instanceof ImageStreamRevisionState && currIMSState != null)
			changes = !currIMSState.equals(baseline);
			
		return new PollingResult(baseline, currIMSState, changes ? Change.SIGNIFICANT : Change.NONE);
    				        		
	}

	@Override
	public SCMDescriptor<?> getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
                getClass());
	}


    @Override
	public boolean requiresWorkspaceForPolling() {
    	// our openshift itself and the cloud/master/slave plugin handles ramping up pods for builds ... those are our
    	// "workspace"
    	return false;
	}


	@Extension
    public static class DescriptorImpl extends SCMDescriptor {

        public DescriptorImpl() {
            super(OpenShiftImageStreams.class, null);
            load();
        }

        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set apiURL");
            return FormValidation.ok();
        }

        public FormValidation doCheckNameSpace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set nameSpace");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckImageStreamName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set imageStreamName");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckTag(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set tag");
            return FormValidation.ok();
        }
        
		@Override
		public String getDisplayName() {
			return "Monitor OpenShift ImageStreams";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {
			save();
			return super.configure(req, json);
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
