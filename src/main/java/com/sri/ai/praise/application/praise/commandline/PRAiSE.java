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
package com.sri.ai.praise.application.praise.commandline;

import static com.sri.ai.praise.model.common.io.PagedModelContainer.getModelPagesFromURI;
import static com.sri.ai.util.Util.getFirstNonNullResultOrNull;
import static com.sri.ai.util.Util.getFirstSatisfyingPredicateOrNull;
import static com.sri.ai.util.Util.join;
import static com.sri.ai.util.Util.mapIntoList;
import static com.sri.ai.util.Util.thereExists;
import static com.sri.ai.util.Util.toHoursMinutesAndSecondsString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.sri.ai.grinder.sgdpllt.api.Theory;
import com.sri.ai.praise.inference.HOGMQueryResult;
import com.sri.ai.praise.inference.HOGMQueryRunner;
import com.sri.ai.praise.lang.ModelLanguage;
import com.sri.ai.praise.model.common.io.ModelPage;
import com.sri.ai.praise.model.common.io.PagedModelContainer;

/**
 * Command line interface for running the SGSolver.
 * 
 * @author oreilly
 *
 */
@Beta
public class PRAiSE {
	private static final String LOWER_CASE_PRAISE_FILE_DEFAULT_EXTENSION = PagedModelContainer.DEFAULT_CONTAINER_FILE_EXTENSION.toLowerCase();
	public static final Charset FILE_CHARSET  = Charsets.UTF_8;
	public static final String  RESULT_PREFIX = "RESULT     = ";
	public static final ModelLanguage DEFAULT_LANGUAGE = ModelLanguage.HOGMv1;
	
	static class PRAiSECommandLineArguments implements AutoCloseable {
		List<File>    inputFiles      = new ArrayList<>(); // non option arguments (at least 1 required)
		ModelLanguage inputLanguage   = null;              // --language (optional - 0 or 1)
		List<String>  globalQueries   = new ArrayList<>(); // --query    (optional - 0 or more)
		PrintStream   out             = System.out;        // --output   (optional - 0 or 1)
		
		@Override
		public void close() throws IOException {
			out.flush();
			// Only close if not System.out
			if (out != System.out) {				
				out.close();
			}
		}
	}
	
	public static void main(String[] args) {
		run(args, null);
	}
	
	public static void run(String[] args, Supplier<Theory> theorySupplier) {
		try (PRAiSECommandLineArguments solverArgs = getArgs(args)) {
			List<ModelPage> hogModelsToQuery = getHOGModelsToQuery(solverArgs);

			for (ModelPage hogModelToQuery : hogModelsToQuery) {
				solverArgs.out.print("MODEL NAME = ");
				solverArgs.out.println(hogModelToQuery.getName());
				solverArgs.out.println("MODEL      = ");
				solverArgs.out.println(hogModelToQuery.getModelString());
				HOGMQueryRunner queryRunner = new  HOGMQueryRunner(hogModelToQuery.getModelString(), hogModelToQuery.getDefaultQueriesToRun());
				if (theorySupplier != null) {
					queryRunner.setOptionTheory(theorySupplier.get());
				}
				List<HOGMQueryResult> hogModelQueryResults = queryRunner.query();
				hogModelQueryResults.forEach(hogModelQueryResult -> {
					solverArgs.out.print("QUERY      = ");
					solverArgs.out.println(hogModelQueryResult.getQueryString());
					solverArgs.out.print(RESULT_PREFIX);
					solverArgs.out.println(queryRunner.simplifyAnswer(hogModelQueryResult.getResult(), hogModelQueryResult.getQueryExpression()));
					solverArgs.out.print("TOOK       = ");
					solverArgs.out.println(toHoursMinutesAndSecondsString(hogModelQueryResult.getMillisecondsToCompute()) + "\n");
					if (hogModelQueryResult.isErrors()) {
						hogModelQueryResult.getErrors().forEach(error -> {
							solverArgs.out.println("ERROR ="+error.getErrorMessage());
							if (error.getThrowable() != null) {
								solverArgs.out.println("THROWABLE =");
								error.getThrowable().printStackTrace(solverArgs.out);
							}
						});
					}
				});
			}
		}
		catch (Exception ex) {
			System.err.println("Error calling SGSolver");
			ex.printStackTrace();
		}
	}
	
	public static PRAiSECommandLineArguments getArgs(String[] args) throws UnsupportedEncodingException, FileNotFoundException, IOException {
		PRAiSECommandLineArguments result = new PRAiSECommandLineArguments();
		
		OptionParser parser = new OptionParser();		
		// Optional
		OptionSpec<String> language   = parser.accepts("language", "input model language (code), allowed values are "+getLegalModelLanguageCodesDescription()).withRequiredArg().ofType(String.class);
		OptionSpec<String> query      = parser.accepts("query", "query to run over all input models").withRequiredArg().ofType(String.class);
		OptionSpec<File>   outputFile = parser.accepts("output",   "output file name (defaults to stdout).").withRequiredArg().ofType(File.class);
		// Help
		OptionSpec<Void> help = parser.accepts("help", "command line options help").forHelp();
		//
		String usage = 
				"java "+PRAiSE.class.getName() + " [--help] [--language language_code] [--query global_query_string] [--output output_file_name] inputModelFile ..."
				+ "\n\n"
				+ "This command reads a set of models from input files and executes a set of queries on each of them.\n\n"
				+ "The models are obtained in the following manner:\n"
				+ "- input files may be one or more; they can be .praise files (saved from the PRAiSE editor and solver) or plain text files.\n"
				+ "- each .praise input file contains possibly multiple pages, each with a model and a set of queries for it (see PRAiSE editor and solver).\n"
				+ "- multiple plain text input files are combined into a single model (not combined with .praise models).\n\n"
				+ "The queries are obtained in the following manner:\n"
				+ "- each page in each .praise file contains a list of queries for its specific model\n"
				+ "- queries specified with --query option will apply to all models from all .praise files and to the model from combined plain text input files.\n\n"
				+ "Evidence can be encoded as deterministic statements (see examples in PRAiSE editor and solver).\n"
						;
		
		List<String> errors   = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		try {
			OptionSet options = parser.parse(args);
			
			if (options.has(help)) {
				System.out.println(usage);
				parser.printHelpOn(System.out);
				System.exit(0);
			}
			for (Object inputFileName : options.nonOptionArguments()) {
				File inputFile = new File(inputFileName.toString());
				if (inputFile.exists()) {
					result.inputFiles.add(inputFile);
				}
				else {
					errors.add("Input file ["+inputFileName+"] is not a file or does not exist.");
				}
			}
			if (result.inputFiles.size() == 0) {
				errors.add("No input files specified");
			}
			
			if (options.has(query)) {
				for (String commandLineQuery : options.valuesOf(query)) {
					result.globalQueries.add(commandLineQuery);
				}
			}
			
			if (options.has(language)) {
				String languageCode = options.valueOf(language);
				result.inputLanguage = findLanguageModel(languageCode);
				if (result.inputLanguage == null) {
					errors.add("Input language code, "+languageCode+", is not legal. Legal values are "+getLegalModelLanguageCodesDescription()+".");
				}
			}
			else {
				// Guess the language from the input file names
				result.inputLanguage = guessLanguageModel(result.inputFiles);
				if (result.inputLanguage == null) {
					errors.add("Unable to guess the input language from the given input file name extensions. Legal input language codes are "+getLegalModelLanguageCodesDescription()+".");
				}
			}
			
			if (options.has(outputFile)) {
				File outFile = options.valueOf(outputFile);
				if (outFile.exists()) {
					if (outFile.isDirectory()) {
						errors.add("Output file specified ["+outFile.getAbsolutePath()+"] is a directory, must be a legal file name.");
					}
					else {
						warnings.add("Warning output file ["+outFile.getAbsolutePath()+"] already exists, will be overwritten.");
					}
				}
				// Only setup file output if there are no errors.
				if (errors.size() == 0) {
					result.out = new PrintStream(outFile, FILE_CHARSET.name());
				}
			}
		}
		catch (OptionException oe) {
			errors.add(oe.getMessage());
		}
		
		if (warnings.size() > 0) {
			warnings.forEach(warning -> System.err.println("WARNING: "+ warning));
		}
		
		if (errors.size() > 0) {
			errors.forEach(error -> System.err.println("ERROR: "+ error));
			System.err.println(usage);
			parser.printHelpOn(System.err);
			System.exit(1);
		}
		
		return result;
	}
	
	//
	// PRIVATE
	//	
	private static List<ModelPage> getHOGModelsToQuery(PRAiSECommandLineArguments solverArgs) throws IOException {
		List<ModelPage> result = new ArrayList<>();
		
		
		// First handle container files and track non-container files
		List<File> nonContainerFiles = new ArrayList<>();
		for (File inputFile : solverArgs.inputFiles) {
			if (inputFile.getName().endsWith(PagedModelContainer.DEFAULT_CONTAINER_FILE_EXTENSION)) {
				if (solverArgs.globalQueries.size() == 0) {
					// Take the models as is
					result.addAll(PagedModelContainer.getModelPagesFromURI(inputFile.toURI()));
				}
				else {
					// Global queries have been specified on the command line, need to add to 
					// each model page
					for (ModelPage containerModelPage : PagedModelContainer.getModelPagesFromURI(inputFile.toURI())) {
						List<String> combinedQueries = new ArrayList<>(solverArgs.globalQueries);
						combinedQueries.addAll(containerModelPage.getDefaultQueriesToRun());
						result.add(new ModelPage(containerModelPage.getLanguage(), containerModelPage.getName(), containerModelPage.getModelString(), combinedQueries));
					}
				}
			}
			else {
				nonContainerFiles.add(inputFile);
			}
		}
		
		// Currently, non-container files are treated as model and evidence files and are just concatenated together
		// to construct a single model file
		if (nonContainerFiles.size() > 0) {
			String textModel = nonContainerFiles.stream()
				.map(file -> {
					String fileContents = "";
					try {
						fileContents = Files.readAllLines(file.toPath()).stream().collect(Collectors.joining("\n"));
					}
					catch (IOException ioe) {
						throw new RuntimeException(ioe);
					}
					return fileContents;
				})
				.collect(Collectors.joining("\n"));
			result.add(new ModelPage(solverArgs.inputLanguage, "Model from concatenation of non-container input files", textModel, solverArgs.globalQueries));
		}
		
// TODO - ensure all results are translated to HOGMv1 if needed.
		
// TODO - if no queries specified, specify them	by taking all the constants and random variables in the model	
		
		return result;
	}
	
	private static String getLegalModelLanguageCodesDescription() {
		String result = join(mapIntoList(ModelLanguage.values(), ModelLanguage::getCode));
		return result;
	}
	
	private static ModelLanguage findLanguageModel(String languageCode) {
		List<ModelLanguage> modelLanguages = Arrays.asList(ModelLanguage.values());
		ModelLanguage result = getFirstSatisfyingPredicateOrNull(modelLanguages, ml -> isLanguageCodeForModelLanguage(languageCode, ml));
		return result;
	}

	private static boolean isLanguageCodeForModelLanguage(String languageCode, ModelLanguage modeLanguage) {
		boolean result = modeLanguage.getCode().toLowerCase().equals(languageCode.toLowerCase());
		return result;
	}
	
	private static ModelLanguage guessLanguageModel(List<File> inputFiles) {
		
		ModelLanguage result = getLanguageMatchingExtensionOfSomeFile(inputFiles);
		
		if (result == null) {
			result = getLanguageFromInputFileWithDefaultPRAiSEFileExtension(inputFiles);
		}
		
		if (result == null) {
			result = DEFAULT_LANGUAGE;
		}
		
		return result;
	}

	private static ModelLanguage getLanguageMatchingExtensionOfSomeFile(List<File> inputFiles) {
		List<ModelLanguage> modelLanguages = Arrays.asList(ModelLanguage.values());
		ModelLanguage result = getFirstSatisfyingPredicateOrNull(modelLanguages, isExtensionForSomeOfTheFiles(inputFiles));
		return result;
	}

	private static Predicate<ModelLanguage> isExtensionForSomeOfTheFiles(List<File> inputFiles) {
		return ml -> thereExists(inputFiles, hasExtension(ml));
	}

	private static Predicate<File> hasExtension(ModelLanguage ml) {
		return inputFile -> lowerCaseName(inputFile).endsWith(lowerCaseFileExtension(ml));
	}

	private static String lowerCaseName(File inputFile) {
		String result = inputFile.getName().toLowerCase();
		return result;
	}

	private static String lowerCaseFileExtension(ModelLanguage ml) {
		String result = ml.getDefaultFileExtension().toLowerCase();
		return result;
	}

	private static ModelLanguage getLanguageFromInputFileWithDefaultPRAiSEFileExtension(List<File> inputFiles) {
		ModelLanguage result;
		result = inputFiles.stream()
					.filter(fileHasPRAiSEFileDefaultExtension)
					.map(fromPRAiSEInputFileToLanguage)
					.findFirst().orElse(null);
		return result;
	}

	private static java.util.function.Predicate<? super File> fileHasPRAiSEFileDefaultExtension = 
			inputFile -> lowerCaseName(inputFile).endsWith(LOWER_CASE_PRAISE_FILE_DEFAULT_EXTENSION);
			
	private static Function<? super File, ? extends ModelLanguage> fromPRAiSEInputFileToLanguage = 
			praiseInputFile -> getLanguageFromFirstModel(praiseInputFile);

	private static ModelLanguage getLanguageFromFirstModel(File praiseInputFile) {
		List<ModelPage> models = getModelPagesFromURI(praiseInputFile.toURI());
		ModelLanguage praiseInputFileLanguage = getFirstNonNullResultOrNull(models, m -> m.getLanguage());
		return praiseInputFileLanguage;
	}
}
