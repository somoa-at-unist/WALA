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
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.PDFViewUtil;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

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
    runWithClassPathFromFile(cp.toString(), "org/joda/time/Partial.java:459");
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

  public static void runWithClassPathFromFile(String cp, String filename) {
    System.out.println("###################################\nrun with class name from file");
    String[] file_analysis = filename.split(":");
    String file = file_analysis[0];
    int line = Integer.parseInt(file_analysis[1]);
    System.out.println("cp: " + cp);
    System.out.println("file: " + file);
    try {
      String filePath = cp + File.separatorChar + file;
      AnalysisScope scope =
              AnalysisScopeReader.makeJavaBinaryAnalysisScope(
                      cp, (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
      ClassHierarchy cha = ClassHierarchyFactory.make(scope);
      String target = StringStuff.slashToDot(file);
      target = target.substring(0, target.lastIndexOf('.'));
      for (IClass cl: cha) {
        for (IMethod im: cl.getAllMethods()) {
          String pattern = im.getSignature().split("\\(")[0];
          if(target.equals(pattern.substring(0, pattern.lastIndexOf('.')))) {
            if (im.getMinLineNumber() <= line && line <= im.getMaxLineNumber()) {
              System.out.println("line " + im.getMinLineNumber() + " to " + im.getMaxLineNumber() + " : " + im.getSignature());
              inFileBasicBlockDistances(cp, file, line, im);
              return;
            }
          }
        }
      }
    } catch (WalaException | IOException e) {
      e.printStackTrace();
      return;
    }
  }

  public static ISSABasicBlock getBasicBlockByLine(IR ir, ControlDependenceGraph<ISSABasicBlock> cdg, int line) throws InvalidClassFileException {
    for(int i = 0; i < cdg.getNumberOfNodes(); i++) {
      ISSABasicBlock n = cdg.getNode(i);
      if(n.getFirstInstructionIndex() < 0) continue;
      if (ir.getBCIndex(n.getFirstInstructionIndex()) <= line && line <= ir.getBCIndex(n.getFirstInstructionIndex())) {
        return n;
      }
    }
    return null;
  }
  /**
   * Use BFS to find shortest distance
   * @param cdg control dependency graph
   * @param target target node
   */
  public static ArrayList<Integer> distancesToNode(ControlDependenceGraph<ISSABasicBlock> cdg, ISSABasicBlock target) {
    ArrayList<Integer> result = new ArrayList<>();
    for(int i = 0; i < cdg.getNumberOfNodes(); i++){
      result.add(-1);
    }
    int distance = 0;
    ArrayList<ISSABasicBlock> Q = new ArrayList<>();
    HashSet<ISSABasicBlock> visited = HashSetFactory.make();
    Q.add(target);
    int index = 0;
    result.set(target.getNumber(), distance);
    while(Q.size() > index) {
      ISSABasicBlock n = Q.get(index);
      index++;
      distance = result.get(n.getNumber()) + 1;
      for (ISSABasicBlock parent : Iterator2Iterable.make(cdg.getPredNodes(n))) {
        if (visited.add(parent)) {
          Q.add(parent);
          result.set(parent.getNumber(), distance);
        }
      }
    }
    return result;
  }

  public static void inFileBasicBlockDistances(String cp, String filename, int line, IMethod m) throws IOException {
    AnalysisOptions options = new AnalysisOptions();
    options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
    IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
    IR ir = cache.getIR(m, Everywhere.EVERYWHERE);

    if (ir == null) {
      Assertions.UNREACHABLE("Null IR for " + m);
    }
    ControlDependenceGraph<ISSABasicBlock> cdg =
            new ControlDependenceGraph<>(ir.getControlFlowGraph());

    ISSABasicBlock target = null;
    try {
      target = getBasicBlockByLine(ir, cdg, line);
    } catch (Exception e) {
      e.printStackTrace();
    }
    ArrayList<Integer> result = distancesToNode(cdg, target);
    System.out.println("target basic block: " + target.toString());
    Properties wp = null;
    try {
      wp = WalaProperties.loadProperties();
      wp.putAll(WalaExamplesProperties.loadProperties());
    } catch (WalaException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
    }
    String csvFile =
            wp.getProperty(WalaProperties.OUTPUT_DIR)
                    + File.separatorChar
                    + "output.csv";
    FileWriter fw = new FileWriter(csvFile);
    fw.append("File,Line,dist\n");
    for(int i = 0; i < cdg.getNumberOfNodes(); i++) {
      if(result.get(i) < 0) {
        System.out.println(i + " to node " + target.getNumber() + " distance: UNREACHABLE");
      } else {
        System.out.println(
                i + " to node " + target.getNumber() + " distance: " + result.get(i));
      }
      ISSABasicBlock n = cdg.getNode(i);
      try {
        if(n.getFirstInstructionIndex() < 0) continue;
        for(int j = ir.getBCIndex(n.getFirstInstructionIndex()); j <= ir.getBCIndex(n.getLastInstructionIndex()); j++) {
          fw.append(filename + "," + j + "," + result.get(i) + "\n");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    fw.close();
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
