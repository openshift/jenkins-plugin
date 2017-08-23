package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.internal.restclient.model.ImageStream;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftCreator;
import com.openshift.restclient.IClient;
import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IImageStream;
import hudson.Launcher;
import hudson.model.TaskListener;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.*;

public interface IOpenShiftImageTagger extends IOpenShiftPlugin {

    final static String DISPLAY_NAME = "Tag OpenShift Image";

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    String getAlias();

    String getSrcTag();

    String getDestTag();

    String getSrcStream();

    String getDestStream();

    String getDestinationNamespace();

    String getDestinationAuthToken();

    default String getAlias(Map<String, String> overrides) {
        return getOverride(getAlias(), overrides);
    }

    default String getSrcTag(Map<String, String> overrides) {
        return getOverride(getSrcTag(), overrides);
    }

    default String getDestTag(Map<String, String> overrides) {
        return getOverride(getDestTag(), overrides);
    }

    default String getSrcStream(Map<String, String> overrides) {
        return getOverride(getSrcStream(), overrides);
    }

    default String getDestStream(Map<String, String> overrides) {
        return getOverride(getDestStream(), overrides);
    }

    default String getDestinationNamespace(Map<String, String> overrides) {
        String destNS = getOverride(getDestinationNamespace(), overrides);
        return destNS.isEmpty() ? getNamespace(overrides) : destNS;
    }

    default String getDestinationAuthToken(Map<String, String> overrides) {
        return getOverride(getDestinationAuthToken(), overrides);
    }

    default String deriveImageID(String srcTag, IImageStream srcIS) {
        String srcImageID = srcIS.getImageId(srcTag);
        if (srcImageID != null && srcImageID.length() > 0) {
            // srcTag an valid ImageStreamTag, so translating to an
            // ImageStreamImage
            srcImageID = srcIS.getName()
                    + "@"
                    + (srcImageID.startsWith("sha256:") ? srcImageID
                            .substring(7) : srcImageID);
            return srcImageID;
        } else {
            // not a valid ImageStreamTag, see if a valid ImageStreamImage
            // TODO port to ImageStream.java in openshift-restclient-java
            ModelNode imageStream = ((ImageStream) srcIS).getNode();
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
                    if (image != null
                            && (image.asString().equals(srcTag) || image
                                    .asString().substring(7).equals(srcTag))) {
                        return srcIS.getName()
                                + "@"
                                + (srcTag.startsWith("sha256:") ? srcTag
                                        .substring(7) : srcTag);
                    }
                }
            }
        }
        return null;
    }

    default String deriveImageTag(String imageID, IImageStream srcIS) {
        // TODO port to ImageStream.java in openshift-restclient-java
        ModelNode imageStream = ((ImageStream) srcIS).getNode();
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
                if (image != null
                        && (image.asString().equals(imageID) || image
                                .asString().substring(7).equals(imageID))) {
                    return tag.asString();
                }
            }
        }
        return null;
    }

    default boolean coreLogic(Launcher launcher, TaskListener listener,
            Map<String, String> overrides) {
        final String srcStream = getSrcStream(overrides);
        final String srcTag = getSrcTag(overrides);
        final String srcNS = getNamespace(overrides);

        final String destStreams = getDestStream(overrides);
        final String destTags = getDestTag(overrides);
        final String destNS = getDestinationNamespace(overrides);

        listener.getLogger().println(
                String.format(MessageConstants.START_TAG, srcStream, srcTag,
                        srcNS, destStreams, destTags, destNS));
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        boolean useTag = Boolean.parseBoolean(getAlias(overrides));

        IClient srcClient = this.getClient(listener, DISPLAY_NAME, overrides);

        if (srcClient != null) {
            // get src id
            IImageStream srcIS = srcClient.get(ResourceKind.IMAGE_STREAM,
                    srcStream, srcNS);
            if (srcIS == null) {
                listener.getLogger().println(
                        String.format(MessageConstants.EXIT_TAG_CANNOT_GET_IS,
                                srcStream, srcNS));
                return false;
            }

            if (chatty) {
                listener.getLogger().println(
                        "\n tag before verification: " + srcTag + " and alias "
                                + useTag);
            }

            String srcImageID = null;
            String tagType = "ImageStreamTag";
            if (!useTag) {
                tagType = "ImageStreamImage";
                srcImageID = deriveImageID(srcTag, srcIS);
            } else {
                Collection<String> tags = srcIS.getTagNames();
                Iterator<String> iter = tags.iterator();
                while (iter.hasNext()) {
                    String tag = iter.next();
                    if (srcTag.equals(tag)) {
                        srcImageID = srcStream + ":" + tag;
                        break;
                    }
                }
                if (srcImageID == null) {
                    // see if this is a valid image id for a known tag
                    String tag = deriveImageTag(srcTag, srcIS);
                    if (tag != null)
                        srcImageID = srcStream + ":" + tag;
                }
            }

            if (srcImageID == null) {
                listener.getLogger().println(
                        String.format(MessageConstants.EXIT_TAG_NOT_FOUND,
                                srcTag, srcStream));
                return false;
            }

            if (chatty) {
                listener.getLogger().println(
                        "\n srcImageID after translation " + srcImageID
                                + " and tag type " + tagType);
            }

            final IClient destClient;

            // If destination token is set, create a new destination client
            final String destToken = getDestinationAuthToken(overrides);
            if (destToken != null && (!destToken.isEmpty())) {
                this.setAuth(Auth.createInstance(chatty ? listener : null,
                        getApiURL(overrides), overrides));
                destClient = this.getClient(listener, DISPLAY_NAME, overrides,
                        destToken);
            } else {
                destClient = srcClient;
            }

            List<String> newDestinationStreams = Arrays.asList(destStreams
                    .split(","));
            List<String> newDestTags = Arrays.asList(destTags.split(","));

            /**
             * The following logic is permissive of the following combinations
             * (1) 1 dest stream and 1 dest tag (i.e. a single new stream:tag
             * combination) (2) 1 dest stream and N dest tags (i.e. all tags
             * apply to the same stream) (3) 1 dest tag and N dest streams (i.e.
             * all streams will get the same tag) (4) N dest streams and N dest
             * tags (i.e. each index will be a unique stream:tag)
             */

            if (newDestinationStreams.size() != 1 && newDestTags.size() != 1
                    && newDestinationStreams.size() != newDestTags.size()) {
                String error = String
                        .format("Destination streams (%s) cardinality [%d] is incompatible with destination tag (%s) cardinality [%d]",
                                newDestinationStreams,
                                newDestinationStreams.size(), newDestTags,
                                newDestTags.size());
                throw new IllegalArgumentException(error);
            }

            Iterator<String> iDestStreams = newDestinationStreams.iterator();
            Iterator<String> iDestTags = newDestTags.iterator();
            String destStream = null;
            String destTag = null;
            while (iDestStreams.hasNext() || iDestTags.hasNext()) {

                if (iDestStreams.hasNext()) {
                    destStream = iDestStreams.next().trim();
                }

                if (iDestTags.hasNext()) {
                    destTag = iDestTags.next().trim();
                }

                IImageStream destIS = null;
                for (int retries = 10; retries > 0; retries--) {
                    try {
                        destIS = destClient.get(ResourceKind.IMAGE_STREAM,
                                destStream, destNS);
                    } catch (com.openshift.restclient.OpenShiftException e) {
                        String createJson = "{\"kind\": \"ImageStream\",\"apiVersion\": \"v1\",\"metadata\": {\"name\": \""
                                + destStream
                                + "\",\"creationTimestamp\": null},\"spec\": {},\"status\": {\"dockerImageRepository\": \"\"}}";

                        Map<String, String> newOverrides = new HashMap<String, String>(
                                overrides);
                        // we don't want this step's source namespace to be the
                        // creator's namespace,
                        // but rather this step's destination namespace, so
                        // clear out the override
                        // and then reuse all other overrides.
                        newOverrides.remove("namespace");

                        String token = destToken;
                        if (token == null || token.isEmpty()) {
                            token = getAuthToken();
                        }

                        OpenShiftCreator isCreator = new OpenShiftCreator(
                                getApiURL(newOverrides), destNS, token, ""
                                        + chatty, createJson);
                        isCreator.setAuth(Auth.createInstance(chatty ? listener
                                : null, getApiURL(newOverrides), overrides));

                        boolean newISCreated = isCreator.coreLogic(launcher,
                                listener, newOverrides);

                        if (!newISCreated) {
                            listener.getLogger()
                                    .println(
                                            String.format(
                                                    MessageConstants.EXIT_TAG_CANNOT_CREATE_DEST_IS,
                                                    destStream, destNS));
                            return false;
                        }

                        destIS = srcClient.get(ResourceKind.IMAGE_STREAM,
                                destStream, destNS);
                    }

                    if (destIS == null) {
                        listener.getLogger()
                                .println(
                                        String.format(
                                                MessageConstants.EXIT_TAG_CANNOT_GET_IS,
                                                destStream, destNS));
                        return false;
                    }

                    destIS.addTag(destTag, tagType, srcImageID, srcNS);
                    if (chatty) {
                        listener.getLogger().println(
                                "\n updated image stream json "
                                        + ((ImageStream) destIS).getNode()
                                                .toJSONString(false)
                                        + " with tag: " + destTag);
                    }

                    try {
                        destClient.update(destIS);
                        break;
                    } catch (OpenShiftException ose) {
                        if (ose.getStatus().getCode() == 403 && retries > 1) { // If
                                                                               // resource
                                                                               // was
                                                                               // updating,
                                                                               // retry
                            continue;
                        }
                        throw ose;
                    }
                }
            }

            listener.getLogger().println(
                    String.format(MessageConstants.EXIT_OK, DISPLAY_NAME));
            return true;
        } else {
            return false;
        }

    }

}
