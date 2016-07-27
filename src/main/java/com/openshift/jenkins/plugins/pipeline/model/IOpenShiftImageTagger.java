package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import com.openshift.internal.restclient.model.ImageStream;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftCreator;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.model.IImageStream;

public interface IOpenShiftImageTagger extends IOpenShiftPlugin {

	final static String DISPLAY_NAME = "Tag OpenShift Image";

	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	public String getAlias();
	
	public String getTestTag();
	
	public String getProdTag();
		
	public String getTestStream();
	
	public String getProdStream();
	
	public String getDestinationNamespace();
		
	public String getDestinationAuthToken();
		
	public TokenAuthorizationStrategy getDestinationToken();
	
	public void setDestinationToken(TokenAuthorizationStrategy token);
	
	default String getAlias(Map<String,String> overrides) {
		return getOverride(getAlias(), overrides);
	}
	
	default String getTestTag(Map<String,String> overrides) {
		return getOverride(getTestTag(), overrides);
	}
	
	default String getProdTag(Map<String,String> overrides) {
		return getOverride(getProdTag(), overrides);
	}
	
	default String getTestStream(Map<String,String> overrides) {
		return getOverride(getTestStream(), overrides);
	}
	
	default String getProdStream(Map<String,String> overrides) {
		return getOverride(getProdStream(), overrides);
	}
	
	default String getDestinationNamespace(Map<String,String> overrides) {
		String val = getOverride(getDestinationNamespace(), overrides);
		if (val.length() == 0 && overrides != null && overrides.containsKey(NAMESPACE_ENV_VAR)) {
			val = overrides.get(NAMESPACE_ENV_VAR);
		}
		return val;
	}
	
	default String getDestinationAuthToken(Map<String,String> overrides) {
		return getOverride(getDestinationAuthToken(), overrides);
	}
	
	default String deriveImageID(String testTag, IImageStream srcIS) {
    	String srcImageID = srcIS.getImageId(testTag);
    	if (srcImageID != null && srcImageID.length() > 0) {
    		// testTag an valid ImageStreamTag, so translating to an ImageStreamImage
        	srcImageID = srcIS.getName() + "@" + (srcImageID.startsWith("sha256:") ? srcImageID.substring(7) : srcImageID);
        	return srcImageID;
    	} else {
    		// not a valid ImageStreamTag, see if a valid ImageStreamImage
    		//TODO port to ImageStream.java in openshift-restclient-java
    		ModelNode imageStream = ((ImageStream)srcIS).getNode();
    		ModelNode status = imageStream.get("status");
    		ModelNode tags = status.get("tags");
    		if (tags.getType() != ModelType.LIST)
    			return null;
    		List<ModelNode> tagWrappers = tags.asList();
    		for (ModelNode tagWrapper : tagWrappers) {
    			ModelNode tag = tagWrapper.get("tag");
    			ModelNode items = tagWrapper.get("items");
    			for (ModelNode itemWrapper : items.asList()) {
    				ModelNode image = itemWrapper.get("image");
    				if (image != null && (image.asString().equals(testTag) ||
    									  image.asString().substring(7).equals(testTag))) {
    					return srcIS.getName() + "@" + (testTag.startsWith("sha256:") ? testTag.substring(7) : testTag);
    				}
    			}
    		}
    	}
    	return null;
	}
	
	default String deriveImageTag(String imageID, IImageStream srcIS) {
		//TODO port to ImageStream.java in openshift-restclient-java
		ModelNode imageStream = ((ImageStream)srcIS).getNode();
		ModelNode status = imageStream.get("status");
		ModelNode tags = status.get("tags");
		if (tags.getType() != ModelType.LIST)
			return null;
		List<ModelNode> tagWrappers = tags.asList();
		for (ModelNode tagWrapper : tagWrappers) {
			ModelNode tag = tagWrapper.get("tag");
			ModelNode items = tagWrapper.get("items");
			for (ModelNode itemWrapper : items.asList()) {
				ModelNode image = itemWrapper.get("image");
				if (image != null && (image.asString().equals(imageID) ||
									  image.asString().substring(7).equals(imageID))) {
					return tag.asString();
				}
			}
		}
		return null;
	}

	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {
    	listener.getLogger().println(String.format(MessageConstants.START_TAG, getTestStream(overrides), getTestTag(overrides), getNamespace(overrides), getProdStream(overrides), getProdTag(overrides), getDestinationNamespace(overrides)));
    	boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	boolean useTag = Boolean.parseBoolean(getAlias(overrides));
    	
    	// get oc clients 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
   		setDestinationToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(null, getDestinationAuthToken(overrides), listener, chatty)));
    	
    	if (client != null) {
    		// get src id
    		IImageStream srcIS = client.get(ResourceKind.IMAGE_STREAM, getTestStream(overrides), getNamespace(overrides));
    		if (srcIS == null) {
    			listener.getLogger().println(String.format(MessageConstants.EXIT_TAG_CANNOT_GET_IS, getTestStream(overrides), getNamespace(overrides)));
    			return false;
    		}
    		
        	if (chatty)
        		listener.getLogger().println("\n tag before verification: " + getTestTag(overrides) + " and alias " + useTag);
        	
        	String srcImageID = null;
        	String tagType = "ImageStreamTag";
        	if (!useTag) {
        		tagType = "ImageStreamImage";
        		srcImageID = deriveImageID(getTestTag(overrides), srcIS);
        	} else {
        		Collection<String> tags = srcIS.getTagNames();
        		Iterator<String> iter = tags.iterator();
        		while (iter.hasNext()) {
        			String tag = iter.next();
        			if (getTestTag(overrides).equals(tag)) {
        				srcImageID = getTestStream(overrides) + ":" + tag;
        				break;
        			}
        		}
        		if (srcImageID == null) {
        			// see if this is a valid image id for a known tag
        			String tag = deriveImageTag(getTestTag(overrides), srcIS);
        			if (tag != null)
        				srcImageID = getTestStream(overrides) + ":" + tag;
        		}
        	}
        	
        	if (srcImageID == null) {
        		listener.getLogger().println(String.format(MessageConstants.EXIT_TAG_NOT_FOUND, getTestTag(overrides), getTestStream(overrides)));
        		return false;
        	}
        	
        	if (chatty)
        		listener.getLogger().println("\n srcImageID after translation " + srcImageID + " and tag type " + tagType);
        	
        	//get dest image stream
			IImageStream destIS = null;
			
			String destinationNS = null;
			if (getDestinationNamespace(overrides).length() == 0) {
				destinationNS = getNamespace(overrides);
			} else {
				destinationNS = getDestinationNamespace(overrides);
			}
			
        	if (getNamespace(overrides).equals(destinationNS) && getTestStream(overrides).equals(getProdStream(overrides))) {
        		destIS = srcIS;
        	} else {
    			try {
    				// if a dest auth token was set we need to get a base client that uses it, essentially like we just did above 
    				// for the OpenShiftCreator
    				if (this.getDestinationAuthToken(overrides) != null && this.getDestinationAuthToken(overrides).length() > 0) {
    					this.setAuth(Auth.createInstance(chatty ? listener : null, getApiURL(overrides), overrides));
    					this.setToken(getDestinationToken());
    					client = this.getClient(listener, DISPLAY_NAME, overrides);
    				}
    				destIS = client.get(ResourceKind.IMAGE_STREAM, getProdStream(overrides), destinationNS);
    			} catch (com.openshift.restclient.OpenShiftException e) {
    				String createJson = "{\"kind\": \"ImageStream\",\"apiVersion\": \"v1\",\"metadata\": {\"name\": \"" +
    				getProdStream(overrides) + "\",\"creationTimestamp\": null},\"spec\": {},\"status\": {\"dockerImageRepository\": \"\"}}";
    				
    				Map<String,String> newOverrides = new HashMap<String,String>(overrides);
    				// we don't want this step's source namespace to be the creator's namespace, but rather this step's destination namespace, so clear out the override and then reuse all other overrides
    				newOverrides.remove("namespace");
    				OpenShiftCreator isCreator = new OpenShiftCreator(getApiURL(newOverrides), destinationNS, getDestinationToken().getToken(), getVerbose(newOverrides), createJson);
    				isCreator.setAuth(Auth.createInstance(chatty ? listener : null, getApiURL(newOverrides), overrides));
    		    	isCreator.setToken(getDestinationToken());
    				
    				
    				boolean newISCreated = isCreator.coreLogic(launcher, listener, newOverrides);

    				if (!newISCreated) {
    					listener.getLogger().println(String.format(MessageConstants.EXIT_TAG_CANNOT_CREATE_DEST_IS, getProdStream(overrides), destinationNS));
    					return false;
    				}
    				
    				destIS = client.get(ResourceKind.IMAGE_STREAM, getProdStream(overrides), destinationNS);
    			}
        	}
        	
        	if (destIS == null) {
    			listener.getLogger().println(String.format(MessageConstants.EXIT_TAG_CANNOT_GET_IS, getProdStream(overrides), destinationNS));
    			return false;
        	}
        	
        	// tag image
        	destIS.addTag(getProdTag(overrides), tagType, srcImageID, getNamespace(overrides));
			if (chatty)
				listener.getLogger().println("\n updated image stream json " + ((ImageStream)destIS).getNode().toJSONString(false));
			client.update(destIS);
			
			
	    	listener.getLogger().println(String.format(MessageConstants.EXIT_OK, DISPLAY_NAME));
			return true;
    	} else {
    		return false;
    	}

	}

}
