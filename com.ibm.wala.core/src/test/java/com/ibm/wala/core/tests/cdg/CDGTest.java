package com.ibm.wala.core.tests.cdg;

import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.drivers.PDFCallGraph;
import com.ibm.wala.examples.drivers.PDFControlDependenceGraph;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.traverse.BFSIterator;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.io.FileUtil;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.PDFViewUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.function.Predicate;
import org.junit.Test;

public class CDGTest {

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
    System.out.println("path: " + cp.toString());
    runWithClassPath(cp.toString(), "org.joda.time.Partial.with(Lorg/joda/time/DateTimeFieldType;I)Lorg/joda/time/Partial;");
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

  public static Process runWithClassPath(String cp, String methodSig) throws IOException {
    try {
      AnalysisScope scope =
          AnalysisScopeReader.makeJavaBinaryAnalysisScope(
              cp, (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
      System.out.println("scope: " + scope.toString());
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
      Iterator<SSAInstruction> IRIter = ir.iterateAllInstructions();

      print_cdg(cdg);
      shortestDistanceToNode(cdg, cdg.getNode(41));

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

  public static void print_cdg(ControlDependenceGraph<ISSABasicBlock> cdg){
    System.out.println("print cdg");
    System.out.println("graph node num: " + cdg.getNumberOfNodes());
    //System.out.println(cdg.getNode(0).toString());
    for (ISSABasicBlock n : cdg) {
      System.out.println(n.getNumber() + " -> " + cdg.getSuccNodeNumbers(n).toString());
      System.out.println(cdg.getPredNodeNumbers(n).toString() + " -> " + n.getNumber());
    }
  }

  /**
   * Use BFS to find shortest distance
   * @param cdg control dependency graph
   * @param target target node
   */
  public static void shortestDistanceToNode(ControlDependenceGraph<ISSABasicBlock> cdg, ISSABasicBlock target){
    System.out.println("Find shortest path to node " + target.toString());
    ReverseBFSIterator<ISSABasicBlock> bfs = new ReverseBFSIterator<ISSABasicBlock>(cdg, target);
    while(bfs.hasNext()){
      ISSABasicBlock n = bfs.next();
      System.out.println(n.getNumber() + " -> " + cdg.getSuccNodeNumbers(n).toString());
      System.out.println(cdg.getPredNodeNumbers(n).toString() + " -> " + n.getNumber());
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
