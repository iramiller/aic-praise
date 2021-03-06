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
package com.sri.ai.praise.application.praise.app.model;

import java.io.File;
import java.util.List;

import com.google.common.annotations.Beta;
import com.sri.ai.praise.application.praise.app.FXUtil;
import com.sri.ai.praise.model.common.io.ModelPage;
import com.sri.ai.praise.model.common.io.PagedModelContainer;

/**
 * Simple utility for getting example model pages for the UI, with default handling of exceptions in place.
 * 
 * @author oreilly
 *
 */
@Beta
public class ExamplePages extends PagedModelContainer {

	public ExamplePages(String name, List<ModelPage> pages) {
		super(name, pages);
	}

	public static List<ModelPage> getExamplePagesFromResource(String resourceName) {
		List<ModelPage> result = null;
		try {
			result = getModelPagesFromURI(ExamplePages.class.getResource(resourceName).toURI());
		}
		catch (Throwable t) {
			FXUtil.exception(t);
		}
		return result;
	}
	
	public static List<ModelPage> getExamplePagesFromFile(File file) {
		List<ModelPage> result = null;
		try {
			result = getModelPagesFromURI(file.toURI());
		}
		catch (Throwable t) {
			FXUtil.exception(t);
		}
		return result;
	}
}
