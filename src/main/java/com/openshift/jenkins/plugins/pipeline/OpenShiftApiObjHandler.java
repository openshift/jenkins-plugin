package com.openshift.jenkins.plugins.pipeline;


import hudson.model.TaskListener;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.jboss.dmr.ModelNode;
import org.yaml.snakeyaml.Yaml;

import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.restclient.IClient;
import com.openshift.restclient.model.IResource;

public abstract class OpenShiftApiObjHandler extends OpenShiftBaseStep {
	//POST https://localhost:8443/apis/extensions/v1beta1/namespaces/test/jobs
	//POST https://localhost:8443/oapi/v1/namespaces/test/imagestreams
    protected final static String api = "/api";
    protected final static String oapi = "/oapi";
    protected final static String apis = "/apis";

    protected static final Map<String, String[]> apiMap;
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
    
	public OpenShiftApiObjHandler(String apiURL, String namespace,
			String authToken, String verbose) {
		super(apiURL, namespace, authToken, verbose);
	}

    protected UrlConnectionHttpClient createHttpClient() {
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, auth, null, null);
		urlClient.setAuthorizationStrategy(bearerToken);
		return urlClient;
    }
    
	protected String fetchApiJsonFromApiServer(boolean chatty, TaskListener listener, 
    		Map<String,String> overrides, String apiDomain) {
    	URL url = null;
    	try {
    		url = new URL(this.getApiURL(overrides) + "/swaggerapi/" + apiDomain + "/v1");
		} catch (MalformedURLException e) {
			e.printStackTrace(listener.getLogger());
			return null;
		}
    	try {
    		return createHttpClient().get(url, 10 * 1000);
		} catch (IOException e1) {
			if (chatty)
				e1.printStackTrace(listener.getLogger());
			return null;
		}
    }
    
    protected void importJsonOfApiTypes(boolean chatty, TaskListener listener, 
    		Map<String,String> overrides, String apiDomain, String json) {
    	if (json == null)
    		return;
    	ModelNode oapis = ModelNode.fromJSONString(json);
    	ModelNode apis = oapis.get("apis");
    	List<ModelNode> apiList = apis.asList();
    	for (ModelNode api : apiList) {
    		String path = api.get("path").asString();
    		ModelNode operations = api.get("operations");
    		List<ModelNode> operationList = operations.asList();
    		for (ModelNode operation : operationList) {
    			String type = operation.get("type").asString();
    			String method = operation.get("method").asString();
    			if (type.startsWith("v1.") && method.equalsIgnoreCase("POST")) {
    				String coreType = type.substring(3);
    				if (!apiMap.containsKey(coreType)) {
        				String typeStrForURL = null;
        				String[] pathPieces = path.split("/");
        				for (String pathPiece : pathPieces) {
        					if (pathPiece.equalsIgnoreCase(coreType + "s")) {
        						typeStrForURL = pathPiece;
        						break;
        					}
        				}
        				if (typeStrForURL != null) {
        					if (chatty) listener.getLogger().println("\nOpenShiftCreator: adding from API server swagger endpoint new type " + coreType + " with url str " + typeStrForURL + " to domain " + apiDomain);
        					apiMap.put(coreType, new String[]{apiDomain, typeStrForURL});
        				}
    				}
    			}
    		}
    	}
    	
    }
    
    protected void updateApiTypes(boolean chatty, TaskListener listener, Map<String,String>overrides) {
    	// our lower level openshift-restclient-java usage here is not agreeable with the TrustManager maintained there,
    	// so we set up our own trust manager like we used to do in order to verify the server cert
    	Auth.createLocalTrustStore(getAuth(), getApiURL(overrides));
		importJsonOfApiTypes(chatty, listener, overrides, oapi, fetchApiJsonFromApiServer(chatty, listener, overrides, oapi));
		importJsonOfApiTypes(chatty, listener, overrides, api, fetchApiJsonFromApiServer(chatty, listener, overrides, api));
    	
    }
    
    protected ModelNode hydrateJsonYaml(String jsonyaml, TaskListener listener) {
    	// construct json/yaml node
    	ModelNode resources = null;
    	try {
    		resources = ModelNode.fromJSONString(jsonyaml);
    	} catch (Exception e) {
    	    Yaml yaml= new Yaml();
    	    Map<String,Object> map = (Map<String, Object>) yaml.load(jsonyaml);
    	    JSONObject jsonObj = JSONObject.fromObject(map);
    	    try {
    	    	resources = ModelNode.fromJSONString(jsonObj.toString());
    	    } catch (Throwable t) {
    	    	if (listener != null)
    	    		t.printStackTrace(listener.getLogger());
    	    }
    	}
    	return resources;
    }
    
    protected int[] deleteAPIObjs(IClient client, TaskListener listener, String namespace, String type, String key, Map<String, String> labels) {
    	int[] ret = new int[2];
    	int deleted = 0;
    	int failed = 0;

    	if (type != null && key != null) {
			IResource resource = client.get(type, key, namespace);
    		if (resource != null) {
    			client.delete(resource);
    			listener.getLogger().println(String.format(MessageConstants.DELETED_OBJ, type, key));
    			deleted++;
    		} else {
    			listener.getLogger().println(String.format(MessageConstants.FAILED_DELETE_OBJ, type, key));
    			failed++;
    		}
    	}
    	
    	if (labels != null && labels.size() > 0) {
    		List<IResource> resources = client.list(type, namespace, labels);
    		for (IResource resource : resources) {
        		if (resource != null) {
        			client.delete(resource);
        			listener.getLogger().println(String.format(MessageConstants.DELETED_OBJ, type, resource.getName()));
        			deleted++;
        		}
    		}
    	}
    	
    	ret[0] = deleted;
    	ret[1] = failed;
    	return ret;
    }

}
