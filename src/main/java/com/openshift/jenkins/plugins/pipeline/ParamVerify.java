package com.openshift.jenkins.plugins.pipeline;

import hudson.util.FormValidation;

import java.io.IOException;

import javax.servlet.ServletException;

import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.QueryParameter;

public class ParamVerify {

    public static FormValidation doCheckApiURL(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.warning("Unless you specify a value here, one of the default API endpoints will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
        return FormValidation.ok();
    }

    public static FormValidation doCheckBldCfg(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("You must set a BuildConfig name");
        return FormValidation.ok();
    }
    
    public static FormValidation doCheckNamespace(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.warning("Unless you specify a value here, the default namespace will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
        return FormValidation.ok();
    }
    
    public static FormValidation doCheckCheckForTriggeredDeployments(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set trigger check");
        try {
        	Boolean.parseBoolean(value);
        } catch (Throwable t) {
        	return FormValidation.error(t.getMessage());
        }
        return FormValidation.ok();
    }

    public static FormValidation doCheckJsonyaml(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("You must set a block of JSON or YAML");
        try {
        	ModelNode.fromJSONString(value);
        } catch (Throwable t) {
        	return FormValidation.error("The input specified encountered the following parsing error:  " + t.getMessage());
        }
        return FormValidation.ok();
    }
    
    public static FormValidation doCheckDepCfg(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("You must set a DeploymentConfig name");
        return FormValidation.ok();
    }
    
    public static FormValidation doCheckReplicaCount(@QueryParameter String value)
            throws IOException, ServletException {
        try {
        	Integer.decode(value);
        } catch (NumberFormatException e) {
        	return FormValidation.warning("If you want to validate the number of replicas, please specify an integer for the replica count");
        }
        return FormValidation.ok();
    }

    public static FormValidation doCheckTestTag(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set the name of image stream tag that serves as the source of the operation");
        return FormValidation.ok();
    }

    public static FormValidation doCheckProdTag(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set the name of the image stream tag that serves as the destination or target of the operation");
        return FormValidation.ok();
    }


    public static FormValidation doCheckTestStream(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set the name of image stream that serves as the source of the operation");
        return FormValidation.ok();
    }

    public static FormValidation doCheckProdStream(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set the name of the image stream that serves as the destination or target of the operation");
        return FormValidation.ok();
    }

    public static FormValidation doCheckSvcName(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set the name of the Service to validate");
        return FormValidation.ok();
    }

    public static FormValidation doCheckTag(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set the name of the image stream tag you want to poll");
        return FormValidation.ok();
    }
    
    public static FormValidation doCheckImageStreamName(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("Please set the name of the image stream you want to poll");
        return FormValidation.ok();
    }
    
}
