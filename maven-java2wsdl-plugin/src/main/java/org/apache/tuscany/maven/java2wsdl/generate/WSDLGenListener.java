/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */
package org.apache.tuscany.maven.java2wsdl.generate;

public interface WSDLGenListener {
	public static int UNKNOWN = 0;

	public static int INPUT_ARGS_PARSING = 1;

	public static int INPUT_ARGS_VALIDATION = 2;

	public static int WSDL_MODEL_CREATION = 3;

	public static int WSDL_MODEL_WRITING = 4;

	public static String[] phaseAsString = { "Unknown",
			"Input Arguments Parsing", "Input Arguments Validation",
			"WSDL Model Creation", "WSDL Model Writing" };

	public void WSDLGenPhaseStarted(WSDLGenEvent event);

	public void WSDLGenPhaseCompleted(WSDLGenEvent event);
}
