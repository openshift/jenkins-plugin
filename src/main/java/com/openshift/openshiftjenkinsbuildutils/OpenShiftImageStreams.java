package com.openshift.openshiftjenkinsbuildutils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLSession;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.internal.restclient.http.UrlConnectionHttpClientBuilder;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ISSLCertificateCallback;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.http.IHttpClient;
import com.openshift.restclient.images.DockerImageURI;
import com.openshift.restclient.model.IImageStream;

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
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
	public OpenShiftImageStreams(String imageStreamName, String tag, String apiURL, String nameSpace, String authToken) {
    	this.imageStreamName = imageStreamName;
    	this.tag = tag;
    	this.apiURL = apiURL;
    	this.nameSpace = nameSpace;
    	this.authToken = authToken;
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

	private String getCommitId(TaskListener listener) {
    	// get oc client (sometime REST, sometimes Exec of oc command
    	URL url = null;
    	try {
			url = new URL(apiURL + "/oapi/v1/namespaces/test/imagestreams/" + imageStreamName);
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
		}
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, this, null, null);
		urlClient.setAuthorizationStrategy(new TokenAuthorizationStrategy(authToken));
		String response = null;
		try {
			response = urlClient.get(url, 2 * 60 * 1000);
		} catch (SocketTimeoutException e1) {
			e1.printStackTrace(listener.getLogger());
		} catch (HttpClientException e1) {
			e1.printStackTrace(listener.getLogger());
		}
		
		listener.getLogger().println("OpenShiftImageStreams response from rest call " + response);
		String commitId = null;
		if (response != null) {
			ModelNode node = ModelNode.fromJSONString(response);
			listener.getLogger().println("OpenShiftImageStreams after json processing " + node);
			if (node != null) {
				ModelNode status = node.get("status");
				listener.getLogger().println("OpenShiftImageStreams status element " + status);
			
				if (status != null) {
					ModelNode tags = status.get("tags");
					listener.getLogger().println("OpenShiftImageStreams tags element " + tags);
					if (tags != null) {
						List<ModelNode> tagWrappers = tags.asList();
						listener.getLogger().println("OpenShiftImageStreams tag wrappers " + tagWrappers);
						if (tagWrappers != null) {
							for (ModelNode tagWrapper : tagWrappers) {
								listener.getLogger().println("OpenShiftImageStreams tag wrapper " + tagWrapper);
								ModelNode tag = tagWrapper.get("tag");
								ModelNode items = tagWrapper.get("items");
								listener.getLogger().println("OpenShiftImageStreams tag  " + tag.asString() + ", comparing to " + this.tag + ",  items " + items);
								if (tag != null && tag.asString().equals(this.tag) && items != null) {
									List<ModelNode> itemWrappers = items.asList();
									for (ModelNode itemWrapper : itemWrappers) {
										ModelNode created = itemWrapper.get("created");
										ModelNode dockerImageReference = itemWrapper.get("dockerImageReference");
										ModelNode image = itemWrapper.get("image");
										listener.getLogger().println("OpenShiftImageStreams created " + created + " dockerImg " + dockerImageReference +
												" image " + image);
										if (image != null) {
											commitId = image.asString();
											// usually the latest one is the first element, so break
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
		
		listener.getLogger().println("OpenShiftImageStreams commit ID is " + commitId);
		return commitId;

    	
//		Map<String,IImageStream> ourIms = new HashMap<String,IImageStream>();
//    	if (client != null) {
//    		// seed the auth
//        	client.setAuthorizationStrategy(new TokenAuthorizationStrategy(this.authToken));
//        	
//			long currTime = System.currentTimeMillis();
//			while (System.currentTimeMillis() < (currTime + 60000)) {
//				List<IImageStream> ims = client.list(ResourceKind.IMAGE_STREAM, nameSpace);
//				for (IImageStream is : ims) {
//					listener.getLogger().println("OpenShiftImageStreams image stream:  " + is.getName());					
//					listener.getLogger().println("OpenShiftImageStreams with annotations:  " + is.getAnnotations());					
//					listener.getLogger().println("OpenShiftImageStreams with labels:  " + is.getLabels());
//					DockerImageURI diu = is.getDockerImageRepository();
//					if (diu != null) {
//						listener.getLogger().println("OpenShiftImageStreams with docker tag:  " + diu.getTag());
//						listener.getLogger().println("OpenShiftImageStreams with abs uri:  " + diu.getAbsoluteUri());					
//						listener.getLogger().println("OpenShiftImageStreams with base uri:  " + diu.getBaseUri());
//						listener.getLogger().println("OpenShiftImageStreams with host:  " + diu.getRepositoryHost());
//						listener.getLogger().println("OpenShiftImageStreams with uri - host:  " + diu.getUriWithoutHost());
//						listener.getLogger().println("OpenShiftImageStreams with uri - tag:  " + diu.getUriWithoutTag());
//						listener.getLogger().println("OpenShiftImageStreams with user:  " + diu.getUserName());					
//					}
//					
//					
//					ourIms.put(is.getName(), is);
//				}
//				
//				if (ourIms.size() > 0) {
//					break;
//				} else {
//					listener.getLogger().println("OpenShiftImageStreams don't have any IMs yet, try again");
//					try {
//						Thread.sleep(1000);
//					} catch (InterruptedException e) {
//					}
//				}
//				
//			}
//    	} else {
//    		listener.getLogger().println("OpenShiftImageStreams could not get oc client");
//    	}
//    	return ourIms;
	}
	
	

	@Override
	public void checkout(Run<?, ?> build, Launcher launcher,
			FilePath workspace, TaskListener listener, File changelogFile,
			SCMRevisionState baseline) throws IOException, InterruptedException {
		String bldName = null;
		if (build != null)
			bldName = build.getDisplayName();
		listener.getLogger().println("OpenShiftImageStreams checkout called for " + bldName);
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
		listener.getLogger().println("OpenShiftImageStreams calcRevisionsFromBuild for " + bldName);
    	// obtain auth token from defined spot in OpenShift Jenkins image
    	authToken = Auth.deriveAuth(authToken, listener);
    	
    	String commitId = this.getCommitId(listener);
			
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null)
			currIMSState = new ImageStreamRevisionState(commitId);
		
		listener.getLogger().println("OpenShiftImageStreams calcRevisionsFromBuild returning " + currIMSState);
		
		return currIMSState;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
		listener.getLogger().println("OpenShiftImageStreams compareRemoteRevisionWith");
    	// obtain auth token from defined spot in OpenShift Jenkins image
    	authToken = Auth.deriveAuth(authToken, listener);
    	
    	String commitId = this.getCommitId(listener);
		
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null)
			currIMSState = new ImageStreamRevisionState(commitId);
		
		listener.getLogger().println("OpenShiftImageStreams compareRemoteRevisionWith comparing baseline " + baseline +
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
