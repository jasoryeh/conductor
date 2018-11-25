package net.vectormc.conductor.downloaders;

import lombok.Getter;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;
import net.vectormc.conductor.log.Logger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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

            File out = new File(getTempFolder() + File.separator + this.fileName);
            if (out.exists()) {
                Logger.getLogger().info("[Download] Deleting from temporary folder " + out.getAbsolutePath() + " | Success:" + out.delete());
                out.delete();
            }
            Logger.getLogger().info("[Download] Downloading file... " + out.getAbsolutePath() + " from " + url);

            //FileUtils.copyURLToFile(u, out);

            InputStream inputStream = huc.getInputStream();
            FileUtils.copyInputStreamToFile(inputStream, out);
            inputStream.close();

            inputStream = null;
            System.gc();

            this.downloadedFile = out;

        } catch(Exception e) {
            throw new RetrievalException(e);
        }
    }
}
