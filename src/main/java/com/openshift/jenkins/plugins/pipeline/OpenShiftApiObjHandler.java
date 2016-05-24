package com.openshift.jenkins.plugins.pipeline;



import java.util.HashMap;
import java.util.Map;


public class OpenShiftApiObjHandler  {
	//POST https://localhost:8443/apis/extensions/v1beta1/namespaces/test/jobs
	//POST https://localhost:8443/oapi/v1/namespaces/test/imagestreams
    public final static String api = "/api";
    public final static String oapi = "/oapi";
    public final static String apis = "/apis";

    public static final Map<String, String[]> apiMap;
    static
    {
    	// a starter set of API endpoints, which will be updated via calls
    	// to fetchApiJsonFromGithub(boolean, TaskListener, Map<String, String>, String)
    	// and importJsonOfApiTypes(boolean, TaskListener, Map<String, String>, String, String)

        // OpenShift API endpoints
        apiMap = new HashMap<String, String[]>();
    	apiMap.put("BuildConfig", new String[]{oapi, "buildconfigs"});
    	apiMap.put("Build", new String[]{oapi, "builds"});
    	apiMap.put("DeploymentConfigRollback", new String[]{oapi, "deploymentconfigrollbacks"});
    	apiMap.put("DeploymentConfig", new String[]{oapi, "deploymentconfigs"});
    	apiMap.put("ImageStreamMapping", new String[]{oapi, "imagestreammappings"});
    	apiMap.put("ImageStream", new String[]{oapi, "imagestreams"});
    	apiMap.put("LocalResourceAccessReview", new String[]{oapi, "localresourceaccessreviews"});
    	apiMap.put("LocalSubjectAccessReview", new String[]{oapi, "localsubjectaccessreviews"});
    	apiMap.put("Policy", new String[]{oapi, "policies"});
    	apiMap.put("PolicyBinding", new String[]{oapi, "policybindings"});
    	//apiMap.put("Template", new String[]{oapi, "processedtemplates"}); // Different from templates?
    	apiMap.put("ResourceAccessReview", new String[]{oapi, "resourceaccessreviews"});
    	apiMap.put("RoleBinding", new String[]{oapi, "rolebindings"});
    	apiMap.put("Role", new String[]{oapi, "roles"});
    	apiMap.put("Route", new String[]{oapi, "routes"});
    	apiMap.put("SubjectAccessReview", new String[]{oapi, "subjectaccessreviews"});
    	apiMap.put("Template", new String[]{oapi, "templates"});

        // Kubernetes API endpoints
    	apiMap.put("Binding", new String[]{api, "bindings"});
    	apiMap.put("Endpoint", new String[]{api, "endpoints"});
    	apiMap.put("Event", new String[]{api, "events"});
    	apiMap.put("LimitRange", new String[]{api, "limitranges"});
    	apiMap.put("PersistentVolumeClaim", new String[]{api, "persistentvolumeclaims"});
    	apiMap.put("Pod", new String[]{api, "pods"});
    	apiMap.put("PodTemplate", new String[]{api, "podtemplates"});
    	apiMap.put("ReplicationController", new String[]{api, "replicationcontrollers"});
    	apiMap.put("ResourceQuota", new String[]{api, "resourcequotas"});
    	apiMap.put("Secret", new String[]{api, "secrets"});
    	apiMap.put("ServiceAccount", new String[]{api, "serviceaccounts"});
    	apiMap.put("Service", new String[]{api, "services"});
    	apiMap.put("Job", new String[]{apis, "jobs"});
    }
    

}
