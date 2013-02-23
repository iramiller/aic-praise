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
import java.util.Arrays;
import java.util.List;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.sri.ai.expresso.api.Expression;
import com.sri.ai.expresso.helper.Expressions;
import com.sri.ai.expresso.helper.IsApplicationOf;
import com.sri.ai.grinder.api.RewritingProcess;
import com.sri.ai.grinder.helper.GrinderUtil;
import com.sri.ai.grinder.helper.Justification;
import com.sri.ai.grinder.helper.Trace;
import com.sri.ai.grinder.helper.concurrent.RewriteOnBranch;
import com.sri.ai.grinder.library.FunctorConstants;
import com.sri.ai.grinder.library.controlflow.IfThenElse;
import com.sri.ai.grinder.library.set.Sets;
import com.sri.ai.grinder.library.set.extensional.ExtensionalSet;
import com.sri.ai.grinder.library.set.intensional.IntensionalSet;
import com.sri.ai.grinder.library.set.tuple.Tuple;
import com.sri.ai.praise.LPIUtil;
import com.sri.ai.praise.lbp.LBPRewriter;
import com.sri.ai.util.Util;

/**
 * Default implementation of {@link LBPRewriter#R_prod_factor}.
 * 
 * @author oreilly
 * 
 */
@Beta
public class ProductFactor extends AbstractLBPHierarchicalRewriter implements LBPRewriter {
	
	public ProductFactor() {
	}
	
	@Override
	public String getName() {
		return R_prod_factor;
	}
	
	/**
	 * @see LBPRewriter#R_prod_factor
	 */
	@Override
	public Expression rewriteAfterBookkeeping(Expression expression, RewritingProcess process) {
		
		// Assert input arguments
		//  a tuple of the form: (prod_F in S m_V<-F, beingComputed).
		if (!Tuple.isTuple(expression) || Tuple.size(expression) != 2) {
			throw new IllegalArgumentException("Invalid input argument expression:"+expression);
		}
		
		Expression productOfFactorsToVariable = Tuple.get(expression, 0);
		Expression beingComputed              = Tuple.get(expression, 1);
		
		Expression result = null;

		Justification.begin(productOfFactorsToVariable);

		// Cases for input:
		if (IfThenElse.isIfThenElse(productOfFactorsToVariable)) {
			// Externalizes Conditionals
			// if Pi is 'if C then P1 else P2
			// return R_basic(if C
			// then R_prod_factor(P1)
			// else R_prod_factor(P2))
			result = rewriteExternalizeProduct(productOfFactorsToVariable, beingComputed, process);
		} 
		else {
			LPIUtil.assertProductOk(productOfFactorsToVariable);

			Expression prodIntensionalSet   = productOfFactorsToVariable.get(0);
			Expression msgToV_F             = IntensionalSet.getHead(prodIntensionalSet);
			Expression prodScopingCondition = IntensionalSet.getCondition(prodIntensionalSet);
			Expression indexExpression      = IntensionalSet.getIndexExpressions(prodIntensionalSet).get(0);
			Expression factorIndexF         = IntensionalSet.getIndex(indexExpression);
			Expression domainS              = IntensionalSet.getDomain(indexExpression);

			if (Sets.isExtensionalSet(domainS) && ExtensionalSet.isEmptySet(domainS)) {

				Trace.log("prod_F in {} m_V<-F");
				Trace.log("    return 1");

				Justification.beginStepWithJustification("no factors, so it is just a constant message");
				result = Expressions.ONE;
				Justification.endStepWithResult(result);
				
			} 
			else if (Sets.isExtensionalSet(domainS) && ExtensionalSet.isSingleton(domainS)) {

				Trace.log("prod_F in {F1} m_V<-F");
				Trace.log("    return R_m_to_v_from_f(m_V<-F1)");

				Justification.beginStepWithJustification("product of a singleton set is just its own single element");
				Expression msgToV_F1 = Expressions.make(LPIUtil.FUNCTOR_MSG_TO_FROM, msgToV_F.get(0), domainS.get(0));
				Justification.endStepWithResult(msgToV_F1);

				Justification.beginStepWithJustification("by solving message to variable from factor");
				result = process.rewrite(R_m_to_v_from_f, 
							LPIUtil.argForMessageToVariableFromFactorRewriteCall(msgToV_F1, beingComputed));
				Justification.endStepWithResult(result);

			} 
			else if (Sets.isIntensionalMultiSet(domainS)) {
				Trace.log("prod_F in {{ F1 | C1 }}_I m_V<-F");
				Trace.log("    C' <- R_formula_simplification(C1 and C)");
				
				Expression factor1            = IntensionalSet.getHead(domainS);
				Expression condition1         = IntensionalSet.getCondition(domainS);
				Expression scopingExpressionI = IntensionalSet.getScopingExpression(domainS);
				
				Trace.log("    message <- R_m_to_v_from_f(m_V<-F1, C', I, beingComputed) // under cont. constraint extended by C' and contextual variables extended by I");
				Expression       msgToV_F1        = Expressions.make(LPIUtil.FUNCTOR_MSG_TO_FROM, msgToV_F.get(0), factor1);
				RewritingProcess cPrimeSubProcess = GrinderUtil.extendContextualVariablesAndConstraint(scopingExpressionI, condition1, process);
				Expression       cPrime           = cPrimeSubProcess.getContextualConstraint();

				if (Justification.isEnabled()) {
					Justification.beginStepWithJustification("re-indexing set of messages");
					Expression newSetOfMessages  = IntensionalSet.makeMultiSet(scopingExpressionI, msgToV_F1, cPrime);
					Expression currentExpression = Expressions.apply(FunctorConstants.PRODUCT, newSetOfMessages);
					Justification.endStepWithResult(currentExpression);
				}

				Justification.beginStepWithJustification("solve message to variable from factor");
				Expression R_msgToV_F1 = cPrimeSubProcess.rewrite(R_m_to_v_from_f,
											LPIUtil.argForMessageToVariableFromFactorRewriteCall(msgToV_F1, cPrime, scopingExpressionI, beingComputed));

				Expression messageSet        = IntensionalSet.makeMultiSet(scopingExpressionI, R_msgToV_F1, cPrime);
				Expression productOfMessages = Expressions.apply(FunctorConstants.PRODUCT, messageSet);
				if (Justification.isEnabled()) {
					Justification.endStepWithResult(productOfMessages);
				}
				
				Trace.log("    return R_basic(prod_{{ (on I) message | C' }})");

				Justification.beginStepWithJustification("simplify intensionally defined product");
				result = process.rewrite(R_basic, productOfMessages);
				Justification.endStepWithResult(result);

				// Note: restriction to extensional multi-sets is required (see assertIsLegalUnionDomain() for details).
			} 
			else if (isUnion(domainS) || Sets.isExtensionalMultiSet(domainS)) { 
				// union of sets, it is either an application of the union
				// operator to a sequence of sets, or a single set.
				// Intensional, {}, and { 1Element} sets are
				// handled earlier in the if then else if... calls.
				Trace.log("prod_F in {{F1,...,Fn}} m_V<-F");
				Trace.log("    return R_prod_factor(prod_F in {F1} union {{F2,...,Fn}}  m_V<-F )");
				Trace.log("prod_F in Set union Union m_V<-F");
				Trace.log("    return R_prod_m_and_prod_factor(R_prod_factor(prod_F in Set m_V<-F) * prod_F in Union m_V<-F)");
				if ((isUnion(domainS) && domainS.numberOfArguments() > 1) ||
					(Sets.isExtensionalMultiSet(domainS) && ExtensionalSet.cardinality(domainS) > 1)) {
					Expression set = null;
					List<Expression> unionArgs = null;

					if (isUnion(domainS)) {
						assertIsLegalUnionDomain(domainS);
						set = domainS.get(0);
						unionArgs = Util.rest(domainS.getArguments());
					} 
					else {
						// Is an extensional set, construct the union arguments
						List<Expression> domainSElements = ExtensionalSet.getElements(domainS);
						set = ExtensionalSet.make(Sets.getLabel(domainS), Arrays.asList(domainSElements.get(0)));
						unionArgs = new ArrayList<Expression>();
						int size = domainSElements.size();
						for (int i = 1; i < size; i++) {
							Expression union =
								ExtensionalSet.make(
									Sets.getLabel(domainS),
									Arrays.asList(domainSElements.get(i)));
							unionArgs.add(union);
						}
					}
					Expression union = null;
					if (unionArgs.size() > 1) {
						union = Expressions.make(FunctorConstants.UNION, unionArgs.toArray());
					} 
					else {
						union = unionArgs.get(0);
					}

					// R_prod_factor(prod_F in Set m_V<-F)
					Expression productOfFactorsInSetToVariable = LPIUtil
							.makeProductOfMessages(factorIndexF, set, msgToV_F, prodScopingCondition);

					Justification.beginStepWithJustification("by solving product of messages from first factor set");
					Expression message = process.rewrite(R_prod_factor, 
											LPIUtil.argForProductFactorRewriteCall(productOfFactorsInSetToVariable, beingComputed));

					// prod_F in Union m_V<-F
					Expression productOfFactorsInUnionToVariable = LPIUtil
							.makeProductOfMessages(factorIndexF, union, msgToV_F, prodScopingCondition);
					
					if (Justification.isEnabled()) {
						Justification.endStepWithResult(
								Expressions.make(FunctorConstants.TIMES, message, productOfFactorsInUnionToVariable));
					}

					Justification.beginStepWithJustification("by multiplying this message to the product of remaining messages");
					result = process.rewrite(R_prod_m_and_prod_factor,
								LPIUtil.argForProductMessageAndProductFactorRewriteCall(message, productOfFactorsInUnionToVariable, beingComputed));
					Justification.endStepWithResult(result);

				} 
				else {
					throw new IllegalArgumentException("S is not a valid Union: S = " + domainS);
				}
			} 
			else if (IfThenElse.isIfThenElse(domainS)) {

				// Externalizes Conditionals
				result = rewriteExternalizeS(factorIndexF, domainS, msgToV_F, prodScopingCondition, beingComputed, process);

			} 
			else {
				throw new IllegalArgumentException("S is not in a legal form: S = " + domainS);
			}
		}

		Justification.end();
		
		return result;
	}

	//
	// PRIVATE METHODS
	//
	private static boolean isUnion(Expression expr) {
		if (Expressions.hasFunctor(expr, FunctorConstants.UNION)) {
			return true;
		}

		return false;
	}

	private Expression rewriteExternalizeProduct(Expression productOfFactorsToVariable, Expression beingComputed, RewritingProcess process) {
		Trace.log("if Pi is 'if condition then P1 else P2'");
		Trace.log("    return R_basic(if condition");
		Trace.log("                   then R_prod_factor(P1, beingComputed)");
		Trace.log("                   else R_prod_factor(P2, beingComputed))");

		Expression condition = IfThenElse
				.getCondition(productOfFactorsToVariable);
		Expression productThen = IfThenElse
				.getThenBranch(productOfFactorsToVariable);
		Expression productElse = IfThenElse
				.getElseBranch(productOfFactorsToVariable);

		if (Justification.isEnabled()) {
			Justification.beginStepWithJustification("by externalizing conditional");
			Justification.endStepWithResult(IfThenElse.make(condition, productThen, productElse));
		}

		Justification.beginStepWithJustification("by solving then and else branches");
		Expression result = GrinderUtil.branchAndMergeOnACondition(
				condition,
				newCallProductFactorRewrite(), new Expression[] { productThen, beingComputed},
				newCallProductFactorRewrite(), new Expression[] { productElse, beingComputed},
				R_check_branch_reachable, 
				R_basic, process);
		Justification.endStepWithResult(result);

		return result;
	}

	private Expression rewriteExternalizeS(Expression factorIndexF,
			Expression domainS, Expression msgToV_F,
			Expression prodScopingCondition, 
			Expression beingComputed, 
			RewritingProcess process) {
		Trace.log("if S in (prod_F in S m_V<-F) is 'if condition then S1 else S2'");
		Trace.log("    return R_basic(if condition");
		Trace.log("                   then R_prod_factor(prod_F in S1 m_V<-F, beingComputed)");
		Trace.log("                   else R_prod_factor(prod_F in S2 m_V<-F, beingComputed))");

		Expression condition = IfThenElse.getCondition(domainS);
		Expression s1 = IfThenElse.getThenBranch(domainS);
		Expression s2 = IfThenElse.getElseBranch(domainS);

		Expression productThen = LPIUtil.makeProductOfMessages(
				factorIndexF, s1, msgToV_F, prodScopingCondition);
		Expression productElse = LPIUtil.makeProductOfMessages(
				factorIndexF, s2, msgToV_F, prodScopingCondition);
		
		if (Justification.isEnabled()) {
			Justification.beginStepWithJustification("by externalizing conditional set " + domainS);
			Justification.endStepWithResult(IfThenElse.make(condition, productThen, productElse));
		}

		Justification.beginStepWithJustification("by solving then and else branches");

		Expression result = GrinderUtil.branchAndMergeOnACondition(
				condition,
				newCallProductFactorRewrite(), new Expression[] { productThen, beingComputed },
				newCallProductFactorRewrite(), new Expression[] { productElse, beingComputed },
				R_check_branch_reachable, 
				R_basic, process);

		Justification.endStepWithResult(result);

		return result;
	}

	@SuppressWarnings("unchecked")
	private static void assertIsLegalUnionDomain(Expression unionDomain) {
		
		Predicate<Expression> isLegalPredicate = Predicates.and(
					new Sets.IsNonEmptyUniSet(),
					Predicates.not(new Sets.IsSingletonExtensionalSet()),
					// nested union and conditional arguments are
					// allowed. As this algorithm is recursive
					// these will be checked on a nested call.
					Predicates.not(new IsApplicationOf(FunctorConstants.UNION)),
					Predicates.not(new IsApplicationOf(IfThenElse.FUNCTOR)));
				
		if (Util.thereExists(unionDomain.getArguments(), isLegalPredicate)) {
			// the reason for this check is that we are not
			// trying to determine the intersection of these
			// sets, which we would need to do if they are
			// non-empty unisets and non-singleton sets.
			throw new IllegalArgumentException(
					"If ProductFactor receives a union of sets of factors, it must be on multisets or singleton unisets, or empty sets only, but got "
							+ unionDomain);
		}
	}
	
	private RewriteOnBranch newCallProductFactorRewrite() {
		return new RewriteOnBranch() {
			@Override
			public Expression rewrite(Expression[] expressions, RewritingProcess process) {
				Expression result = process.rewrite(R_prod_factor,
										LPIUtil.argForProductFactorRewriteCall(expressions[0], expressions[1]));
				return result;
			}
		};
	}
}