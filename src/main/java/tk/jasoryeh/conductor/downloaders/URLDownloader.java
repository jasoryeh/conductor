package tk.jasoryeh.conductor.downloaders;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.util.TerminalColors;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Retrieve files from a link
 */
public class URLDownloader extends Downloader {
    @Getter
    private String url;

    @Getter
    @Setter
    protected Map<String, String> headers;

    /**
     * Downloader for file links
     * @param destination Destination
     * @param overwrite if the file is found in temporary storage or already there, do you want to delete it and replace it?
     * @param url https://example.com/files/example.file.ext
     * @param headers HTTP headers for authorization
     */
    public URLDownloader(File destination, boolean overwrite, String url, Map<String, String> headers) {
        super(destination, overwrite);
        this.url = url;
        this.headers = headers;
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
            useThisUrl = useThisUrl.replaceFirst(Pattern.quote(basicAuthString + "@"), "");
            this.log("Using BASIC authentication. Auth length " + basicAuthString.length());
        }

        HttpURLConnection huc = (HttpURLConnection) new URL(useThisUrl).openConnection();
        if(basic) {
            String basicB64 = new String(Base64.encodeBase64(basicAuthString.getBytes(StandardCharsets.UTF_8)));
            huc.setRequestProperty("Authorization", "Basic " + basicB64);
            this.log("Appended authorizaton header, b64 len:" + basicB64.length());
        }

        this.headers.entrySet().forEach(entry -> huc.setRequestProperty(entry.getValue(), entry.getKey()));

        // Protect against 403 by setting default user-agent.
        if(huc.getRequestProperty("User-Agent") == null) {
            huc.setRequestProperty("User-Agent", "conductor, Java");
        }

        File out = this.destination;

        if (out.exists()) this.log("Deleting from temporary folder " + out.getAbsolutePath() + " | Success:" + out.delete());

        this.log("Downloading file... " + out.getAbsolutePath() + " from " + url);

        InputStream inputStream = huc.getInputStream();
        FileUtils.copyInputStreamToFile(inputStream, out);
        inputStream.close();

        this.log(TerminalColors.GREEN.wrap("Successfully transferred ") + out.getAbsolutePath());

        return true;
    }

    private void log(String msg) {
        L.i("[URL] " + msg);
    }
}
