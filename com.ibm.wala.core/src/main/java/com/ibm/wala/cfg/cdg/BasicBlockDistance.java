package com.ibm.wala.cfg.cdg;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
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
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

public class BasicBlockDistance {
  public static void main(String[] args) {
    System.out.println("main!!!");
  }
  public BasicBlockDistance() {
    System.out.println("BasicBlock distance");
  }

  public void test_print() {
    runWithClassPathFromFile("/home/plase1/Docker/poracle/modules/WALA/com.ibm.wala.core/resources/test/Time4p/target/classes",
        "org/joda/time/Partial.java:459");
  }
  public void runWithClassPathFromFile(String cp, String filename) {
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
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
  }

  public ISSABasicBlock getBasicBlockByLine(IR ir, ControlDependenceGraph<ISSABasicBlock> cdg, int line) throws InvalidClassFileException {
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
  public ArrayList<Integer> distancesToNode(ControlDependenceGraph<ISSABasicBlock> cdg, ISSABasicBlock target) {
    ArrayList<Integer> result = new ArrayList<>();
    for(int i = 0; i < cdg.getNumberOfNodes(); i++){
      result.add(Integer.MAX_VALUE);
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

  public void inFileBasicBlockDistances(String cp, String filename, int line, IMethod m) throws IOException {
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
    } catch (Exception e) {
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
      if(result.get(i) < 0 || result.get(i) == Integer.MAX_VALUE) {
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
}
