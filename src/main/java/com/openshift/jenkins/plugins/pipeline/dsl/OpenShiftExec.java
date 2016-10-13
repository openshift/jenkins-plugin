package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.Argument;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftExec;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.*;

public class OpenShiftExec extends TimedOpenShiftBaseStep implements IOpenShiftExec {

    protected String pod;
    protected String container;
    protected String command;
    protected List<Argument> arguments = new ArrayList<>();

    @DataBoundConstructor
    public OpenShiftExec(String pod) {
        this.pod = pod != null ? pod.trim() : null;
    }

    @DataBoundSetter
    public void setContainer(String container) {
        this.container = container != null ? container.trim() : null;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command != null ? command.trim() : null;
    }

    @DataBoundSetter
    public void setArguments(List<Argument> arguments) {
        this.arguments = arguments;
    }

    public String getPod() {
        return pod;
    }

    public String getContainer() {
        return container;
    }

    public String getCommand() {
        return command;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftExecExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftExec";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        private Object requiredArgument(Map<String, Object> arguments, String arg) {
            Object o = arguments.get(arg);
            if (o == null) {
                throw new IllegalArgumentException("Missing required argument: " + arg);
            }
            return o;
        }

        private String argumentAsString(Map<String, Object> arguments, String arg, String def) {
            Object o = arguments.get(arg);
            if (o == null) {
                return def;
            }
            return o.toString();
        }

        private String argumentAsString(Map<String, Object> arguments, String arg) throws IllegalArgumentException {
            return requiredArgument(arguments, arg).toString();
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            OpenShiftExec step = new OpenShiftExec(argumentAsString(arguments, "pod"));
            step.setContainer(argumentAsString(arguments, "container", ""));

            Object commandObject = requiredArgument(arguments, "command");
            if (commandObject instanceof String) { // command: "date"
                step.setCommand(commandObject.toString());
            } else if (commandObject instanceof List) { // command : [ "echo", "hello", ... ]
                List commandList = (List) commandObject;
                Iterator i = commandList.iterator();
                if (!i.hasNext()) {
                    throw new IllegalArgumentException("Command list cannot be empty");
                }
                step.setCommand(i.next().toString());
                List<Argument> commandArgs = new ArrayList<>();
                while (i.hasNext()) {
                    commandArgs.add(new Argument(i.next().toString()));
                }
                step.setArguments(commandArgs);
            } else {
                throw new IllegalArgumentException("Unrecognized command syntax. It created type: " + commandObject.getClass().getName());
            }

            Object argumentsObject = arguments.get("arguments");
            if (argumentsObject != null) {
                if (argumentsObject instanceof List) {
                    List<Argument> commandArgs = new ArrayList<>();
                    for (Object o : (List) argumentsObject) {
                        String arg;
                        if (o instanceof Map) {
                            Object aObject = ((Map) o).get("value");
                            if (aObject == null) {
                                throw new IllegalArgumentException("Expected value entry in arguments map: " + o.toString());
                            }
                            arg = aObject.toString().trim();
                        } else {
                            arg = o.toString().trim();
                        }
                        commandArgs.add(new Argument(arg));
                    }
                    step.setArguments(commandArgs);
                } else {
                    throw new IllegalArgumentException("Unrecognized arguments syntax. It created type: " + argumentsObject.getClass().getName());
                }
            }

            ParamVerify.updateTimedDSLBaseStep(arguments, step);
            return step;
        }

    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        return true;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(
            AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return null;
    }

}
