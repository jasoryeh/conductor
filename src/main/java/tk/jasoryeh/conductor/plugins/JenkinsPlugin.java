package tk.jasoryeh.conductor.plugins;

import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.secrets.JenkinsPluginSecret;

public class JenkinsPlugin extends Plugin {
    private final JenkinsPluginSecret secret;
    private final String artifact;
    private final String job;
    private final int build;

    public JenkinsPlugin(V2FileSystemObject fsObject, JenkinsPluginSecret secret, String job, int build, String artifact) {
        super(fsObject);
        this.secret = secret;
        this.artifact = artifact;
        this.job = job;
        this.build = build;
    }

    @Override
    public void prepare() {
        this.logger.info(String.format("Jenkins is attempting to prepare: %s on #%d on %s", this.artifact, this.build, this.job));
        JenkinsDownloader jenkinsDownloader = new JenkinsDownloader(
                this.getFsObject().getTemporary(),
                true,
                this.secret,
                this.job,
                this.build, this.artifact
        );
        jenkinsDownloader.download();
        this.logger.info("-");
    }

    @Override
    public void execute() {
        // nothing yet
    }
}
