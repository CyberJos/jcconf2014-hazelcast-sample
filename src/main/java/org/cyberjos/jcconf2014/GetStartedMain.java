/*
 * @(#)GetStartedMain.java 2014/10/14
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

import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Get started program of Hazelcast.
 *
 * @author Joseph S. Kuo
 * @since 0.0., 2014/10/14
 */
public class GetStartedMain {
    /**
     * The key for customers.
     */
    private static final String KEY_CUSTOMERS = "customers";

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(GetStartedMain.class);

    /**
     * Main method.
     *
     * @param args arguments
     */
    public static void main(final String[] args) {
        final Config cfg = new Config();
        final HazelcastInstance instance = Hazelcast.newHazelcastInstance(cfg);
        final Map<Long, String> mapCustomers = instance.getMap(KEY_CUSTOMERS);

        if (MapUtils.isNotEmpty(mapCustomers)) {
            logger.info("Found items in the map {} ", KEY_CUSTOMERS);
            mapCustomers.entrySet()
            .stream()
            .forEachOrdered(entry -> logger.info("  -- key: {}, value: {}", entry.getKey(), entry.getValue()));
        } else {
            logger.info("No data found in the map {}.", KEY_CUSTOMERS);
        }

        final long creationTime = System.currentTimeMillis();
        mapCustomers.put(creationTime, Integer.toHexString(cfg.hashCode()));
        logger.info("Added a new item. key: {}, value: {}", creationTime, mapCustomers.get(creationTime));
        logger.info("Map Size:" + mapCustomers.size());
    }
}
