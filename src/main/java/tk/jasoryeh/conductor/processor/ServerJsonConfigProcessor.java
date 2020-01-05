package tk.jasoryeh.conductor.processor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.config.JenkinsConfiguration;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.config.ServerConfig;
import tk.jasoryeh.conductor.debug.DebugException;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.FileUtils;
import tk.jasoryeh.conductor.util.Utility;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ServerJsonConfigProcessor {

    /**
     * Being processing the server_cnf.json, this will loop through everything and download/unzip/delete/etc. if necessary
     * @param jsonObject parsed server_cnf.json
     * @return The configuration of the current server
     */
    public static ServerConfig process(final JsonObject jsonObject) {
        ServerConfig config = new ServerConfig(jsonObject);

        HashMap<String, String> vars = new HashMap<>();
        if (jsonObject.has("variables")) {
            JsonObject variables = jsonObject.get("variables").getAsJsonObject();
            for (Map.Entry<String, JsonElement> var : variables.entrySet()) {
                String key = var.getKey();
                String val = var.getValue().getAsString();
                vars.put(key, val);
            }
        }

        ArrayList<JsonObject> trees = new ArrayList<>();
        trees.add(jsonObject);
        // Includes just like another server_cnf but doesnt require name or anything
        if(jsonObject.has("includes")) {
            JsonArray includes = jsonObject.get("includes").getAsJsonArray();
            for (JsonElement include : includes) {
                String where = include.getAsString();

                String includeContent;
                if(Utility.isRemote(where)) {
                    includeContent = Utility.remoteFileToString(where);
                } else {
                    try {
                        includeContent = Utility.readToString(Utility.determineFileFromPath(where));
                    } catch(IOException e) {
                        L.w(String.format("Include [%s] cannot be loaded, skipping.", where));
                        e.printStackTrace();
                        continue;
                    }
                }
                JsonObject importedInclude = new JsonParser().parse(includeContent).getAsJsonObject();
                trees.add(importedInclude);

                if (importedInclude.has("variables")) {
                    JsonObject variables = importedInclude.get("variables").getAsJsonObject();
                    for (Map.Entry<String, JsonElement> var : variables.entrySet()) {
                        String key = var.getKey();
                        String val = var.getValue().getAsString();
                        if(!vars.containsKey(key)) {
                            vars.put(key, val);
                        } else {
                            L.i("Skipping include variable (duplicate): " + key + "=>" + val);
                        }
                    }
                }
            }
        }

        config.setLaunchType(jsonObject.has("launchType")
                ? ServerConfig.LaunchType.valueOf(jsonObject.get("launchType").getAsString().toUpperCase())
                : config.getLaunchType());

        // Files
        for (int i = 0; i < trees.size(); i++) {
            JsonObject treeJson = trees.get(i);
            L.i(treeJson);
            boolean isInclude = treeJson.has("iam");
            boolean proc = processTree(treeJson.get("tree").getAsJsonObject(), config, "", true, vars, isInclude);
            if (!proc && i == 0) {
                L.e("Something went wrong, shutting down.");
                Conductor.shutdown(true);
            } else if(!proc) {
                String icl = treeJson.has("iam") ? treeJson.get("iam").getAsString() : treeJson.toString();
                L.e("Something went wrong with processing an include. Include: " + icl);
                Conductor.shutdown(true);
            }
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
    private static boolean processTree(final JsonObject jsonObject, ServerConfig conf, String parents,
                                       boolean recursive, Map<String, String> vars, boolean isInclude) {
        for (Map.Entry<String, JsonElement> stringJsonElementEntry : jsonObject.entrySet()) {
            if(!stringJsonElementEntry.getValue().isJsonObject()) continue;

            // if(stringJsonElementEntry.getKey().equals(conf.getLaunchFile())) conf.setLaunchFilePresent(true);

            processObject(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue().getAsJsonObject(), conf,
                    parents, recursive, vars, isInclude);
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
    private static boolean processObject(String fileName, JsonObject obj, ServerConfig conf, String parents,
                                         boolean recursive, Map<String, String> vars, boolean isInclude) {
        L.empty(); // newline, secretly identify separate objects.

        String objCWD = Utility.cwdAndSep() +
                (parents.equalsIgnoreCase("") ? "" : (File.separator + parents + File.separator));
        File objectFile = new File(objCWD + fileName);

        L.i("[File] Now working on... " + objectFile.getAbsolutePath());

        prepareFile(objectFile,
                conf.isOverwrite(),
                (obj.has("overwrite") && obj.get("overwrite").getAsBoolean()),
                isInclude);

        String fileType = obj.get("type").getAsString();
        L.i("[File|W] Processing " + objectFile.getAbsolutePath() + " as a " + fileType);
        if(fileType.equalsIgnoreCase("folder")) {
            if (!objectFile.mkdir() || !objectFile.mkdirs()) {
                if(objectFile.exists()) {
                    L.w("[File|W] Unable to create directory(s) " + objectFile.getAbsolutePath() +
                            " but the directory exists, so this shouldn't be a problem.");
                } else {
                    L.e("[File|W] Unable to create directory(s) " + objectFile.getAbsolutePath() +
                            " trying to continue anyways");
                }
            }

            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                if(obj.get("contents") != null) {
                    L.i("Scanning content configuration for " + objectFile.getAbsolutePath() + " " + fileName + "]");
                    for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                        // Tree only, folders can't have "text content"
                        if(contents.getKey().equalsIgnoreCase("tree")) {
                            L.i("Processing configuration... [" + fileName + "]");
                            processTree(contents.getValue().getAsJsonObject(), conf,
                                    parents + File.separator + fileName, recursive, vars, isInclude);
                            L.i("Done processing [" + fileName + "]");
                        }
                    }
                }
                return true;
            } else {
                RetrieveType type = RetrieveType.valueOf(retrieval.get("method").getAsString().toUpperCase());

                Credentials credentials = new Credentials();
                Credentials.CredentialType ct = Credentials.CredentialType
                        .valueOf(obj.get("requestType") == null ? "DEFAULT" : obj.get("requestType").getAsString().toUpperCase());

                if(obj.get("authDetails") != null) {
                    for (Map.Entry<String, JsonElement> authDetails : obj.get("authDetails").getAsJsonObject().entrySet()) {
                        // (should) do nothing if auth details aren't present
                        credentials.addToRequiredCredentials(ct, authDetails.getKey(), authDetails.getValue().getAsString());
                    }
                }

                switch(type) {
                    case URL:
                        try {
                            URLDownloader ud = new URLDownloader(retrieval.get("url").getAsString(), fileName,
                                    conf.isOverwrite(), credentials);
                            ud.download();
                            // Unzip
                            if (retrieval.has("unzipRequired") && retrieval.get("unzipRequired").getAsBoolean()) {
                                L.i("[File|W/UZ] Unzipping to" + objectFile.getAbsolutePath());
                                objectFile.mkdirs();
                                ZipUtil.unpack(ud.getDownloadedFile(), objectFile);
                                //System.gc();
                            } else {
                                L.i("[File|W] Copying files to " + objectFile.toPath());
                                Files.copy(ud.getDownloadedFile().toPath(), objectFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }

                            // ... move on
                        } catch(IOException ioe) {
                            ioe.printStackTrace();
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
        } else if(fileType.equalsIgnoreCase("file")) {
            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                try {
                    if (!objectFile.createNewFile()) {
                        if(isInclude) {
                            L.w("[File|Duplicate] Duplicate file in include.");
                        } else {
                            throw new IOException("Unable to create new file " + objectFile.getAbsolutePath());
                        }
                    }
                    for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                        // Tree only, folders can't have "text content"
                        if(contents.getKey().equalsIgnoreCase("content")) {
                            FileOutputStream fos = new FileOutputStream(objectFile);
                            String out = contents.getValue().getAsString()
                                    .replaceAll(Pattern.quote("{NEWLINE}"), System.lineSeparator());

                            // detect if we shouldn't apply variables, don't do it if we said no.
                            boolean applyVars = !obj.has("applyVariables") || obj.get("applyVariables").getAsBoolean();
                            if(applyVars) {
                                for (Map.Entry<String, String> entry : vars.entrySet()) {
                                    String lookFor = Pattern.quote("{{" + entry.getKey() + "}}");
                                    out = out.replaceAll(lookFor, entry.getValue());
                                }
                            }

                            fos.write(out.getBytes());
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
                Credentials.CredentialType ct = Credentials.CredentialType
                        .valueOf((!retrieval.has("requestType") ? "DEFAULT" : retrieval.get("requestType").getAsString()));

                if(retrieval.get("authDetails") != null) {
                    for (Map.Entry<String, JsonElement> authDetails : retrieval.get("authDetails").getAsJsonObject().entrySet()) {
                        // (should) do nothing if auth details aren't present
                        credentials.addToRequiredCredentials(ct, authDetails.getKey(), authDetails.getValue().getAsString());
                    }
                }

                switch(type) {
                    case URL:
                        try {
                            URLDownloader ud = new URLDownloader(retrieval.get("url").getAsString(), fileName,
                                    conf.isOverwrite(), credentials);
                            ud.download();

                            Files.copy(
                                    ud.getDownloadedFile().toPath(),
                                    objectFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            // ... move on
                        } catch(IOException ioe) {
                            ioe.printStackTrace();
                            Conductor.shutdown(true);
                        }
                        break;
                    case JENKINS:
                        try {
                            if(!retrieval.has("jenkinsAuth")) {
                                L.e("No jenkins job details specified for " + fileName);
                                Conductor.shutdown(true);
                                return false;
                            }
                            JsonObject jenkinsAuth = retrieval.get("jenkinsAuth").getAsJsonObject();

                            String job, artifact;
                            int jobNum = -1;
                            if(!jenkinsAuth.has("job") || !jenkinsAuth.has("artifact")) {
                                L.e("No job or artifact specified for " + fileName);
                                Conductor.shutdown(true);
                                return false;
                            } else {
                                job = jenkinsAuth.get("job").getAsString();
                                artifact = jenkinsAuth.get("artifact").getAsString();
                                jobNum = jenkinsAuth.has("number") ? jenkinsAuth.get("number").getAsInt() : jobNum;
                            }

                            JenkinsConfiguration jenkinsConfig;
                            if(jenkinsAuth.has("host") && jenkinsAuth.has("username")
                                    && jenkinsAuth.has("passwordOrToken")) {
                                jenkinsConfig = new JenkinsConfiguration(
                                        jenkinsAuth.get("host").getAsString(),
                                        jenkinsAuth.get("username").getAsString(),
                                        jenkinsAuth.get("passwordOrToken").getAsString());
                            } else {
                                jenkinsConfig = LauncherConfiguration.get().getJenkins();
                            }

                            JenkinsDownloader jenkinsDownloader = new JenkinsDownloader(jenkinsConfig, job, artifact,
                                    jobNum, fileName, true);
                            jenkinsDownloader.download();

                            Files.copy(
                                    jenkinsDownloader.getDownloadedFile().toPath(),
                                    objectFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            //... move on
                        } catch(IOException ioe) {
                            ioe.printStackTrace();
                            Conductor.shutdown(true);
                        }
                        break;
                    case SPECIFIED:
                        // Specified not supposed to land here
                        throw new UnsupportedOperationException();
                    default:
                        // Other types
                        throw new UnsupportedOperationException();
                }
            }
        }
        return true;
    }

    public static void prepareFile(File file, boolean mainOverwrite, boolean overwrite, boolean isInclude) {
        if(isInclude) {
            if(overwrite) {
                if(file.exists()) {
                    if(FileUtils.delete(file)) {
                        L.i("[File|D] Deleted directory: " + file.getAbsolutePath() + " (poof)");
                    } else {
                        L.w("[File|D] Unable to delete directory, this may be overwritten later. " + file.getAbsolutePath());
                    }
                } else {
                    L.i("[File|D] Not deleting (nothing here): " + file.getAbsolutePath());
                }
                // include obj says to ow
            } else {
                L.i("[File|D] Not deleting (include, no overwrite property detected): " + file.getAbsolutePath());
            }
        } else { // master
            if(mainOverwrite || overwrite) {
                // overwrite?
                if(file.exists()) {
                    if (FileUtils.delete(file)) {
                        L.i("[File|D] Deleted directory: " + file.getAbsolutePath() + " (poof)");
                    } else {
                        L.w("[File|D] Unable to delete directory, this may be overwritten later. " + file.getAbsolutePath());
                    }
                } else {
                    L.i("[File|D] Not deleting (nothing here): " + file.getAbsolutePath());
                }
            } else {
                L.i("[File|D] Not deleting (no overwrite / not detected): " + file.getAbsolutePath());
            }
        }
    }

    public enum RetrieveType {
        URL("url"), JENKINS("jenkins"), SPECIFIED("specified");

        @Getter
        private final String equivalent;

        RetrieveType(String equivalent) {
            this.equivalent = equivalent;
        }
    }
    public enum ServerType {
        JAVA("java", "-jar"),
        PYTHON("python", ""),
        PYTHON3("python3", ""),
        PYTHON2("python2", ""),
        NODEJS("node", ""),
        BASH("bash", "");

        @Getter
        private final String equivalent;
        @Getter
        private final String params;

        ServerType(String equivalent, String params) {
            this.equivalent = equivalent;
            this.params = params;
        }
    }
}
