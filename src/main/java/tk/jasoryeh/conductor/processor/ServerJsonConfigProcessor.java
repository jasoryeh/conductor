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
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.L;
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
        // Server configuration (name, type, overwrite, etc.)
        ServerConfig config = new ServerConfig(jsonObject);

        // Server file trees to process
        ArrayList<JsonObject> trees = new ArrayList<>();
        // Add local/main server configuration
        trees.add(jsonObject);

        // Variables to replace in files
        HashMap<String, String> vars = new HashMap<>();
        if (jsonObject.has("variables")) {
            JsonObject variables = jsonObject.get("variables").getAsJsonObject();
            for (Map.Entry<String, JsonElement> var : variables.entrySet()) {
                String key = var.getKey();
                String val = var.getValue().getAsString();
                vars.put(key, val);
            }
        }

        /* Check for other server configurations to also process, these includes are not to overwrite current server
        configuration and files unless otherwise specified in each included file's "overwrite" property

        Includes are just like another server_cnf but doesnt require as many properties

        (Required) Includes must have: "iam", "tree"
        (Optional) Additional properties that can be set: "variables", "overwrite_variables"
         */
        if(jsonObject.has("includes")) {
            // has "includes", trying to get all of them now
            JsonArray includes = jsonObject.get("includes").getAsJsonArray();
            for (JsonElement include : includes) {
                // get each include's location (usually a url, or local file)
                String where = include.getAsString();

                String includeContent;
                if(Utility.isRemote(where)) {
                    // If the include is remotely stored, we will download it, and store it as this string
                    includeContent = Utility.remoteFileToString(where);
                } else {
                    // If this include is local, we will load it into this string
                    try {
                        includeContent = Utility.readToString(Utility.determineFileFromPath(where));
                        L.i(String.format("Include [%s] loaded.", where));
                    } catch(IOException e) {
                        L.e(String.format("Include [%s] cannot be loaded!", where));
                        e.printStackTrace();
                        Conductor.shutdown(true);
                        return null;
                    }
                }

                // Parse include string to json element -> object, and add it to what we will process
                JsonObject importedInclude = new JsonParser().parse(includeContent).getAsJsonObject();
                trees.add(importedInclude);

                // import variables from these includes
                if (importedInclude.has("variables")) {
                    // check if we want included variables to overwrite our main server configuration's
                    boolean overwriteVariables = importedInclude.has("overwrite_variables")
                            && importedInclude.get("overwrite_variables").getAsBoolean();
                    JsonObject variables = importedInclude.get("variables").getAsJsonObject();
                    for (Map.Entry<String, JsonElement> var : variables.entrySet()) {
                        String key = var.getKey();
                        String val = var.getValue().getAsString();
                        // skip existing/or not
                        if(!vars.containsKey(key) || overwriteVariables) {
                            vars.put(key, val);
                        } else {
                            L.i("Skipping include variable (duplicate): " + key + "=>" + val);
                        }
                    }
                }
            }
        }

        if(jsonObject.has("launchType")) { // default it "process"
            config.setLaunchType(ServerConfig.LaunchType
                    .valueOf(
                            jsonObject.get("launchType")
                                    .getAsString().toUpperCase()
                    )
            );
        }


        // Files, by numbers because we want to go in order (our main configuration should be first)
        for (int i = 0; i < trees.size(); i++) {
            JsonObject treeJson = trees.get(i);
            boolean isInclude = treeJson.has("iam") || i != 0;
            L.d("Is include: " + isInclude + ";Data: " + treeJson);
            boolean proc = processTree(treeJson.get("tree").getAsJsonObject(), config, "", true, vars, isInclude);
            if (!proc && i == 0) {
                // if our main configuration fails, we fail and shutdown
                L.e("Something went wrong, shutting down.");
                Conductor.shutdown(true);
            } else if(!proc) {
                // if an include fails, we also fail and shutdown
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
        // loop elements in tree
        for (Map.Entry<String, JsonElement> stringJsonElementEntry : jsonObject.entrySet()) {
            // elements should be objects, not arrays or values, etc. so we skip them
            if(!stringJsonElementEntry.getValue().isJsonObject()) continue;

            // process this element
            L.i("Working on " + stringJsonElementEntry.getKey() + " in " + parents);
            boolean success = processObject(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue().getAsJsonObject(), conf,
                    parents, recursive, vars, isInclude);
            if(!success) {
                // not well.
                L.e("Failed at " + stringJsonElementEntry + " in " + parents);
                return false;
            }
        }
        // if everything goes well, we should get back to here
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
        String objectCWD = Utility.cwdAndSep() +
                (parents.equalsIgnoreCase("") ? "" : (File.separator + parents + File.separator));
        File objectFile = new File(objectCWD + fileName);

        L.empty(); // newline, secretly identify separate objects.
        L.i("[File] Now working on... " + objectFile.getAbsolutePath());

        try {
            boolean processedAndReady = prepareFile(
                    objectFile,
                    conf.isOverwrite(),
                    isInclude ? (obj.has("overwrite") && obj.get("overwrite").getAsBoolean())
                            : (!obj.has("overwrite") || obj.get("overwrite").getAsBoolean()),
                    isInclude);

            if(!processedAndReady) {
                L.i("[File] Overwrite rule say we should skip " + objectFile.getAbsolutePath());
                return true;
            }
        } catch(IOException e) {
            L.e("[File] Something happened while preparing " + objectFile.getAbsolutePath() + " for overwrite, this is just to be safe.");
            return false;
        }

        String fileType = obj.get("type").getAsString();
        L.i("[File|W] Processing " + objectFile.getAbsolutePath() + " as a \"" + fileType + "\"");
        if(fileType.equalsIgnoreCase("folder")) {
            L.i("Working on folder contents for " + objectFile.getAbsolutePath());
            if(!objectFile.exists()) {
                // doesn't exist, make the folder
                if (!objectFile.mkdirs()) {
                    L.w("[File|W] Unable to create folder(s) " + objectFile.getAbsolutePath() +
                            " folder doesn't exist, so anything else in this folder is impossible.");
                    return false;
                } else {
                    L.i("[File|W] Created folder " + objectFile.getAbsolutePath());
                }
            } else {
                L.w("[File|W] Folder has existed, this shouldn't be a problem.");
            }

            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                // Don't retrieve from a remote source, tree is defined in the configuration
                if(obj.has("contents") && (obj.get("contents") instanceof JsonObject)) {
                    L.i("[File] Processing content configuration for " + objectFile.getAbsolutePath() + " " + fileName + "]");
                    for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                        // Tree only, folders can't have "text content"
                        if(contents.getKey().equalsIgnoreCase("tree")) {
                            L.i("[File] Processing configuration... [" + fileName + "]");
                            boolean success = processTree(contents.getValue().getAsJsonObject(), conf,
                                    parents.equals("") ? fileName : parents + File.separator + fileName, recursive, vars, isInclude);
                            if(!success) {
                                L.e("[File] Error processing [" + fileName + "]");
                                return false;
                            }
                            L.i("[File] Done processing [" + fileName + "]");
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
                            return false;
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
                return true;
            }
        } else if(fileType.equalsIgnoreCase("file")) {
            L.i("Working on file " + objectFile.getAbsolutePath());
            if(objectFile.exists()) {
                L.e("[File] Duplicate file, this is probably due to a missing overwrite property on this file.");
                if(isInclude) {
                    L.e("[File] This is probably because this is a include, if you want to overwrite " +
                            "existing files in an include you must set overwrite to true.");
                }
                return false;
            }

            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                // If defined in configuration, we can only write text based file types
                try {
                    if(objectFile.createNewFile() && (obj.has("contents") && obj.get("contents").getAsJsonObject().has("content"))) {
                        // Tree only, folders can't have "text content"
                        FileOutputStream fos = new FileOutputStream(objectFile);
                        String out = obj.get("contents").getAsJsonObject().get("content").getAsString()
                                .replaceAll(Pattern.quote("{NEWLINE}"), System.lineSeparator());

                        // detect if we shouldn't apply variables, don't do it if we said no, do it if we didn't say anything
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
                    return true;
                } catch(IOException io) {
                    io.printStackTrace();
                    return false;
                }
                // verify is unreachable return true;
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
                            return true;
                        } catch(IOException ioe) {
                            ioe.printStackTrace();
                            return false;
                        }
                    case JENKINS:
                        try {
                            if(!retrieval.has("jenkinsAuth")) {
                                L.e("No jenkins job details specified for " + fileName);
                                return false;
                            }
                            JsonObject jenkinsAuth = retrieval.get("jenkinsAuth").getAsJsonObject();

                            String job, artifact;
                            int jobNum = -1;
                            if(!jenkinsAuth.has("job") || !jenkinsAuth.has("artifact")) {
                                L.e("No job or artifact specified for " + fileName);
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
                            boolean jenkinsOK = jenkinsDownloader.download();
                            if(!jenkinsOK) {
                                L.e("Unable to download artifact for " + objectFile.getAbsolutePath());
                                return false;
                            }

                            Files.copy(
                                    jenkinsDownloader.getDownloadedFile().toPath(),
                                    objectFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            //... move on
                            return true;
                        } catch(IOException ioe) {
                            ioe.printStackTrace();
                            return false;
                        }
                    case SPECIFIED:
                        // Specified not supposed to land here
                        throw new UnsupportedOperationException();
                    default:
                        // Other types
                        throw new UnsupportedOperationException();
                }
                // verify is unreachable: return false;
            }
        }

        L.e("\"" + fileType + "\" not found! Please fix this before continuing!");
        return false;
    }

    /**
     *
     * @param file Object being processed
     * @param mainOverwrite What main configuration told us to do about overwrites
     * @param configIndividualOverwrite What the individual file's overwrite rule is
     * @param isInclude Is this file from an include?
     * @return true - should continue (ready to write to); false - don't process this file any further (shouldn't write to)
     * @throws IOException thrown if we should stop everything, equivalent to a false, but worse
     */
    public static boolean prepareFile(File file, boolean mainOverwrite, boolean configIndividualOverwrite, boolean isInclude) throws IOException {
        if(!file.exists()) {
            L.i("The file " + file.getAbsolutePath() + " doesn't exist, and we didn't delete anything here so it's ready to write to!");
            return true;
        }

        // if it's a include, we only look at our individual include file's overwrite rules if it wants
        // to change stuff that main might have, then it will force itself, otherwise if it doesn't say
        // to overwrite then if it exists we should skip by returning false
        // success should return true

        // exception thrown here if we fail to overwrite because we should overthrow everything!
        if(isInclude) {
            // includes, we only look at individual file overwrite property
            if(configIndividualOverwrite) {
                if(file.isDirectory()) {
                    if(FileUtils.delete(file)) {
                        L.i("[File|D] Deleted folder: " + file.getAbsolutePath()
                                + " (previously existed, folder, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", include)");
                        return true;
                    } else {
                        L.e("[File|D] Unable to delete folder! " + file.getAbsolutePath()
                                + " (previously existed, folder, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", include)");
                        throw new IOException("Cannot prepare file!");
                    }
                } else {
                    if(FileUtils.delete(file)) {
                        L.i("[File|D] Deleted file: " + file.getAbsolutePath()
                                + " (previously existed, file, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", include)");
                        return true;
                    } else {
                        L.e("[File|D] Unable to delete file! " + file.getAbsolutePath()
                                + " (previously existed, file, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", include)");
                        throw new IOException("Cannot prepare file!");
                    }
                }
            }
        } else {
            // main configuration, also look at main config
            // if main says no nothing goes
            // if main says yes individual says no, just that doesn't go
            // if main says no individual says no, still can't go
            if(mainOverwrite && configIndividualOverwrite) {
                if(file.isDirectory()) {
                    if(FileUtils.delete(file)) {
                        L.i("[File|D] Deleted folder: " + file.getAbsolutePath()
                                + " (previously existed, folder, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", main)");
                        return true;
                    } else {
                        L.e("[File|D] Unable to delete folder! " + file.getAbsolutePath()
                                + " (previously existed, folder, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", main)");
                        throw new IOException("Cannot prepare file!");
                    }
                } else {
                    if(FileUtils.delete(file)) {
                        L.i("[File|D] Deleted file: " + file.getAbsolutePath() +
                                " (previously existed, file, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", main)");
                        return true;
                    } else {
                        L.e("[File|D] Unable to delete file! " + file.getAbsolutePath()
                                + " (previously existed, file, " + mainOverwrite + ", "
                                + configIndividualOverwrite + ", main)");
                        throw new IOException("Cannot prepare file!");
                    }
                }
            }
        }

        if(file.isDirectory()) {
            L.w("[File|D] Configuration told us to leave existing files alone, so we weren't sure what to do with "
                    + file.getAbsolutePath() + " (previously existed, file, false all, " + (isInclude ? "include" : "main") + " )"
                    + " however this is a folder, so we will assume things will be done to the files in the folder.");
            return true;
        } else {
            L.w("[File|D] Configuration told us to leave existing files alone, so we weren't sure what to do with "
                    + file.getAbsolutePath() + " (previously existed, folder, " + mainOverwrite + ", "
                    + configIndividualOverwrite + ", " + (isInclude ? "include" : "main") + " )");
            L.w("[File|D] We will be skipping this file, " + file.getAbsolutePath());
            // we just skip this file, don't throw exception since it is safe to skip and continue
            return false;
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
