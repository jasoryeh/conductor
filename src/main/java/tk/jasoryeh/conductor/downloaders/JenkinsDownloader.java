package tk.jasoryeh.conductor.downloaders;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.Job;
import lombok.Getter;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.downloaders.exceptions.RetrievalException;
import tk.jasoryeh.conductor.log.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Retrieve files from the Jenkins server specified
 */
public class JenkinsDownloader extends Downloader {
    @Getter
    private String host;
    @Getter
    private String job;
    @Getter
    private String artifactName;
    @Getter
    private int number;
    @Getter
    private String username;
    @Getter
    private String passwordOrToken;

    /**
     * Downloader for artifacts from your jenkins server
     * @param job Job name
     * @param artifactName Archived artifact's name from the job example test-1.0.jar
     * @param number Build number (-1) for latest
     * @param fileName Name you want it saved as ex. test-1.0.jar to test.jar
     * @param replaceIfExists replace if it already exists in destination
     * @param credentials jenkins credentials
     */
    public JenkinsDownloader(String job, String artifactName, int number, String fileName, boolean replaceIfExists, Credentials credentials) {
        this(Conductor.getInstance().getConfig().getString("jenkinsHost"), job, artifactName, number, "", "", fileName, replaceIfExists, credentials);
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
     * @param credentials additional credentials
     */
    public JenkinsDownloader(String host, String job, String artifactName, int number, String username, String passwordOrToken, String fileName, boolean replaceIfExists, Credentials credentials) {
        this.host = host;
        this.job = job;
        this.artifactName = artifactName;
        this.number = number;
        this.username = username;
        this.passwordOrToken = passwordOrToken;
        this.fileName = fileName;
        this.replaceIfExists = replaceIfExists;
        this.downloaded = false;
        this.credentials = credentials;
    }

    @Getter
    private JenkinsServer jenkins;

    /**
     * Retrieve the file into temporary storage
     * @throws RetrievalException thrown if fail, stops server to help you fix the problem
     */
    @Override
    public void download() throws RetrievalException {
        try {
            this.jenkins = !username.equals("") ? new JenkinsServer(new URI(this.host), this.username, this.passwordOrToken) : new JenkinsServer(new URI(this.host));

            Job job = this.jenkins.getJobs().get(this.job);

            Artifact acceptedArtifact = null;
            Build acceptedBuild = null;
            if (number == -1) {
                // Latest
                for (Artifact artifact : job.details().getLastSuccessfulBuild().details().getArtifacts()) {
                    Logger.getLogger().debug("[Jenkins] Artifact | " + artifact.getFileName() + " | " + artifact.getDisplayPath());
                    if (artifact.getFileName().equalsIgnoreCase(this.artifactName)) {
                        acceptedArtifact = artifact;
                        acceptedBuild = job.details().getLastSuccessfulBuild();
                        break;
                    }
                }
            } else {
                // Specified job run number
                for (Artifact artifact : job.details().getBuildByNumber(this.number).details().getArtifacts()) {
                    Logger.getLogger().debug("[Jenkins] Artifact | " + artifact.getFileName() + " | " + artifact.getDisplayPath());
                    if (artifact.getFileName().equalsIgnoreCase(this.artifactName)) {
                        acceptedArtifact = artifact;
                        acceptedBuild = job.details().getBuildByNumber(this.number);
                        break;
                    }
                }
            }
            if (acceptedArtifact == null | acceptedBuild == null) {
                Logger.getLogger().info("[Jenkins] Unable to retrieve artifact(s)/build(s) | " + this.job + " | " + this.artifactName + " #" + this.number);
                Conductor.shutdown(true);
            }

            Logger.getLogger().info("[Jenkins] Retrieving " + acceptedArtifact.getFileName() + " as " + this.fileName);

            InputStream inputStream = acceptedBuild.details().downloadArtifact(acceptedArtifact);

            File out = new File(getTempFolder() + File.separator + this.fileName);
            if (out.exists()) {
                Logger.getLogger().info("[Jenkins] Deleting from temporary folder " + out.getAbsolutePath() + " | Success:"
                        + out.delete());
            }

            ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
            FileOutputStream outputStream = new FileOutputStream(out);
            outputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

            Logger.getLogger().info("[Jenkins] Successful transfer.");

            this.downloadedFile = out;

            outputStream.close();
            readableByteChannel.close();
            inputStream.close();

        } catch (URISyntaxException | IOException | NullPointerException e) {
            e.printStackTrace();
            Conductor.shutdown(true);
        }
    }
}
