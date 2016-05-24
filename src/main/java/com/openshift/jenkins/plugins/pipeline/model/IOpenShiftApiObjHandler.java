package com.openshift.jenkins.plugins.pipeline.model;

import hudson.model.TaskListener;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.jboss.dmr.ModelNode;
import org.yaml.snakeyaml.Yaml;

import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftApiObjHandler;
import com.openshift.restclient.IClient;
import com.openshift.restclient.model.IResource;


public interface IOpenShiftApiObjHandler extends IOpenShiftPlugin {

    default UrlConnectionHttpClient createHttpClient() {
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, getAuth(), null, null);
		urlClient.setAuthorizationStrategy(getToken());
		return urlClient;
    }
    
	default String fetchApiJsonFromApiServer(boolean chatty, TaskListener listener, 
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
    
    default void importJsonOfApiTypes(boolean chatty, TaskListener listener, 
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
    				if (!OpenShiftApiObjHandler.apiMap.containsKey(coreType)) {
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
        					OpenShiftApiObjHandler.apiMap.put(coreType, new String[]{apiDomain, typeStrForURL});
        				}
    				}
    			}
    		}
    	}
    	
    }
    
    default void updateApiTypes(boolean chatty, TaskListener listener, Map<String,String>overrides) {
    	// our lower level openshift-restclient-java usage here is not agreeable with the TrustManager maintained there,
    	// so we set up our own trust manager like we used to do in order to verify the server cert
    	Auth.createLocalTrustStore(getAuth(), getApiURL(overrides));
		importJsonOfApiTypes(chatty, listener, overrides, OpenShiftApiObjHandler.oapi, fetchApiJsonFromApiServer(chatty, listener, overrides, OpenShiftApiObjHandler.oapi));
		importJsonOfApiTypes(chatty, listener, overrides, OpenShiftApiObjHandler.api, fetchApiJsonFromApiServer(chatty, listener, overrides, OpenShiftApiObjHandler.api));
    	
    }
    
    default ModelNode hydrateJsonYaml(String jsonyaml, TaskListener listener) {
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
    
    default int[] deleteAPIObjs(IClient client, TaskListener listener, String namespace, String type, String key, Map<String, String> labels) {
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
