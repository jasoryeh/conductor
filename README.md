`# conductor
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/jasoryeh/conductor/maven.yml)

A tool to update, download, remove servers with one jar file. 
The configuration defines the layout of a program's filesystem 
by text as well as where to locate resources for each file in 
the filesystem.

Requires Jave 8 and above (8+)

## Why?
1. Simplify updates across networks/in container builds
2. Well-defined known-working filesystem layout that can be 
   reproduced and easily shared may be useful in some use cases 
   (such as in ephemeral applications)
3. Assists with continuous deployment of servers where 
   development assets may be built and updated frequently.
4. For fun, think of this like a "game launcher" but for servers
5. Templatizing configuration supports ease of deployment for 
   end-users and systems administrators.

## Configuration
### serverlauncher.properties
See `resources/serverlauncher.properties` for a template. 

- Typically `serverlauncher.properties` and placed in the same folder as `conductor.jar`
- Alternatively (when CONDUCTOR_ISCONFIGLESS is present) the configuration can be specified as environment variables in the format of `CONDUCTOR_SERVERLAUNCHER_PROPERTIES_<KEY>` for each key where special characters are replaced with `_`
### server_cnf.json
See `resources/sample.config.json` for a template.

## Usage

### What you need to start:
1. `conductor.jar`: A build of conductor, can be named anything, but we will reference it as just `conductor.jar`
   - If specified in the Conductor configuration (see below), all builds will try and upgrade themselves if a source to retrieve upgrades is specified2. A Conductor configuration:
2. Your template configuration: Your program's folder layout, we call this the "template"
   - Typically `server_cnf.json` and placed in the same folder as `conductor.jar`
   - Alternatively, if specified otherwise in the Conductor configuration, it can be named otherwise OR can be served/generated from a remote asset server
3. (if applicable) Relevant remote sources (jenkins, asset server) online and serving the resources specified in your template
   - Conductor will typically abort if the resource in inaccessible or fails to be downloaded, but additional protections are not yet implemented

When all of those are in place, simply `java -jar conductor.jar`, Conductor will exit gracefully (exit code 0) if all sources are downloaded and the template is successfully installed.

### Templates
Conductor assembles your program's file structure using a configuration that is called a "template". Templates are essentially enhanced blueprints that not only indicate the structure of the program's folder (as the name implies) but also substitutions in these files that may dynamically change from system to system.

At the root of a template exists two root elements, `_conductor` and `filesystem`
- `_conductor`: Contains metadata and information about secrets and variables that may change
- `runtime`: Optional configuration- if you would like Conductor to handle starting your program for you, configure this option.
- `filesystem`: Contains a layout of the filesystem that conductor will install, top level elements in the `filesystem` element act as a file at the same level as `conductor.jar`

#### Dynamically changing variables and secrets
Conductor recognizes that when many complex services are deployed autonomously (such as in game systems) where configuration, updates, and values may vary across instances quickly, provisioning those changes must be fast too. The solution is variables and secrets.

Variables are simple key-value maps that specify simple text replacements to make in templates.

Secrets are configuration objects that specify potentially sensitive information associated with accessing a remote asset server via a Conductor plugin.

In order to enable more complex configuration options, a configuration hierarchy is applied (highest to lowest where higher configuration overwrites lower hierarchy configuration):
1. Environment
2. Included templates
3. Root template

#### Conductor plugins
This program was built with the goal of making provisioning of server changes across multiple platforms and instances easy, and as such the program sometimes needs access to remote sources that can provide assets and configurations, the plugin system was created so that such support can be easily provided when needed.

Each filesystem object can be managed by zero or more plugins, and each plugin executes in order of definition, secrets adjust or configure the behavior of these plugins.

Currently available plugins include:
1. HTTP
2. JENKINS

#### Template filesystem objects
The main attraction of the Conductor Template are the filesystem objects that specify the file/folder. There are currently only two filesystem objects:
1. File
2. Folder

##### File/Folder Object configuration
Key: File name and extension (e.g. `filename.ext`, `document.pdf`, `test.txt`, `some_directory`)

Value:
- `type`: `file`
- `plugins`: `[ { "type": "plugin_type", "plugin_config_option1": ..., ..., "plugin_config_optionN": ...,  } ]`
- `content`: 
  - `JSON Object` for folders
    - Where the key is the file name, and the value is another File Object from this definition
  - `JSON Array` of strings for files that are text-only, binary files are not supported at this moment

Folders differ from Files such that Folders will always apply the folder first before
evaluating nested objects within them.
