/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.dynamic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.ibm.wala.util.PlatformUtil;

/**
 * A Java process launcher
 */
public class JavaLauncher extends Launcher {

  /**
   * @param programArgs arguments to be passed to the Java program
   * @param mainClass Declaring class of the main() method to run.
   * @param classpathEntries Paths that will be added to the default classpath
   */
  public static JavaLauncher make(String programArgs, String mainClass, List<String> classpathEntries, Logger logger) {
    return new JavaLauncher(programArgs, mainClass, true, classpathEntries, false, false, logger);
  }

  /**
   * @param programArgs arguments to be passed to the Java program
   * @param mainClass Declaring class of the main() method to run.
   * @param inheritClasspath Should the spawned process inherit all classpath entries of the currently running process?
   * @param classpathEntries Paths that will be added to the default classpath
   * @param captureOutput should the launcher capture the stdout from the subprocess?
   * @param captureErr should the launcher capture the stderr from the subprocess?
   */
  public static JavaLauncher make(String programArgs, String mainClass, boolean inheritClasspath, List<String> classpathEntries,
      boolean captureOutput, boolean captureErr, Logger logger) {
    if (mainClass == null) {
      throw new IllegalArgumentException("null mainClass");
    }
    return new JavaLauncher(programArgs, mainClass, inheritClasspath, classpathEntries, captureOutput, captureErr, logger);
  }

  /**
   * arguments to be passed to the Java program
   */
  private String programArgs;

  /**
   * Declaring class of the main() method to run.
   */
  private final String mainClass;

  /**
   * Should the spawned process inherit all classpath entries of the currently running process?
   */
  private final boolean inheritClasspath;

  /**
   * Should assertions be enabled in the subprocess? default false.
   */
  private boolean enableAssertions;

  /**
   * Paths that will be added to the default classpath
   */
  private final List<String> xtraClasspath = new ArrayList<String>();

  /**
   * A {@link Thread} which spins and drains stdout of the running process.
   */
  private Thread stdOutDrain;

  /**
   * A {@link Thread} which spins and drains stderr of the running process.
   */
  private Thread stdErrDrain;
  
  /**
   * Absolute path of the 'java' executable to use.
   */
  private String javaExe;
  
  /**
   * Extra args to pass to the JVM
   */
  private String vmArgs;

  private JavaLauncher(String programArgs, String mainClass, boolean inheritClasspath, List<String> xtraClasspath,
      boolean captureOutput, boolean captureErr, Logger logger) {
    super(captureOutput, captureErr, logger);
    this.programArgs = programArgs;
    this.mainClass = mainClass;
    this.inheritClasspath = inheritClasspath;
    if (xtraClasspath != null) {
      this.xtraClasspath.addAll(xtraClasspath);
    }
    this.javaExe = defaultJavaExe();
  }
  
  public String getJavaExe() {
    return javaExe;
  }

  public void setJavaExe(String javaExe) {
    this.javaExe = javaExe;
  }

  public void setProgramArgs(String s) {
    this.programArgs = s;
  }

  public String getProgramArgs() {
    return programArgs;
  }

  public String getMainClass() {
    return mainClass;
  }

  public List<String> getXtraClassPath() {
    return xtraClasspath;
  }

  @Override
  public String toString() {
    StringBuffer result = new StringBuffer(super.toString());
    result.append(" (programArgs: ");
    result.append(programArgs);
    result.append(", mainClass: ");
    result.append(mainClass);
    result.append(", xtraClasspath: ");
    result.append(xtraClasspath);
    result.append(')');
    return result.toString();
  }

  /**
   * @return the string that identifies the java executable file
   */
  public static String defaultJavaExe() {
    String java = System.getProperty("java.home") + File.separatorChar + "bin" + File.separatorChar + "java";
    return java;
  }

  /**
   * Launch the java process.
   */
  public Process start() throws IllegalArgumentException, IOException {
    String cp = makeClasspath();

    String heap = " -Xmx800M ";

    // on Mac, need to pass an extra parameter so we can cleanly kill child
    // Java process
    String signalParam = PlatformUtil.onMacOSX() ? " -Xrs " : "";

    String ea = enableAssertions ? " -ea " : "";
    String vmArgs = getVmArgs() == null ? "" : getVmArgs();

    String cmd = javaExe + heap + signalParam + cp + " " + makeLibPath() + " " + ea + " " + vmArgs + " " + getMainClass() + " " + getProgramArgs();

    Process p = spawnProcess(cmd);
    stdErrDrain = isCaptureErr() ? captureStdErr(p) : drainStdErr(p);
    stdOutDrain = isCaptureOutput() ? captureStdOut(p) : drainStdOut(p);
    return p;
  }

  private String makeLibPath() {
    String libPath = System.getProperty("java.library.path");
    if (libPath == null) {
      return "";
    } else {
      return "-Djava.library.path=" + libPath;
    }
  }

  /**
   * Wait for the spawned process to terminate.
   * @throws IllegalStateException if the process has not been started
   */
  public void join() {
    if (stdOutDrain == null || stdErrDrain == null) {
      throw new IllegalStateException("process not started.  illegal to join()");
    }
    try {
      stdOutDrain.join();
      stdErrDrain.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new InternalError("Internal error in JavaLauncher.join()");
    }
    if (isCaptureErr()) {
      Drainer d = (Drainer) stdErrDrain;
      setStdErr(d.getCapture().toByteArray());
    }
    if (isCaptureOutput()) {
      Drainer d = (Drainer) stdOutDrain;
      setStdOut(d.getCapture().toByteArray());
    }
  }

  /**
   * Compute the classpath for the spawned process
   */
  private String makeClasspath() {
    String cp = inheritClasspath ? System.getProperty("java.class.path") : "";
    if (getXtraClassPath() == null || getXtraClassPath().isEmpty()) {
      return " -classpath " + quoteStringIfNeeded(cp);
    } else {
      for (Iterator it = getXtraClassPath().iterator(); it.hasNext();) {
        cp += File.pathSeparatorChar;
        cp += (String) it.next();
      }
      return " -classpath " + quoteStringIfNeeded(cp);
    }
  }

  /**
   * If the input string contains a space, quote it (for use as a classpath). TODO: Figure out how to make a Mac happy with quotes.
   * Trailing separators are unsafe, so we have to escape the last backslash (if present and unescaped), so it doesn't escape the
   * closing quote.
   */
  public static String quoteStringIfNeeded(String s) {
    s = s.trim();
    // Check if there's a space. If not, skip quoting to make Macs happy.
    // TODO: Add the check for an escaped space.
    if (s.indexOf(' ') == -1) {
      return s;
    }
    if (s.charAt(s.length() - 1) == '\\' && s.charAt(s.length() - 2) != '\\') {
      s += '\\'; // Escape the last backslash, so it doesn't escape the quote.
    }
    return '\"' + s + '\"';
  }

  public boolean isEnableAssertions() {
    return enableAssertions;
  }

  public void setEnableAssertions(boolean enableAssertions) {
    this.enableAssertions = enableAssertions;
  }

  public void setVmArgs(String vmArgs) {
    this.vmArgs = vmArgs;
  }

  public String getVmArgs() {
    return vmArgs;
  }

}
