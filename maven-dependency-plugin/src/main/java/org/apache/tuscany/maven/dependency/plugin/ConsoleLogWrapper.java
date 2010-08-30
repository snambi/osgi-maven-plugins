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

package org.apache.tuscany.maven.dependency.plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsoleLogWrapper implements LogWrapper {
    private final static Logger log = Logger.getLogger(ConsoleLogWrapper.class.getName());

    public ConsoleLogWrapper() {
        super();
    }

    public boolean isDebugEnabled() {
        return log.isLoggable(Level.FINE);
    }

    public boolean isInfoEnabled() {
        return log.isLoggable(Level.INFO);
    }

    public boolean isWarnEnabled() {
        return log.isLoggable(Level.WARNING);
    }

    public boolean isErrorEnabled() {
        return log.isLoggable(Level.SEVERE);
    }

    public void debug(String msg) {
        if (isDebugEnabled()) {
            System.out.println(msg);
        }
    }

    public void info(String msg) {
        if (isInfoEnabled()) {
            System.out.println(msg);
        }
    }

    public void warn(String msg) {
        if (isWarnEnabled()) {
            System.err.println(msg);
        }
    }

    public void error(String msg) {
        if (isErrorEnabled()) {
            System.err.println(msg);
        }
    }

}
