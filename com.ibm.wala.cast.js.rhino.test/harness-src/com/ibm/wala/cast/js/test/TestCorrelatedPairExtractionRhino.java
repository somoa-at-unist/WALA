/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.wala.cast.js.test;

import java.io.IOException;

import com.ibm.wala.cast.js.ipa.callgraph.correlations.CorrelationFinder;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.classLoader.SourceModule;
import org.mozilla.javascript.RhinoToAstTranslator;

public class TestCorrelatedPairExtractionRhino extends TestCorrelatedPairExtraction {
	protected CorrelationFinder makeCorrelationFinder() {
		return new CorrelationFinder(new CAstRhinoTranslatorFactory());
	}
	
	protected CAstEntity parseJS(CAstImpl ast, SourceModule module) throws IOException {
		RhinoToAstTranslator.resetGensymCounters();
		RhinoToAstTranslator translator = new RhinoToAstTranslator(ast, module, module.getName());
		CAstEntity entity = translator.translate();
		return entity;
	}
}