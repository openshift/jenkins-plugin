package com.openshift.openshiftjenkinsbuildutils;

import java.io.InputStream;
import java.io.SequenceInputStream;

import org.apache.commons.lang.StringUtils;

import com.openshift.internal.restclient.capability.resources.AbstractOpenShiftBinaryCapability;
import com.openshift.restclient.IClient;
import com.openshift.restclient.capability.resources.IPodLogRetrieval;
import com.trilead.ssh2.util.IOUtils;

public class BinaryDeployInvocation extends AbstractOpenShiftBinaryCapability implements IPodLogRetrieval {

	// while the class is still present, we've replaced it with a REST flow in OpenShiftDeployer. the
	// following is an example of how this class was leveraged in OpenShiftDeployer:
//	BinaryDeployInvocation runner = new BinaryDeployInvocation(depCfg, nameSpace, client);
//	InputStream logs = null;
//	// create stream and copy bytes
//	try {
//		logs = new BufferedInputStream(runner.getLogs(true));
//		int b;
//		while ((b = logs.read()) != -1) {
//			listener.getLogger().write(b);
//		}
//		deployDone = true;
//	} catch (Throwable e) {
//		e.printStackTrace(listener.getLogger());
//	} finally {
//		runner.stop();
//		try {
//			if (logs != null)
//				logs.close();
//		} catch (Throwable e) {
//			e.printStackTrace(listener.getLogger());
//		}
//	}
//	
//	if (logs != null) {
//		break;
//	} else {
//		listener.getLogger().println("OpenShiftDeployer wait 10 seconds, then try oc deploy again");
//		try {
//			Thread.sleep(10000);
//		} catch (InterruptedException e) {
//		}
//	}
	
	private final String deployment;
	private final String nameSpace;
	private StringBuilder args;
	
	public BinaryDeployInvocation(String deployment, String nameSpace, IClient client) {
		super(client);
		this.deployment = deployment;
		this.nameSpace = nameSpace;
	}
	
	@Override
	public boolean isSupported() {
		return true;
	}

	@Override
	public String getName() {
		return BinaryDeployInvocation.class.getSimpleName();
	}

	@Override
	protected void cleanup() {
		if (getProcess() != null) {
			IOUtils.closeQuietly(getProcess().getInputStream());
			IOUtils.closeQuietly(getProcess().getErrorStream());
		}
	}

	@Override
	protected boolean validate() {
		return true;
	}

	@Override
	protected String[] buildArgs(String location) {
		args = new StringBuilder(location);
		String sec = " --insecure-skip-tls-verify=true ";
		if (Auth.useCert())
			sec = Auth.CERT_ARG;
		args.append(" -n ").append(nameSpace).append(" deploy ")
			.append(deployment).append(" ")
			.append(sec)
			.append(" --server=").append(getClient().getBaseURL()).append(" ")
			.append(" --latest ");
		addToken(args);
		return StringUtils.split(args.toString(), " ");
	}
	
	public String getArgs() {
		return args.toString();
	}

	@Override
	public InputStream getLogs(boolean follow) {
		start();
		SequenceInputStream is = new SequenceInputStream(getProcess().getInputStream(), getProcess().getErrorStream());
		return is;
	}

}
