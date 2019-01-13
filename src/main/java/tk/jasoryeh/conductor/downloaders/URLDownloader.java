package tk.jasoryeh.conductor.downloaders;

import lombok.Getter;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.downloaders.exceptions.RetrievalException;
import tk.jasoryeh.conductor.log.Logger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Retrieve files from a link
 */
public class URLDownloader extends Downloader {
    @Getter
    private String url;

    /**
     * Downloader for file links
     * @param url https://example.com/files/example.file.ext
     * @param fileName example.file.ext or some other file name with extension
     * @param replaceIfExists if the file is found in temporary storage or already there, do you want to delete it and replace it?
     * @param credentials HTTP headers for authorization
     */
    public URLDownloader(String url, String fileName, boolean replaceIfExists, Credentials credentials) {
        this.url = url;
        this.fileName = fileName;
        this.replaceIfExists = replaceIfExists;
        this.downloaded = false;
        this.credentials = credentials;
    }

    /**
     * Retrieve the file from the internet or whatever source and save it to temporary storage
     * @throws RetrievalException thrown for failed download, stops so you can fix it.
     */
    @Override
    public void download() throws RetrievalException {
        try {
            URL u = new URL(this.url);
            HttpURLConnection huc = (HttpURLConnection) u.openConnection();
            this.credentials.credentials.forEach((credentialType, stringStringMap) -> stringStringMap.forEach(huc::setRequestProperty));

            File out = new File(getTempFolder() + File.separator + this.fileName);

            if (out.exists()) Logger.getLogger().debug("[Download] Deleting from temporary folder " + out.getAbsolutePath() + " | Success:" + out.delete());

            Logger.getLogger().info("[Download] Downloading file... " + out.getAbsolutePath() + " from " + url);

            InputStream inputStream = huc.getInputStream();
            FileUtils.copyInputStreamToFile(inputStream, out);
            inputStream.close();

            this.downloadedFile = out;

        } catch(Exception e) {
            throw new RetrievalException(e);
        }
    }
}
