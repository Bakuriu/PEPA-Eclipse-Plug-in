/*******************************************************************************
 * Copyright (c) 2009 University of Edinburgh.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the BSD Licence, which accompanies this feature
 * and can be downloaded from http://groups.inf.ed.ac.uk/pepa/update/licence.txt
 ******************************************************************************/
package uk.ac.ed.inf.biopepa.core.compiler;

import uk.ac.ed.inf.biopepa.core.BioPEPAException;
import uk.ac.ed.inf.biopepa.core.dom.Expression;
import uk.ac.ed.inf.biopepa.core.dom.VariableDeclaration;
import uk.ac.ed.inf.biopepa.core.dom.VariableDeclaration.Kind;

/**
 * @author Mirco
 * 
 */
public class FunctionalRateCompiler extends AbstractDefinitionCompiler {

	public FunctionalRateCompiler(ModelCompiler compiler, Kind kind, VariableDeclaration dec) {
		super(compiler, kind, dec);
	}

	@Override
	protected Data doGetData() throws BioPEPAException {
		// just performs static analysis of right hand side
		// which is evaluated during analysis
		FunctionalRateData data = new FunctionalRateData(dec.getName().getIdentifier(), dec);
		Expression rhs = dec.getRightHandSide();
		FunctionalRateCheckerVisitor v = new FunctionalRateCheckerVisitor(compiler);
		rhs.accept(v);
		data.setRightHandSide(v.getExpressionNode());
		data.predefinedLaw = v.predefinedLaw;
		return data;
	}

}
