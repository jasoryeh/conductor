package tk.jasoryeh.conductor.downloaders;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.JobWithDetails;
import lombok.Getter;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.config.JenkinsConfiguration;
import tk.jasoryeh.conductor.log.L;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
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
    private JenkinsConfiguration auth;

    public JenkinsDownloader(JenkinsConfiguration authentication, String job, String artifact, int num,
                             String fileName, boolean replaceIfExists) {
        super(fileName, replaceIfExists);
        this.auth = authentication;
        this.job = job;
        this.artifactName = artifact;
        this.number = num;
    }

    /**
     * Same as above but with username/pass authorization plus host
     * @param host jenkins host address
     * @param job job name
     * @param artifactName Archived artifact's name from the job ex. test-1.0.jar
     * @param number Build number (-1 for latest)
     * @param username Username
     * @param passwordOrToken Password
     * @param fileName Name to save the artifact as ex. test-1.0.jar to test.jar
     * @param replaceIfExists replace if exists in destination
     */
    public JenkinsDownloader(String host, String job, String artifactName, int number, String username,
                             String passwordOrToken, String fileName, boolean replaceIfExists) {
        super(fileName, replaceIfExists);
        this.auth = new JenkinsConfiguration(host, username, passwordOrToken);
        this.job = job;
        this.artifactName = artifactName;
        this.number = number;
    }

    @Getter
    private JenkinsServer jenkins;

    /**
     * Retrieve the file into temporary storage
     */
    @SneakyThrows
    @Override
    public boolean download() {
        this.jenkins = this.auth.isAuthless() ? new JenkinsServer(new URI(this.auth.getHost()))
                : new JenkinsServer(new URI(this.auth.getHost()), this.auth.getUser(), this.auth.getAuth());

        if(this.jenkins.getJobs().get(this.job) == null) {
            throw new IllegalArgumentException("Unknown job " + this.job + ": Job not found.");
        }

        JobWithDetails details = this.jenkins.getJobs().get(this.job).details();

        List<Artifact> artifacts = number == -1 ? details.getLastSuccessfulBuild().details().getArtifacts()
                : details.getBuildByNumber(this.number).details().getArtifacts();

        for (Artifact artifact : artifacts) {
            this.log("Found artifact | " + artifact.getFileName() + " | " + artifact.getDisplayPath());
            if (artifact.getFileName().equalsIgnoreCase(this.artifactName)) {
                Artifact acceptedArtifact = artifact;
                Build acceptedBuild = number == -1 ? details.getLastSuccessfulBuild() : details.getBuildByNumber(number);

                this.log("Artifact matched | " + artifact.getFileName() + " | " + artifact.getDisplayPath()
                        + " |  retrieving from jenkins as | " + this.outputFileName);

                InputStream inputStream = acceptedBuild.details().downloadArtifact(acceptedArtifact);
                File out = new File(tmpFolderWSep() + this.outputFileName);
                out.delete(); // Delete from temp. folder.

                ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
                FileOutputStream outputStream = new FileOutputStream(out);
                outputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

                this.downloadedFile = out;
                this.log("Successfully transferred " + artifactName);

                outputStream.close();
                readableByteChannel.close();
                inputStream.close();
                return true;
            }
        }

        this.log("Unable to find the artifact/build | " + this.job + " | " + this.artifactName + " #" + this.number);
        return false;
    }
    
    private void log(String msg) {
        L.i("[Jenkins] " + msg);
    }
}
