package com.openshift.openshiftjenkinsbuildutils;

import java.io.InputStream;
import java.io.SequenceInputStream;

import org.apache.commons.lang.StringUtils;

import com.openshift.internal.restclient.capability.resources.AbstractOpenShiftBinaryCapability;
import com.openshift.restclient.IClient;
import com.openshift.restclient.capability.resources.IPodLogRetrieval;
import com.trilead.ssh2.util.IOUtils;

public class BinaryScaleInvocation extends AbstractOpenShiftBinaryCapability implements IPodLogRetrieval {
	
	// we are replacing this use of this class with REST flow in OpenShiftScaler, but leave this class present
	// in the interim.  Just in case, here is an example of how this class would be used:
//	BinaryScaleInvocation runner = new BinaryScaleInvocation(replicaCount, depId, nameSpace, client);
//	InputStream logs = null;
//	// create stream and copy bytes
//	try {
//		logs = new BufferedInputStream(runner.getLogs(true));
//		int b;
//		while ((b = logs.read()) != -1) {
//			listener.getLogger().write(b);
//		}
//		scaleDone = true;
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
//		listener.getLogger().println("OpenShiftScaler wait 10 seconds, then try oc scale again");
//		try {
//			Thread.sleep(10000);
//		} catch (InterruptedException e) {
//		}
//	}
//}
	

	private final String replicaCount;
	private final String deployment;
	private final String nameSpace;
	private StringBuilder args;
	
	public BinaryScaleInvocation(String replicaCount, String deployment, String nameSpace, IClient client) {
		super(client);
		this.replicaCount = replicaCount;
		this.deployment = deployment;
		this.nameSpace = nameSpace;
	}
	
	@Override
	public boolean isSupported() {
		return true;
	}

	@Override
	public String getName() {
		return BinaryScaleInvocation.class.getSimpleName();
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
		args.append(" -n ").append(nameSpace).append(" scale ")
			.append("--replicas=").append(replicaCount).append(" ")
			.append(sec)
			.append(" --server=").append(getClient().getBaseURL()).append(" ")
			.append(" rc ")
			.append(deployment).append(" ");
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
