package com.ibm.wala.core.tests.cdg;

import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.drivers.PDFCallGraph;
import com.ibm.wala.examples.drivers.PDFControlDependenceGraph;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.PDFViewUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.Test;

public class MethodTest {

  public static final boolean SANITIZE_CFG = false;

  public static final String PDF_FILE = "cdg.pdf";

  @Test
  public void runCDGTestWithJar() throws IOException {
    Path jarFile = FileSystems.getDefault().getPath(System.getProperty("user.dir"),
        "resources", "test", "JLex.jar");
    run(jarFile.toString(), "JLex.CUtility.ASSERT(Z)V");
  }

  @Test
  public void testPatch180() throws IOException {
    Path cp = FileSystems.getDefault().getPath(System.getProperty("user.dir"),
        "resources", "test", "Time4p", "target", "classes");
    runWithClassPath(cp.toString(), "Lorg/joda/time/Partial");
  }

  /**
   * Usage: GVControlDependenceGraph -appJar [jar file name] -sig [method signature] The "jar file
   * name" should be something like "c:/temp/testdata/java_cup.jar" The signature should be
   * something like "java_cup.lexer.advance()V"
   */
  public static void main(String[] args) throws IOException {
    System.out.println("Working Directory = " + System.getProperty("user.dir"));
    run(args);
  }

  /**
   * @param args -appJar [jar file name] -sig [method signature] The "jar file name" should be
   * something like "c:/temp/testdata/java_cup.jar" The signature should be something like
   * "java_cup.lexer.advance()V"
   */
  public static Process run(String[] args) throws IOException {
    validateCommandLine(args);
    return run(args[1], args[3]);
  }

  /**
   * @param appJar should be something like "c:/temp/testdata/java_cup.jar"
   * @param methodSig should be something like "java_cup.lexer.advance()V"
   */
  public static Process run(String appJar, String methodSig) throws IOException {
    try {
      if (PDFCallGraph.isDirectory(appJar)) {
        appJar = PDFCallGraph.findJarFiles(new String[]{appJar});
      }
      AnalysisScope scope =
          AnalysisScopeReader.makeJavaBinaryAnalysisScope(
              appJar, (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));

      ClassHierarchy cha = ClassHierarchyFactory.make(scope);

      MethodReference mr = StringStuff.makeMethodReference(methodSig);

      IMethod m = cha.resolveMethod(mr);
      if (m == null) {
        System.err.println("could not resolve " + mr);
        throw new RuntimeException();
      }
      AnalysisOptions options = new AnalysisOptions();
      options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
      IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
      IR ir = cache.getIR(m, Everywhere.EVERYWHERE);

      if (ir == null) {
        Assertions.UNREACHABLE("Null IR for " + m);
      }

      System.err.println(ir.toString());
      ControlDependenceGraph<ISSABasicBlock> cdg =
          new ControlDependenceGraph<>(ir.getControlFlowGraph());

      Properties wp = null;
      try {
        wp = WalaProperties.loadProperties();
        wp.putAll(WalaExamplesProperties.loadProperties());
      } catch (WalaException e) {
        e.printStackTrace();
        Assertions.UNREACHABLE();
      }
      String psFile =
          wp.getProperty(WalaProperties.OUTPUT_DIR)
              + File.separatorChar
              + PDFControlDependenceGraph.PDF_FILE;
      String dotFile =
          wp.getProperty(WalaProperties.OUTPUT_DIR)
              + File.separatorChar
              + PDFTypeHierarchy.DOT_FILE;
      String dotExe = "dot"; // wp.getProperty(WalaExamplesProperties.DOT_EXE);
      String gvExe = "evince"; //wp.getProperty(WalaExamplesProperties.PDFVIEW_EXE);

      DotUtil.<ISSABasicBlock>dotify(cdg, PDFViewUtil.makeIRDecorator(ir), dotFile, psFile, dotExe);

      return PDFViewUtil.launchPDFView(psFile, gvExe);

    } catch (WalaException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return null;
    }
  }

  public static void runWithClassPath(String cp, String classId) throws IOException {
    try {
      AnalysisScope scope =
          AnalysisScopeReader.makeJavaBinaryAnalysisScope(
              cp, (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));

      ClassHierarchy cha = ClassHierarchyFactory.make(scope);

      for (IClass cl : cha) {
        if (cl.getName().toString().equals(classId)) {
          String sourceFileName = cl.getSourceFileName();
          for (IMethod m : cl.getAllMethods()) {
            System.out.println(m.getSignature() + ": " + m.getMinLineNumber() + " - " + m.getMaxLineNumber());
          }
        }
      }
    } catch (WalaException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }


  /**
   * Validate that the command-line arguments obey the expected usage.
   *
   * <p>Usage:
   *
   * <ul>
   *   <li>args[0] : "-appJar"
   *   <li>args[1] : something like "c:/temp/testdata/java_cup.jar"
   *   <li>args[2] : "-sig"
   *   <li>args[3] : a method signature like "java_cup.lexer.advance()V"
   * </ul>
   *
   * @throws UnsupportedOperationException if command-line is malformed.
   */
  static void validateCommandLine(String[] args) {
    if (args.length != 4) {
      throw new UnsupportedOperationException("must have at exactly 4 command-line arguments");
    }
    if (!args[0].equals("-appJar")) {
      throw new UnsupportedOperationException(
          "invalid command-line, args[0] should be -appJar, but is " + args[0]);
    }
    if (!args[2].equals("-sig")) {
      throw new UnsupportedOperationException(
          "invalid command-line, args[2] should be -sig, but is " + args[0]);
    }
  }
}
