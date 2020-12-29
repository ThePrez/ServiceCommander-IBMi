package jesseg.ibmi.opensource;

import java.util.List;

import jesseg.ibmi.opensource.yaml.YamlServiceDef;

import java.util.LinkedList;

/**
 * Abstract class representing a definition of a service. Since currently only 
 * .yaml files are supported, the only implementing class is {@link YamlServiceDef}
 * 
 * @author Jesse Gorzinski
 */
public abstract class ServiceDefinition {

	public enum CheckAliveType {
		JOBNAME, PORT
	}

	public abstract String getCheckAliveCriteria();

	public abstract CheckAliveType getCheckAliveType();

	public List<String> getEnvironmentVars() {
		return new LinkedList<String>();
	}

	public String getFriendlyName() {
		return getName();
	}

	public abstract String getName();

	public abstract String getStartCommand();

	public String getStopCommand() {
		return null;
	}

	public String getWorkingDirectory() {
		return System.getProperty("user.dir");
	}

	public boolean isInheritingEnvironmentVars() {
		return true;
	}

	public List<String> getDependencies() {
		return new LinkedList<String>();
	}

	public int getStartupWaitTime() {
		return 60;
	}

	public int getShutdownWaitTime() {
		return 45;
	}

	public enum BatchMode {
		BATCH_QP2SHELL2, NO_BATCH;
		public static BatchMode guessFromConfigString(final String _configString) {
			String lc = _configString.toLowerCase().trim();
			switch (lc) {
			case "yes":
			case "1":
			case "qp2shell2":
			case "qp2":
			case "y":
			case "true":
			case "t":
				return BATCH_QP2SHELL2;
			case "no":
			case "0":
			case "n":
			case "false":
			case "f":
				return NO_BATCH;
			}
			return valueOf(_configString);
		}
	}

	public BatchMode getBatchMode() {
		return BatchMode.NO_BATCH;
	}

	public String getBatchJobName() {
		return null;
	}

	public String getSbmJobOpts() {
		return "";
	}

	public abstract String getSource();
}
