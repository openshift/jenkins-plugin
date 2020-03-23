package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.dsl.OpenShiftBaseStep;
import com.openshift.jenkins.plugins.pipeline.dsl.TimedOpenShiftBaseStep;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.QueryParameter;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;

public class ParamVerify {

    public static void updateDSLBaseStep(Map<String, Object> arguments,
            OpenShiftBaseStep step) {
        if (arguments.containsKey("namespace")) {
            Object namespace = arguments.get("namespace");
            if (namespace != null) {
                step.setNamespace(namespace.toString());
            }
        }
        if (arguments.containsKey("apiURL")) {
            Object apiURL = arguments.get("apiURL");
            if (apiURL != null)
                step.setApiURL(apiURL.toString());
        }
        if (arguments.containsKey("authToken")) {
            Object authToken = arguments.get("authToken");
            if (authToken != null)
                step.setAuthToken(authToken.toString());
        }
        if (arguments.containsKey("verbose")) {
            Object verbose = arguments.get("verbose");
            if (verbose != null)
                step.setVerbose(verbose.toString());
        }
    }

    public static void updateTimedDSLBaseStep(Map<String, Object> arguments,
            TimedOpenShiftBaseStep step) {
        updateDSLBaseStep(arguments, step);
        Object waitTime = arguments.get("waitTime");

        if (waitTime != null) {
            step.setWaitTime(waitTime.toString());
        }

        Object waitUnit = arguments.get("waitUnit");
        if (waitUnit != null) {
            step.setWaitUnit(waitUnit.toString());
        }
    }

    public static FormValidation doCheckApiURL(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .warning("Unless you specify a value here, one of the default API endpoints will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
        return FormValidation.ok();
    }

    public static FormValidation doCheckBldCfg(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation.error("You must set a BuildConfig name");
        return FormValidation.ok();
    }

    public static FormValidation doCheckNamespace(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .warning("Unless you specify a value here, the default namespace will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
        return FormValidation.ok();
    }

    public static FormValidation doCheckToken(String value) {
        if (value.length() == 0)
            return FormValidation
                    .warning("Unless you specify a value here, the default token will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
        return FormValidation.ok();
    }

    public static FormValidation doCheckDestTagToken(
            @QueryParameter String value) throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation
                    .warning("A token is only needed if the new tag is targeted for a different project than the project containing the current tag");
        return FormValidation.ok();
    }

    public static FormValidation doCheckForWaitTime(String value) {
        value = (value == null) ? "" : value.trim();
        if (value.isEmpty()) // timeout will fallthrough to default
            return FormValidation.ok();
        try {
            Long.parseLong(value);
        } catch (Throwable t) {
            return FormValidation
                    .ok("Non-numeric value specified. During execution, an attempt will be made to resolve [%s] as a build parameter or Jenkins global variable.",
                            value);
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
            try {
                Yaml yaml = new Yaml(new SafeConstructor());
                Map<String, Object> map = (Map<String, Object>) yaml
                        .load(value);
                JSONObject jsonObj = JSONObject.fromObject(map);
                ModelNode.fromJSONString(jsonObj.toString());
            } catch (Throwable t2) {
                return FormValidation
                        .error("Valid JSON or YAML must be specified");
            }
        }
        return FormValidation.ok();
    }

    public static FormValidation doCheckDepCfg(@QueryParameter String value)
            throws IOException, ServletException {
        if (value.length() == 0)
            return FormValidation.error("You must set a DeploymentConfig name");
        return FormValidation.ok();
    }

    public static FormValidation doCheckReplicaCount(
            @QueryParameter String value) {
        try {
            Integer.decode(value);
        } catch (NumberFormatException e) {
            return FormValidation
                    .ok("Non-numeric value specified. During execution, an attempt will be made to resolve [%s] as a build parameter or Jenkins global variable.",
                            value);
        }
        return FormValidation.ok();
    }

    public static FormValidation doCheckReplicaCountRequired(
            @QueryParameter String value) {
        try {
            Integer.decode(value);
        } catch (NumberFormatException e) {
            return FormValidation
                    .ok("Non-numeric value specified. During execution, an attempt will be made to resolve [%s] as a build parameter or Jenkins global variable.",
                            value);
        }
        return FormValidation.ok();
    }

    public static FormValidation doCheckTestTag(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of image stream tag that serves as the source of the operation");
        return FormValidation.ok();
    }

    public static FormValidation doCheckProdTag(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of the image stream tag that serves as the destination or target of the operation");
        return FormValidation.ok();
    }

    public static FormValidation doCheckTestStream(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of image stream that serves as the source of the operation");
        return FormValidation.ok();
    }

    public static FormValidation doCheckProdStream(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of the image stream that serves as the destination or target of the operation");
        return FormValidation.ok();
    }

    public static FormValidation doCheckSvcName(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of the Service to validate");
        return FormValidation.ok();
    }

    public static FormValidation doCheckTag(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of the image stream tag you want to poll");
        return FormValidation.ok();
    }

    public static FormValidation doCheckImageStreamName(
            @QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of the image stream you want to poll");
        return FormValidation.ok();
    }

    public static FormValidation doCheckType(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of the API object type you want to delete");
        return FormValidation.ok();
    }

    public static FormValidation doCheckKey(@QueryParameter String value) {
        if (value.length() == 0)
            return FormValidation
                    .error("Please set the name of the key of the API object you want to delete");
        return FormValidation.ok();
    }
}
