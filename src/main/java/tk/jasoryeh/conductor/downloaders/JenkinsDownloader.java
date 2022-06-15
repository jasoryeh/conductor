package tk.jasoryeh.conductor.downloaders;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import lombok.Getter;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.secrets.JenkinsPluginSecret;
import tk.jasoryeh.conductor.util.Assert;
import tk.jasoryeh.conductor.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

/**
 * Retrieve files from the Jenkins server specified
 */
public class JenkinsDownloader extends Downloader {
    @Getter
    private String job;
    @Getter
    private String artifactName;
    @Getter
    private int number;
    @Getter
    private JenkinsPluginSecret auth;

    public JenkinsDownloader(File destination, boolean replaceIfExists, JenkinsPluginSecret authentication, String job, int buildNum, String artifact) {
        super(destination, replaceIfExists);
        this.auth = authentication;
        this.job = job;
        this.artifactName = artifact;
        this.number = buildNum;
    }

    @Getter
    private JenkinsServer jenkins;

    private static final int LATEST_SUCCESSFUL_ARTIFACT = -1;

    /**
     * Retrieve the file into temporary storage
     */
    @SneakyThrows
    @Override
    public boolean download() {
        this.jenkins = this.auth.toJenkinsAPI();

        Job job = this.jenkins.getJobs().get(this.job);
        if(job == null) {
            throw new IllegalArgumentException(String.format("Unknown Jenkins job: %s", this.job));
        }

        JobWithDetails details = job.details();
        Build build = this.number == LATEST_SUCCESSFUL_ARTIFACT ?
                details.getLastSuccessfulBuild() :
                details.getBuildByNumber(this.number);
        List<Artifact> artifacts =  build.details().getArtifacts();
        this.log(String.format("Found %d artifacts for job %s", artifacts.size(), this.job));

        this.log("Looking for artifact: " + this.artifactName);
        for (Artifact artifact : artifacts) {
            this.log(String.format("Found artifact | %s | %s", artifact.getFileName(), artifact.getDisplayPath()));

            if (!artifact.getFileName().equalsIgnoreCase(this.artifactName)) {
                continue;
            }

            this.log(String.format("Artifact matched | %s | %s |  retrieving from jenkins as | %s",
                    artifact.getFileName(),
                    artifact.getDisplayPath(),
                    this.destination.getAbsolutePath()));

            InputStream inputStream = build.details().downloadArtifact(artifact);
            Assert.isTrue(FileUtils.delete(this.destination), "Preparing destination failed: " + this.destination.getAbsolutePath());

            ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
            FileOutputStream outputStream = new FileOutputStream(this.destination);
            outputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

            this.log("Successfully transferred " + this.artifactName);

            outputStream.close();
            readableByteChannel.close();
            inputStream.close();
            return true;
        }

        this.log("Unable to find the artifact/build | " + this.job + " | " + this.artifactName + " #" + this.number);
        return false;
    }
    
    private void log(String msg) {
        L.i("[Jenkins] " + msg);
    }
}
