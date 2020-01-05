package tk.jasoryeh.conductor.downloaders;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.L;
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

    @Getter
    @Setter
    protected Credentials credentials;

    /**
     * Downloader for file links
     * @param url https://example.com/files/example.file.ext
     * @param fileName example.file.ext or some other file name with extension
     * @param replaceIfExists if the file is found in temporary storage or already there, do you want to delete it and replace it?
     * @param credentials HTTP headers for authorization
     */
    public URLDownloader(String url, String fileName, boolean replaceIfExists, Credentials credentials) {
        super(fileName, replaceIfExists);
        this.url = url;
        this.credentials = credentials;
    }

    /**
     * Retrieve the file from the internet or whatever source and save it to temporary storage
     */
    @SneakyThrows
    @Override
    public void download() {
        URL u = new URL(this.url);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        this.credentials.credentials.forEach((credentialType, stringStringMap) -> stringStringMap.forEach(huc::setRequestProperty));

        File out = new File(getTempFolder() + File.separator + this.outputFileName);

        if (out.exists()) this.log("Deleting from temporary folder " + out.getAbsolutePath() + " | Success:" + out.delete());

        this.log("Downloading file... " + out.getAbsolutePath() + " from " + url);

        InputStream inputStream = huc.getInputStream();
        FileUtils.copyInputStreamToFile(inputStream, out);
        inputStream.close();

        this.downloadedFile = out;
    }

    private void log(String msg) {
        L.i("[URL] " + msg);
    }
}
