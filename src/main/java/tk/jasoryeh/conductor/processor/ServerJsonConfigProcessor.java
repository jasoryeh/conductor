package tk.jasoryeh.conductor.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.config.Configuration;
import tk.jasoryeh.conductor.config.ServerConfig;
import tk.jasoryeh.conductor.debug.DebugException;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.downloaders.exceptions.RetrievalException;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.Environment;
import tk.jasoryeh.conductor.util.FileUtils;
import tk.jasoryeh.conductor.util.Utility;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerJsonConfigProcessor {
    /**
     * Being processing the server_cnf.json, this will loop through everything and download/unzip/delete/etc. if necessary
     * @param jsonObject parsed server_cnf.json
     * @return The configuration of the current server
     */
    public static ServerConfig process(final JsonObject jsonObject) {
        String name = jsonObject.get("templateName").getAsString();
        ServerType type = ServerType.valueOf(jsonObject.get("type").getAsString().toUpperCase());

        String launchFile = jsonObject.get("launch").getAsString();
        String launchOptions = jsonObject.get("launchOptions").getAsString();

        boolean overwrite = jsonObject.get("overwrite").getAsBoolean();

        // Files
        JsonObject tree = jsonObject.get("tree").getAsJsonObject();

        ServerConfig config = new ServerConfig(name, type, launchFile, launchOptions, overwrite, tree);

        if(jsonObject.has("launchType")) {
            String launchType = jsonObject.get("launchType").getAsString();
            if(launchType.equalsIgnoreCase("process")) {
                config.setLaunchType(ServerConfig.LaunchType.PROCESS);
            } else if(launchType.equalsIgnoreCase("classloader")) {
                config.setLaunchType(ServerConfig.LaunchType.CLASSLOADER);
            } else {
                throw new UnsupportedOperationException("Unknown launchType option " + launchType);
            }
        } // no else, defaults to process automagically

        if (!processTree(tree, config, "", true)) {
            Logger.getLogger().error("Something went wrong, shutting down.");
            new DebugException().printStackTrace();
            Conductor.shutdown(true);
        }

        return config;
    }

    /**
     * Process trees of files
     * @param jsonObject object of tree
     * @param conf Server configuration
     * @param parents Parents of tree
     * @param recursive whether to not to go through all of them (auto-loop)
     * @return Success/fail
     */
    private static boolean processTree(final JsonObject jsonObject, ServerConfig conf, String parents, boolean recursive) {
        for (Map.Entry<String, JsonElement> stringJsonElementEntry : jsonObject.entrySet()) {
            if(!stringJsonElementEntry.getValue().isJsonObject()) continue;

            // if(stringJsonElementEntry.getKey().equals(conf.getLaunchFile())) conf.setLaunchFilePresent(true);

            processObject(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue().getAsJsonObject(), conf, parents, recursive);
        }
        return true;
    }

    /**
     * Process files/folders in `tree`
     * @param fileName name of file
     * @param obj json object of the file/folder
     * @param conf server configuration object
     * @param parents folder to put the file/folder under ex. plugins/Some/Secret/Folder <- will place for example file test.txt in NO slashes beginning
     * @param recursive go through each objects under all the other trees (auto-loop)
     * @return success or not
     */
    private static boolean processObject(String fileName, JsonObject obj, ServerConfig conf, String parents, boolean recursive) {
        Logger.getLogger().info(Logger.EMPTY);

        File f = new File(Utility.getCWD() + File.separator +
                (parents.equalsIgnoreCase("") ? "" : File.separator + parents + File.separator) + fileName);
        Logger.getLogger().info("Now working on... " + f.getName());

        // Check if the file should be overwritten every startup
        boolean fileOverwrite = obj.get("overwrite") == null ? conf.isOverwrite() : obj.get("overwrite").getAsBoolean();
        if(f.exists() && !fileOverwrite) {
            Logger.getLogger().info("Skipping " + f.getAbsolutePath() + ", configuration specified not to re-download");
            return true;
        }

        // Try to delete, else warn about overwrite
        if(f.exists() && (conf.isOverwrite() && fileOverwrite)) {
            // Try deleting like a folder
            if(FileUtils.delete(f)) {
                Logger.getLogger().info("[File|D] Deleted directory: " + f.getAbsolutePath() + " (poof)");
            } else {
                Logger.getLogger().warn("[File|D] Unable to delete directory, this may be overwritten later. " + f.getAbsolutePath());
            }
        } else {
            L.i("[File|D] Not deleting (no overwrite): " + f.getAbsolutePath());
        }

        Logger.getLogger().info("[File|W] Create " + f.getAbsolutePath());

        if(obj.get("type").getAsString().equalsIgnoreCase("folder")) {
            if (!f.mkdir()) {
                Logger.getLogger().error("[File|W] Unable to create directory " + f.getAbsolutePath());
                Logger.getLogger().error("[File|W] Trying to continue anyways");
            }

            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                if(obj.get("contents") != null) {
                    Logger.getLogger().info("Scanning content configuration for " + f.getAbsolutePath() + " " + fileName + "]");
                    for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                        // Tree only, folders can't have "text content"
                        if(contents.getKey().equalsIgnoreCase("tree")) {
                            Logger.getLogger().info("Processing configuration... [" + fileName + "]");
                            processTree(contents.getValue().getAsJsonObject(), conf, parents + File.separator + fileName, recursive);
                            Logger.getLogger().info("Done processing [" + fileName + "]");
                        }
                    }
                }
                return true;
            } else {
                RetrieveType type = RetrieveType.valueOf(retrieval.get("method").getAsString().toUpperCase());

                Credentials credentials = new Credentials();
                Credentials.CredentialType ct = Credentials.CredentialType.valueOf(obj.get("requestType") == null ? "DEFAULT" : obj.get("requestType").getAsString().toUpperCase());

                if(obj.get("authDetails") != null) {
                    for (Map.Entry<String, JsonElement> authDetails : obj.get("authDetails").getAsJsonObject().entrySet()) {
                        // (should) do nothing if auth details aren't present
                        credentials.addToRequiredCredentials(ct, authDetails.getKey(), authDetails.getValue().getAsString());
                    }
                }

                switch(type) {
                    case URL:
                        try {
                            URLDownloader ud = new URLDownloader(retrieval.get("url").getAsString(), fileName, conf.isOverwrite(), credentials);
                            ud.download();
                            // Unzip
                            if (retrieval.get("unzipRequired") != null && retrieval.get("unzipRequired").getAsBoolean()) {
                                Logger.getLogger().info("[File|W/UZ] Unzipping to" + f.getAbsolutePath());
                                f.mkdirs();
                                ZipUtil.unpack(ud.getDownloadedFile(), f);
                                System.gc();
                            } else {
                                Logger.getLogger().info("[File|W] Copying files to " + f.toPath());
                                Files.copy(ud.getDownloadedFile().toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            ud = null;
                            System.gc();
                            // Finish unzip

                            // ... move on
                        } catch(RetrievalException | IOException re) {
                            re.printStackTrace();
                            Conductor.shutdown(true);
                        }
                        break;
                    case JENKINS:
                        // TODO: Jenkins WIP
                        throw new UnsupportedOperationException();
                    case SPECIFIED:
                        // Specified not supposed to land here
                        throw new UnsupportedOperationException();
                    default:
                        // Other types
                        throw new UnsupportedOperationException();
                }
            }
        } else if(obj.get("type").getAsString().equalsIgnoreCase("file")) {
            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                try {
                    if (!f.createNewFile()) {
                        throw new IOException("Unable to create new file " + f.getAbsolutePath());
                    }
                    for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                        // Tree only, folders can't have "text content"
                        if(contents.getKey().equalsIgnoreCase("content")) {
                            FileOutputStream fos = new FileOutputStream(f);
                            fos.write(contents.getValue().getAsString().replaceAll("\\{NEWLINE}", System.lineSeparator()).getBytes());
                            fos.close();
                        }
                    }
                } catch(IOException io) {
                    io.printStackTrace();
                }
                return true;
            } else {
                RetrieveType type = RetrieveType.valueOf(retrieval.get("method").getAsString().toUpperCase());

                Credentials credentials = new Credentials();
                Credentials.CredentialType ct = Credentials.CredentialType.valueOf((retrieval.get("requestType") == null ? "DEFAULT" : retrieval.get("requestType").getAsString()));

                if(retrieval.get("authDetails") != null) {
                    for (Map.Entry<String, JsonElement> authDetails : retrieval.get("authDetails").getAsJsonObject().entrySet()) {
                        // (should) do nothing if auth details aren't present
                        credentials.addToRequiredCredentials(ct, authDetails.getKey(), authDetails.getValue().getAsString());
                    }
                }

                Downloader downloader;

                switch(type) {
                    case URL:
                        downloader = new URLDownloader(retrieval.get("url").getAsString(), fileName, conf.isOverwrite(), credentials);

                        break;
                    case JENKINS:
                        Configuration config = Conductor.getInstance().getConfig();
                        boolean endNoAuth = !config.entryExists("jenkinsHost")
                                || !config.entryExists("jenkinsUsername")
                                || !config.entryExists("jenkinsPasswordOrToken");
                        if(!retrieval.get("auth").getAsBoolean() && endNoAuth) {
                            Logger.getLogger().error("Jenkins Authentication not present, not continuing.");
                            Conductor.shutdown(true);
                        }

                        JsonObject jenkinsAuth = retrieval.get("jenkinsAuth").getAsJsonObject();

                        boolean useSpecifiedAuth = retrieval.get("auth").getAsBoolean();

                        boolean endNoInfo = (jenkinsAuth.get("job") == null) || (jenkinsAuth.get("artifact") == null) || (jenkinsAuth.get("number") == null);
                        if(endNoInfo) {
                            Logger.getLogger().error("Jenkins artifact information not present, not continuing.");
                            Conductor.shutdown(true);
                        }

                        String job = jenkinsAuth.get("job").getAsString();
                        String artifact = jenkinsAuth.get("artifact").getAsString();
                        int number = jenkinsAuth.get("number").getAsInt();

                        String host = useSpecifiedAuth ? jenkinsAuth.get("host").getAsString() : config.getString("jenkinsHost");
                        String username = useSpecifiedAuth ? (jenkinsAuth.get("username") == null ? "" : jenkinsAuth.get("username").getAsString()) : config.getString("jenkinsUsername");
                        String passwordOrToken = useSpecifiedAuth ? (jenkinsAuth.get("passwordOrToken") == null ? "" : jenkinsAuth.get("passwordOrToken").getAsString()) : config.getString("jenkinsPasswordOrToken");

                        downloader = new JenkinsDownloader(host, job, artifact, number, username, passwordOrToken, fileName, true, new Credentials());

                        break;
                    case SPECIFIED:
                        // Specified not supposed to land here
                        throw new UnsupportedOperationException();
                    default:
                        // Other types
                        throw new UnsupportedOperationException();
                }

                Environment allowThreading = Environment.get("allowDLThreading");

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            downloader.download();
                            Files.copy(
                                    downloader.getDownloadedFile().toPath(),
                                    f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException | RetrievalException e) {
                            L.e("Unable to download/copy " + downloader.getFileName());
                            e.printStackTrace();
                            Conductor.shutdown(true);
                        }

                        retrieveThreads.remove(Thread.currentThread());
                    }
                });

                if(allowThreading.exists() && allowThreading.getAsBoolean()) {
                    thread.start();

                    retrieveThreads.add(thread);
                } else {
                    thread.run();
                }
            }
        }
        return true;
    }

    // exp

    public static CopyOnWriteArrayList<Thread> retrieveThreads = new CopyOnWriteArrayList<>();

    // end

    public enum RetrieveType {
        URL("url"), JENKINS("jenkins"), SPECIFIED("specified");

        @Getter
        private final String equivalent;

        RetrieveType(String equivalent) {
            this.equivalent = equivalent;
        }
    }
    public enum ServerType {
        JAVA("java");

        @Getter
        private final String equivalent;

        ServerType(String equivalent) {
            this.equivalent = equivalent;
        }
    }
}
