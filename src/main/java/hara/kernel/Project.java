package hara.kernel;

import hara.kernel.base.Parser;
import hara.kernel.base.RT;
import hara.kernel.maven.Maven;
import hara.lang.base.Ex;
import hara.lang.data.Keyword;
import hara.lang.data.types.IMapType;
import hara.lang.protocol.ILookup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class Project {

  private static final String PROJECT_FILE = "project.hara";
  private static final String SRC_DIR = "src";
  private static final String MAIN_FILE = "main.hrl";

  public static void create(String name) {
    if (name == null || name.isEmpty()) {
      System.err.println("Usage: hara new <project-name>");
      return;
    }

    File projectDir = new File(name);
    if (projectDir.exists()) {
      System.err.println("Directory " + name + " already exists.");
      return;
    }

    projectDir.mkdirs();
    new File(projectDir, SRC_DIR).mkdirs();

    String projectContent = "{:name \"" + name + "\"\n :dependencies []}";
    String mainContent = "(prn \"Hello, World from " + name + "!\")";

    try {
      Files.write(new File(projectDir, PROJECT_FILE).toPath(), projectContent.getBytes());
      Files.write(
          new File(new File(projectDir, SRC_DIR), MAIN_FILE).toPath(), mainContent.getBytes());
      System.out.println("Created new project: " + name);
    } catch (IOException e) {
      System.err.println("Failed to create project files: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static RT.Instance loadContext() {
    File projectFile = new File(PROJECT_FILE);
    if (!projectFile.exists()) {
      System.err.println(PROJECT_FILE + " not found. Are you in a hara project directory?");
      System.exit(1);
    }

    try {
      String content = new String(Files.readAllBytes(projectFile.toPath()));
      Object projectData = Parser.LispReader.readString(content, null);

      if (!(projectData instanceof IMapType)) {
        throw new Ex.Runtime("Invalid project.hara format. Expected a map.");
      }

      // Initialize Runtime
      var F = new Foundation();
      var rt = new RT.Instance<>(F, "ROOT");
      F.RTS.put(rt._key, rt);

      // Add src directory to classpath
      File srcDir = new File(SRC_DIR);
      if (srcDir.exists()) {
        java.net.URL srcUrl = srcDir.toURI().toURL();
        rt.pathAdd(srcUrl);
      }

      // Load Dependencies
      // Use ILookup to access map values
      // Fix: Use Keyword for lookup since keys are keywords like :dependencies
      Object depsObj = ((ILookup) projectData).lookup(Keyword.create("dependencies"), null);

      if (depsObj instanceof Iterable) {
        Iterable deps = (Iterable) depsObj;
        Iterator it = deps.iterator();
        while (it.hasNext()) {
          Object dep = it.next();
          if (dep instanceof String) {
            String coordinate = (String) dep;
            System.out.println("Loading dependency: " + coordinate);
            Maven.load.invoke(rt, coordinate);
          }
        }
      }

      return rt;
    } catch (IOException e) {
      throw Ex.Sneaky(e);
    }
  }

  public static void run() {
    RT.Instance rt = loadContext();
    Path mainPath = Paths.get(SRC_DIR, MAIN_FILE);
    if (!Files.exists(mainPath)) {
      System.err.println("Entry file not found: " + mainPath);
      System.exit(1);
    }

    try {
      String mainScript = new String(Files.readAllBytes(mainPath));

      hara.kernel.base.Reader rdr =
          new hara.kernel.base.Reader(new java.io.StringReader(mainScript));

      // Use a unique object for EOF marker
      Object EOF = new Object();

      while (true) {
        // Call LispReader.read with eofIsError=false and our EOF marker
        Object form = Parser.LispReader.read(rdr, false, EOF, false, null);
        if (form == EOF) {
          break;
        }
        rt.eval(form);
      }

    } catch (IOException e) {
      throw Ex.Sneaky(e);
    }
  }

  public static void repl() throws IOException {
    RT.Instance rt = loadContext();

    Foundation F = (Foundation) rt.getRoot();
    var server = new Server(F, "PRIMARY", Foundation.DEFAULT_PORT, null);
    F.SERVERS.put(server._key, server);
    server.start();

    System.out.println("Starting REPL with project context...");
    Repl repl = new Repl(rt);
    repl.run();
  }
}
