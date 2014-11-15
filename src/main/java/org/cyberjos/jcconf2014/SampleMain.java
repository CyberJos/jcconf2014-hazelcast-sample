/*
 * @(#)SampleMain.java 2014/09/01
 *
 * Copyright (c) 2014 Joseph S. Kuo
 * All Rights Reserved.
 *
 * --LICENSE NOTICE--
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * --LICENSE NOTICE--
 */
package org.cyberjos.jcconf2014;

import org.cyberjos.jcconf2014.node.CloudNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Node program entry point.
 *
 * @author Joseph S. Kuo
 * @since 0.0.1, 2014/09/01
 */
public class SampleMain {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(SampleMain.class);

    /**
     * Application context.
     */
    private static ConfigurableApplicationContext context;

    /**
     * Main method.
     *
     * @param args arguments
     */
    public static void main(final String[] args) {
        logger.info("Starting to launch application...");

        context = new AnnotationConfigApplicationContext(Application.class);
        final CloudNode cloudNode = context.getBean(CloudNode.class);

        context.registerShutdownHook();
        logger.info("The node has been started: {}", cloudNode.getName());
    }

    /**
     * Returns the application context.
     * 
     * @return the application context
     */
    public static ConfigurableApplicationContext getApplicationContext() {
        return context;
    }
}
