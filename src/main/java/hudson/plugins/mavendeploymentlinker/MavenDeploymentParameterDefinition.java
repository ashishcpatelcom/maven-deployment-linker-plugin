package hudson.plugins.mavendeploymentlinker;

import hudson.Extension;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.SimpleParameterDefinition;
import hudson.model.Hudson;
import hudson.model.StringParameterValue;
import hudson.plugins.mavendeploymentlinker.MavenDeploymentLinkerAction.ArtifactVersion;
import hudson.util.RunList;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

public class MavenDeploymentParameterDefinition extends SimpleParameterDefinition {

	private static final long serialVersionUID = 7749382789673185691L;
	
	private final String projectNameRegex;
	private final Pattern projectNamePattern;

	public String getProjectNameRegex() {
		return projectNameRegex;
	}

	public Pattern getProjectNamePattern() {
		return projectNamePattern;
	}

	@DataBoundConstructor
	public MavenDeploymentParameterDefinition(String name, String projectNameRegex, String description) {
		super(name, description);
		this.projectNameRegex = projectNameRegex;
		this.projectNamePattern = Pattern.compile(projectNameRegex);
	}

	protected ParameterValue createValueCommon(StringParameterValue value) {
		return value;
	}

	@Override
	public ParameterValue createValue(StaplerRequest request, JSONObject jo) {
		StringParameterValue value = request.bindJSON(StringParameterValue.class, jo);
		value.setDescription(getDescription());

		return createValueCommon(value);
	}

	@Override
	public ParameterValue createValue(String value) throws IllegalArgumentException {
		return createValueCommon(new StringParameterValue(getName(), value, getDescription()));
	}

	private List<String> getMavenJobNames() {
		List<String> mavenJobNames = new ArrayList<String>();

		List<MavenModuleSet> items = Hudson.getInstance().getItems(MavenModuleSet.class);
		for (MavenModuleSet mavenModuleSet : items) {
			if (getProjectNamePattern().matcher(mavenModuleSet.getName()).matches()) {
				mavenJobNames.add(mavenModuleSet.getName());
			}
		}
		return mavenJobNames;
	}

	public ListBoxModel getProjectNameItems() {
		ListBoxModel listBoxModel = new ListBoxModel();

		for (String mavenJobName : getMavenJobNames()) {
			listBoxModel.add(mavenJobName, mavenJobName);
		}
		return listBoxModel;
	}

	@JavaScriptMethod
	public ListBoxModel getVersionItems(String projectName, String artifactType) {
		ListBoxModel listBoxModel = new ListBoxModel();

		MavenModuleSet mavenModuleSet = getMavenModuleSet(projectName);
		if (mavenModuleSet == null) return listBoxModel;

		List<MavenModuleSetBuild> successfullBuilds = getSuccessfullBuilds(mavenModuleSet);
		List<MavenDeploymentLinkerAction> actions = getActions(successfullBuilds);

		boolean isSnapshot = "snapshot".equalsIgnoreCase(artifactType);
		for (MavenDeploymentLinkerAction action : actions) {
			List<ArtifactVersion> deployments = action.getDeployments();
			for (ArtifactVersion deployment : deployments) {
				if (deployment.isSnapshot() != isSnapshot) continue;
				listBoxModel.add(createOption(deployment));
			}
		}

		return listBoxModel;
	}
	
	private Option createOption(ArtifactVersion deployment) {
		String url = deployment.getUrl();
		String name = url.substring(url.lastIndexOf('/') + 1, url.length());
		return new Option(name, url);
	}

	private MavenModuleSet getMavenModuleSet(String jobName) {
		List<MavenModuleSet> items = Hudson.getInstance().getItems(MavenModuleSet.class);
		for (MavenModuleSet mavenModuleSet : items) {
			if (mavenModuleSet.getName().equalsIgnoreCase(jobName)) {
				return mavenModuleSet;
			}
		}
		return null;
	}

	private List<MavenModuleSetBuild> getSuccessfullBuilds(MavenModuleSet mavenModuleSet) {
		List<MavenModuleSetBuild> successfullBuilds = new ArrayList<MavenModuleSetBuild>();
		RunList<MavenModuleSetBuild> builds = mavenModuleSet.getBuilds();
		for (MavenModuleSetBuild build : builds) {
			if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
				successfullBuilds.add(build);
			}
		}
		return successfullBuilds;
	}

	private List<MavenDeploymentLinkerAction> getActions(List<MavenModuleSetBuild> builds) {
		List<MavenDeploymentLinkerAction> actions = new ArrayList<MavenDeploymentLinkerAction>();
		for (MavenModuleSetBuild build : builds) {
			actions.addAll(build.getActions(MavenDeploymentLinkerAction.class));
		}
		return actions;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static final class DescriptorImpl extends ParameterDescriptor {

		@Override
		public String getDisplayName() {
			return "Maven Deployment Linked Arfitact";
		}

	}

}
