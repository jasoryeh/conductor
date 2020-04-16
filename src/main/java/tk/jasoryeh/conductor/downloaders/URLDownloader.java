package tk.jasoryeh.conductor.downloaders;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.L;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
    public boolean download() {
        String useThisUrl = this.url;

        boolean basic = false;
        String basicAuthString = null;
        String locationDomain = useThisUrl.split("/")[2];
        if(locationDomain.contains("@")) {
            basic = true;
            basicAuthString = locationDomain.split("@")[0];
            useThisUrl = useThisUrl.replaceFirst(basicAuthString, "");
        }

        URL u = new URL(useThisUrl);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        if(basic) {
            huc.setRequestProperty("Authorization", new String(Base64.encodeBase64(basicAuthString.getBytes(StandardCharsets.UTF_8))));
        }
        this.credentials.credentials.forEach((credentialType, stringStringMap) -> stringStringMap.forEach(huc::setRequestProperty));

        File out = new File(getTempFolder() + File.separator + this.outputFileName);

        if (out.exists()) this.log("Deleting from temporary folder " + out.getAbsolutePath() + " | Success:" + out.delete());

        this.log("Downloading file... " + out.getAbsolutePath() + " from " + url);

        InputStream inputStream = huc.getInputStream();
        FileUtils.copyInputStreamToFile(inputStream, out);
        inputStream.close();

        this.downloadedFile = out;

        return true;
    }

    private void log(String msg) {
        L.i("[URL] " + msg);
    }
}
