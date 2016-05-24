package com.openshift.jenkins.plugins.pipeline;

import com.openshift.restclient.IClient;
import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.model.IResource;

import hudson.EnvVars;
import hudson.model.TaskListener;

public class AnnotationUtils {

	// arg1=resource type, arg2=object name
	public static final String ANNOTATION_FAILURE = "\nWARNING: Failed to annotate %s %s with job information.";

	// Jenkins env variable keys
	// This key contains the full url to the jenkins job instance that is currently running.
	public static final String BUILD_URL_ENV_KEY = "BUILD_URL";
	// this key contains the jenkins job name
	public static final String JOB_NAME = "JOB_NAME";
	// this key contains the jenkins job instance number
	public static final String BUILD_NUMBER = "BUILD_NUMBER";

	// Openshift annotation keys
	// This annotation will contain a the full url of the jenkins job instance that lead
	// to this object (e.g. build, deployment) being created.
	public static final String BUILD_URL_ANNOTATION = "openshift.io/jenkins-build-uri";

	// Adds the jenkins job information annotation to the resource object.
	// Will retry up to 3 times, 3 seconds apart, in case of update conflicts.
	public static boolean AnnotateResource(IClient client, TaskListener listener, boolean chatty, EnvVars env, IResource resource) {
		boolean annotated=false;
		String buildURL=env.get(AnnotationUtils.BUILD_URL_ENV_KEY);
		// when not running as a pipeline, the build_url key is not available, so we have to
		// construct the url from pieces.  unfortunately the jenkins hostname itself is not available
		// as an env variable in this case.
		if(buildURL==null) {
			buildURL="job/"+env.get(AnnotationUtils.JOB_NAME)+"/"+env.get(AnnotationUtils.BUILD_NUMBER);
		}
		for(int i=0;i<3;i++) {
			// anticipate update conflicts so get the latest object and retry the update 3 times.
			IResource annotatedResource=client.get(resource.getKind(),resource.getName(),resource.getNamespace());
			annotatedResource.setAnnotation(AnnotationUtils.BUILD_URL_ANNOTATION,buildURL);
			try {
				client.update(annotatedResource);
				annotated=true;
				break;
			} catch(OpenShiftException e) {
				if (chatty)
					e.printStackTrace(listener.getLogger());
			}
			try { Thread.sleep(3000); } catch(InterruptedException e){}
		}
		if(!annotated) {
			listener.getLogger().println(String.format(ANNOTATION_FAILURE, resource.getKind(), resource.getName()));
		}
		return annotated;
	}

}
