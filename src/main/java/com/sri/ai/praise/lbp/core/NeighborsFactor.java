/*
 * Copyright (c) 2013, SRI International
 * All rights reserved.
 * Licensed under the The BSD 3-Clause License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * 
 * http://opensource.org/licenses/BSD-3-Clause
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of the aic-praise nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sri.ai.praise.lbp.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import com.google.common.annotations.Beta;
import com.sri.ai.expresso.api.Expression;
import com.sri.ai.expresso.helper.Apply;
import com.sri.ai.expresso.helper.SubExpressionsDepthFirstIterator;
import com.sri.ai.grinder.api.RewritingProcess;
import com.sri.ai.grinder.helper.Justification;
import com.sri.ai.grinder.helper.Trace;
import com.sri.ai.grinder.library.set.extensional.ExtensionalSet;
import com.sri.ai.grinder.library.set.extensional.NormalizeExtensionalUniSet;
import com.sri.ai.praise.BracketedExpressionSubExpressionsProvider;
import com.sri.ai.praise.LPIUtil;
import com.sri.ai.praise.lbp.LBPRewriter;
import com.sri.ai.praise.model.IsRandomVariableValueExpression;
import com.sri.ai.util.Util;
import com.sri.ai.util.collect.PredicateIterator;

/**
 * Default implementation of {@link LBPRewriter#R_neigh_f}.
 * 
 * @author oreilly
 * 
 */
@Beta
public class NeighborsFactor extends AbstractLBPHierarchicalRewriter implements LBPRewriter {

	//
	private NormalizeExtensionalUniSet rNormalizeExtensionalSet = new NormalizeExtensionalUniSet();
	
	public NeighborsFactor() {
		updateChildRewriter(null, rNormalizeExtensionalSet);
	}
	
	@Override
	public String getName() {
		return R_neigh_f;
	}
	
	/**
	 * @see LBPRewriter#R_neigh_f
	 */
	@Override
	public Expression rewriteAfterBookkeeping(Expression expression, RewritingProcess process) {
		
		// Assert input arguments
		// an expression in the form: Neigh([ Ef ])
		if (!expression.hasFunctor(LPIUtil.FUNCTOR_NEIGHBOR) || expression.numberOfArguments() != 1) {
			throw new IllegalArgumentException("Invalid input argument expression:"+expression);
		}
			
		Expression factor = expression.get(0);
		
		Expression result = null;

		Trace.log("return R_basic(R_normalize_extensional_uniset({ v1, ..., vn }))");
		Trace.log("         where v1, ..., vn are the subexpressions of Ef that are random variable value expressions.");

		if (Justification.isEnabled()) {
			Justification.begin(expression);
		}
		
		Expression factorValue = LPIUtil.getFactorValueExpression(factor, process);

		SubExpressionsDepthFirstIterator subExpressionsDepthFirstIterator =
			new SubExpressionsDepthFirstIterator(factorValue);

		Iterator<Expression> randomVariableValuesIterator =
			new PredicateIterator<Expression>(
					subExpressionsDepthFirstIterator,
					new IsRandomVariableValueExpression(process));

		List<Expression> randomVariables =
			Util.mapIntoList(
					randomVariableValuesIterator,
					new Apply(BracketedExpressionSubExpressionsProvider.SYNTAX_TREE_LABEL));

		// Ensure duplicates are removed (want to maintain order).
		randomVariables   = new ArrayList<Expression>(new LinkedHashSet<Expression>(randomVariables));
		Expression uniset = ExtensionalSet.makeUniSet(randomVariables);

		Justification.beginStepWithJustification("definition of neighbors of a factor");
		Justification.endStepWithResult(uniset);

		Justification.beginStepWithJustification("checking for duplicate neighbors");
		Expression normalizedUniSet = rNormalizeExtensionalSet.rewrite(uniset, process);
		result = process.rewrite(R_basic, normalizedUniSet);
		Justification.endStepWithResult(result);
		
		Justification.end();

		return result;
	}
}