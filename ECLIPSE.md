# Importing IPED into the Eclipse IDE

IPED is a multi-module Maven project. The Eclipse project files (`.project`, `.classpath` and `.settings`) are not versioned: Eclipse generates them from the `pom.xml` files when the project is imported.

## 1. Install a JDK with JavaFX

Several modules (`iped-viewers`, `iped-geo`, `iped-app`) use JavaFX, which is not part of a regular JDK. Install the **Full** package of BellSoft Liberica JDK, which bundles JavaFX:

- Download it from https://bell-sw.com/pages/downloads/, selecting the Java version required by IPED (see [Building](README.md#building)) and the package **Full JDK**. The "Standard" package does not include JavaFX.

On Debian, Ubuntu and derived Linux distributions it can be installed with `apt` instead:

1. Add the BellSoft APT repository, following the "APT Repository" section of the [Liberica JDK installation guide](https://bell-sw.com/pages/repositories/).
2. Install the `-full` package, which is the one with JavaFX, replacing `11` with the Java version required by IPED:
   ```
   sudo apt install bellsoft-java11-full
   ```

The JDK is installed in `/usr/lib/jvm/bellsoft-java11-full-amd64`, which is the folder to be used as `JRE home` in step 3.

The same JDK is used to build IPED from the command line.

## 2. Install Eclipse

Use a recent "Eclipse IDE for Java Developers" (or "Enterprise Java and Web Developers"). It must support the Java version required by IPED and already includes the Maven integration (m2e).

## 3. Register the Liberica Full JDK in Eclipse

1. Open `Window > Preferences > Java > Installed JREs` (on macOS, `Eclipse > Settings...`).
2. Click `Add...`, choose `Standard VM` and set `JRE home` to the folder of the Liberica Full JDK.
3. Check the new entry to make it the default JRE.
4. Open `Java > Installed JREs > Execution Environments`, select the environment of the Java version required by IPED (e.g. `JavaSE-11`) and check the Liberica Full JDK in the list of compatible JREs.

Step 4 is needed because the imported projects are bound to an execution environment, not directly to a JDK. If that environment points to a JDK without JavaFX, all `javafx.*` imports are reported as errors.

## 4. Import the project

1. Clone the repository (see [Building](README.md#building)).
2. In Eclipse, choose `File > Import... > Maven > Existing Maven Projects`.
3. Set `Root Directory` to the folder of the clone, keep all the modules selected and click `Finish`.

Eclipse downloads the dependencies and builds the workspace, which takes a few minutes the first time.

## 5. After changing the branch or the `pom.xml` files

The generated project files keep the Java version and the dependencies of the moment they were created. After switching to a branch that uses another Java version, or after a change in the dependencies, select all the projects and run `Maven > Update Project...` (`Alt+F5`).

## Running IPED

The Eclipse build only compiles the classes. To get a runnable IPED, with its libraries, configuration files and tools, build it with Maven as described in [Building](README.md#building) and use the snapshot created in the `target/release` folder.

## Debugging a processing

IPED is not launched from Eclipse. It is started from the command line with the Java debug agent, which connects to Eclipse, where the breakpoints are set. In this mode Eclipse is the one that listens on a port and IPED connects to it (`server=n`).

### In Eclipse

1. Open `Run > Debug Configurations...` and create a new `Remote Java Application`.
2. Set `Project` to `iped-app`, so the sources of all the modules are found.
3. Set `Connection Type` to `Standard (Socket Listen)` and `Port` to a free port, e.g. `5005`.
4. Set `Connection limit` to `2`. IPED runs as two Java processes, a small launcher and the process that does the processing, and both connect to Eclipse.
5. Click `Debug`. Eclipse starts waiting for the connections.

### Starting IPED

Build IPED with Maven and, in the snapshot folder (`target/release/iped-<version>`), start the processing with the debug agent before `-jar`, using the port chosen above:

```
java -agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=5005 -jar iped.jar -d <evidence> -o <output folder>
```

- Eclipse must already be listening when IPED starts. If a process can not connect, it ends with `transport error 202: connect failed: Connection refused`. The same happens to the processing process if the connection limit is 1.
- Two entries show up in the `Debug` view. The breakpoints of the processing code are hit in the second one.
- To debug the first lines executed by a process, use `suspend=y`: each process waits for the debugger before running.
- If Eclipse runs on another computer, use `address=<host>:<port>`.
- The debugged classes are the ones of the snapshot, not the ones compiled by Eclipse. Build IPED again with Maven after changing the code, otherwise the breakpoints do not match the running code.
