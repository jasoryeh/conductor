package net.vectormc.conductor.downloaders;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.Job;
import lombok.Getter;
import net.vectormc.conductor.Conductor;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;
import net.vectormc.conductor.log.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

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

    public JenkinsDownloader(String job, String artifactName, int number, String fileName, boolean replaceIfExists, Credentials credentials) {
        this(Conductor.getInstance().getConfig().getString("jenkinsHost"), job, artifactName, number, "", "", fileName, replaceIfExists, credentials);
    }

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
                    if (artifact.getFileName().equalsIgnoreCase(this.artifactName)) {
                        acceptedArtifact = artifact;
                        acceptedBuild = job.details().getLastSuccessfulBuild();
                        break;
                    }
                }
            } else {
                // Specified job run number
                for (Artifact artifact : job.details().getBuildByNumber(this.number).details().getArtifacts()) {
                    if (artifact.getFileName().equalsIgnoreCase(this.artifactName)) {
                        acceptedArtifact = artifact;
                        acceptedBuild = job.details().getBuildByNumber(this.number);
                        break;
                    }
                }
            }
            if (acceptedArtifact == null | acceptedBuild == null) {
                Logger.getLogger().info("Unable to retrieve artifact(s)/build(s) | " + this.job + " | " + this.artifactName + " #" + this.number);
                Conductor.getInstance().shutdown(true);
            }

            Logger.getLogger().info("[Jenkins] Retrieving " + acceptedArtifact.getFileName() + " as " + this.fileName);

            InputStream inputStream = acceptedBuild.details().downloadArtifact(acceptedArtifact);

            File out = new File(getTempFolder() + File.separator + this.fileName);
            if (out.exists()) {
                Logger.getLogger().info("[Jenkins] Deleting from temporary folder " + out.getAbsolutePath() + " | Success:" + out.delete());
            }

            ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
            FileOutputStream outputStream = new FileOutputStream(out);
            outputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

            Logger.getLogger().info("[Jenkins] Successful transfer.");

            this.downloadedFile = out;

            outputStream.close();
            readableByteChannel.close();
            inputStream.close();

        } catch (URISyntaxException use) {
            use.printStackTrace();
            Conductor.getInstance().shutdown(true);
        } catch (IOException io) {
            io.printStackTrace();
            Conductor.getInstance().shutdown(true);
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            Conductor.getInstance().shutdown(true);
        }
    }
}