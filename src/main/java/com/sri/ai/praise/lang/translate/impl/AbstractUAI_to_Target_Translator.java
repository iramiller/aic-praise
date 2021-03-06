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
package com.sri.ai.praise.lang.translate.impl;

import java.io.PrintWriter;
import java.io.Reader;

import com.google.common.annotations.Beta;
import com.sri.ai.praise.lang.ModelLanguage;
import com.sri.ai.praise.model.v1.imports.uai.UAIEvidenceReader;
import com.sri.ai.praise.model.v1.imports.uai.UAIModel;
import com.sri.ai.praise.model.v1.imports.uai.UAIModelReader;

/**
 * Abstract base class for UAI->[some target] translations.
 * 
 * @author oreilly
 *
 */
@Beta
public abstract class AbstractUAI_to_Target_Translator extends AbstractTranslator {
	public static final String[] INPUT_FILE_EXTENSIONS = new String[] {
			ModelLanguage.UAI.getDefaultFileExtension(),
			ModelLanguage.UAI.getDefaultFileExtension()+".evid"}; // The associated evidence file (must exist as expected by UAI propositional solvers)
	
	//
	// START-Translator
	@Override
	public ModelLanguage getSource() {
		return ModelLanguage.UAI;
	}
	
	@Override
	public int getNumberOfInputs() {
		return INPUT_FILE_EXTENSIONS.length;
	}
	
	@Override
	public String[] getInputFileExtensions() {
		return INPUT_FILE_EXTENSIONS;
	}		
	// END-Translator
	//
	
	@Override
	protected void translate(String inputIdentifier, Reader[] inputModelReaders, PrintWriter[] translatedOutputs) throws Exception {	
		Reader uaiModelReader    = inputModelReaders[0];
		Reader uaiEvidenceReader = inputModelReaders[1];
		
		//
		// Instantiate the source UAI model
		UAIModel uaiModel = UAIModelReader.read(uaiModelReader);
		
		//
		// Read the corresponding evidence and merge into the model
		// This is required as the UAI solvers all take the evidence
		// when they are searching for solutions, so other solvers
		// need to have this information contained in their models 
		// as well.
		UAIEvidenceReader.read(uaiEvidenceReader, uaiModel);
		uaiModel.mergeEvidenceIntoModel();
		
		translate(inputIdentifier, uaiModel, translatedOutputs);
	}
	
	protected abstract void translate(String inputIdentifier, UAIModel uaiModel, PrintWriter[] translatedOutputs) throws Exception;
}
