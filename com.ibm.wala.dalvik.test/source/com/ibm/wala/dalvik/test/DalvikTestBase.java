package com.ibm.wala.dalvik.test;

import static com.ibm.wala.properties.WalaProperties.ANDROID_RT_JAR;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.NestedJarFileModule;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.core.tests.shrike.DynamicCallGraphTestBase;
import com.ibm.wala.dalvik.test.callGraph.DalvikCallGraphTestBase;
import com.ibm.wala.dalvik.util.AndroidAnalysisScope;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.io.TemporaryFile;

public abstract class DalvikTestBase extends DynamicCallGraphTestBase {

  public static Properties walaProperties;

  static {
    try {
      walaProperties = WalaProperties.loadProperties();
    } catch (WalaException e) {
      walaProperties = null;
    }
  }

  public static String getJavaJar(AnalysisScope javaScope) throws IOException {
  	Module javaJar = javaScope.getModules(javaScope.getApplicationLoader()).iterator().next();
  	if (javaJar instanceof JarFileModule) {
  		String javaJarPath = ((JarFileModule)javaJar).getAbsolutePath();
  		return javaJarPath;
  	} else {
  		assert javaJar instanceof NestedJarFileModule : javaJar;
  		File F = File.createTempFile("android", ".jar");
  		//F.deleteOnExit();
  		System.err.println(F.getAbsolutePath());
  		TemporaryFile.streamToFile(F, ((NestedJarFileModule)javaJar).getNestedContents());
  		return F.getAbsolutePath();
  	}
  }

  public static File convertJarToDex(String jarFile) throws IOException {
  	File f = File.createTempFile("convert", ".dex");
  	//f.deleteOnExit();
  	System.err.println(f);
  	com.android.dx.command.Main.main(new String[]{"--dex", "--output=" + f.getAbsolutePath(), jarFile});
  	return f;
  }

  public static URI[] androidLibs() {
    System.err.println(System.getProperty("java.vm.name"));
    if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
        List<URI> libs = new ArrayList<URI>();
        for(File f : new File("/system/framework/").listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
              String name = pathname.getName();
              return 
                  (name.startsWith("core") || name.startsWith("framework")) && 
                  (name.endsWith("jar") || name.endsWith("apk"));
            } 
          })) 
        {
          System.out.println("adding " + f);
          libs.add(f.toURI());
        }
        return libs.toArray(new URI[ libs.size() ]);
    } else {
      List<URI> libs = new ArrayList<URI>();
      try {
        for(File lib : new File(walaProperties.getProperty(ANDROID_RT_JAR)).listFiles(new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            return name.endsWith("dex") || name.endsWith("jar") || name.endsWith("apk");
          } 
        })) {
          System.out.println("adding " + lib);
          libs.add(lib.toURI());
        }
      } catch (Exception e) {
        System.out.println("unexpected " + e);
        for(String l : WalaProperties.getJ2SEJarFiles()) {
          libs.add(new File(l).toURI());
        }
        try {
          File jarFile = TemporaryFile.urlToFile("android.jar", DalvikCallGraphTestBase.class.getClassLoader().getResource("android.jar"));
          libs.add(jarFile.toURI());
        } catch (IOException e1) {
          assert false : e1;
        } 
      } 
      return libs.toArray(new URI[ libs.size() ]);
    }
  }

  public static AnalysisScope makeDalvikScope(boolean useAndroidLib, String dexFileName) throws IOException {
    AnalysisScope scope = 
  		useAndroidLib?
  		AndroidAnalysisScope.setUpAndroidAnalysisScope(
  			new File(dexFileName).toURI(), 
  			CallGraphTestUtil.REGRESSION_EXCLUSIONS,
  			CallGraphTestUtil.class.getClassLoader(),
  			androidLibs()):
  		AndroidAnalysisScope.setUpAndroidAnalysisScope(
  			new File(dexFileName).toURI(), 
  			CallGraphTestUtil.REGRESSION_EXCLUSIONS,
  			CallGraphTestUtil.class.getClassLoader());
    return scope;
  }

}