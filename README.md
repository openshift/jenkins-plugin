# openshift-jenkins-buildutils
As set of Jenkins plugins that operate on OpenShift.

The set includes these Jenkins "build steps", selectable from the `Add build step` pull down available on any project's configure page:

1) "Perform builds in OpenShift": performs the equivalent of an `oc start-build` command invocation, where the build logs are echoed to the Jenkins plugin in real time; in addition to confirming whether the build succeeded or not, the plugin will look to see if a deployment config specifying a non-zero replica count existed, and if so, confirm whether the deployment occurred

2) "Scale deployments in OpenShift":  performs the equivalent of an `oc scale` command invocation; the number of replicas is specified as a parameter to this build step, and the plugin will confirm whether the desired number of replicas was launched in a timely manner

3) "Start a deployment in OpenShift":  performs the equivalent of an `oc deploy` command invocation; the plugin will confirm whether the desired number of replicas was launched in a timely manner

4) "Verify a service is up in OpenShift": find the ip and port for the specified OpenShift service, and attempts to make a connection to that ip/port combination to confirm the service is up

5) "Tag an image in OpenShift": performs the equivalent of an `oc tag` command invocation in order to manipulate tags for images in OpenShift ImageStream's

6) "Verify deployments in OpenShift":  determines whether the expected set of DeploymentConfig's, ReplicationController's, and active replicas are present based on prior use of the scaler (2) and deployer (3) steps

For each required parameter, a default value is provided.  And each parameter field has help text available via clicking the help icon located just right of the parameter input field.
