package net.vectormc.conductor.downloaders;

import lombok.Getter;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;
import net.vectormc.conductor.log.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class URLDownloader extends Downloader {
    @Getter
    private String url;

    public URLDownloader(String url, String fileName, boolean replaceIfExists, Credentials credentials) {
        this.url = url;
        this.fileName = fileName;
        this.replaceIfExists = replaceIfExists;
        this.downloaded = false;
        this.credentials = credentials;
    }

    @Override
    public void download() throws RetrievalException {
        try {
            URL u = new URL(this.url);
            HttpURLConnection huc = (HttpURLConnection) u.openConnection();
            this.credentials.credentials.forEach((credentialType, stringStringMap) -> stringStringMap.forEach(huc::setRequestProperty));

            ReadableByteChannel i = Channels.newChannel(huc.getInputStream());
            File out = new File(getTempFolder() + File.separator + this.fileName);
            if (out.exists()) {
                Logger.getLogger().info("[Download] Deleting from temporary folder " + out.getAbsolutePath() + " | Success:" + out.delete());
                out.delete();
            }
            FileOutputStream o = new FileOutputStream(out);
            o.getChannel().transferFrom(i, 0, Long.MAX_VALUE);

            this.downloadedFile = out;

            o.close();
            i.close();

            i = null; o = null;
            System.gc();
        } catch(Exception e) {
            throw new RetrievalException(e);
        }
    }
}
