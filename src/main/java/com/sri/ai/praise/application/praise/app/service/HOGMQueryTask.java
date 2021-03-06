/*
 * Copyright (c) 2015, SRI International
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
package com.sri.ai.praise.application.praise.app.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.annotations.Beta;
import com.sri.ai.praise.application.praise.app.PRAiSEController;
import com.sri.ai.praise.inference.HOGMQueryResult;
import com.sri.ai.praise.inference.HOGMQueryRunner;
import com.sri.ai.expresso.api.Expression;

import javafx.concurrent.Task;

@Beta
public class HOGMQueryTask extends Task<HOGMQueryResult> {
	private String query;
	private String model;
	//
	private HOGMQueryRunner hogmQueryRunner = null;
	
	public HOGMQueryTask(String query, String model) {
		this.query = query;
		this.model = model;
	}
	
	@Override
	public HOGMQueryResult call() {
		final AtomicReference<HOGMQueryResult> result = new AtomicReference<>();
		
		PRAiSEController.computeExpressionWithDesiredPrecision(() -> {
			hogmQueryRunner = new HOGMQueryRunner(model, query);
			List<HOGMQueryResult> queryResults = hogmQueryRunner.getResults();
			if (queryResults.size() == 1) {
				HOGMQueryResult queryResult = queryResults.get(0);
				if (queryResult.hasErrors()) {
					result.set(queryResult);
				}
				else {
					Expression answer = hogmQueryRunner.simplifyAnswer(queryResult.getResult(), queryResult.getQueryExpression());					
					result.set(new HOGMQueryResult(queryResult.getQueryString(), queryResult.getQueryExpression(), queryResult.getParsedModel(), answer, queryResult.getMillisecondsToCompute()));
				}
			}		
		});		
 
        return result.get();
    }
	
	@Override
	protected void cancelled() {
		if (hogmQueryRunner != null) {
			hogmQueryRunner.cancelQuery();
		}
	}
}
