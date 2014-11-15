/*
 * @(#)NodeRecord.java 2014/10/14
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
package org.cyberjos.jcconf2014.node;

import java.io.Serializable;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This class is used for recording node-hazelcastInstance pair info.
 *
 * @author Joseph S. Kuo
 * @since 0.0., 2014/10/14
 */
class NodeRecord implements Serializable {
    /**
     * The serial version UID.
     */
    private static final long serialVersionUID = -633496296272828030L;

    /**
     * The cloud node name.
     */
    private final String nodeName;

    /**
     * The Hazelcast member ID.
     */
    private final String memberId;

    /**
     * Constructor.
     *
     * @param theNodeName the cloud node name
     * @param theMemberId the Hazelcast member ID
     */
    public NodeRecord(final String theNodeName, final String theMemberId) {
        this.nodeName = theNodeName;
        this.memberId = theMemberId;
    }

    /**
     * Returns the cloud node name.
     *
     * @return the cloud node name
     */
    public String getNodeName() {
        return this.nodeName;
    }

    /**
     * Returns the Hazelcast member ID.
     *
     * @return the Hazelcast member ID
     */
    public String getMemberId() {
        return this.memberId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this).append("Node name", this.nodeName).append("Member ID", this.memberId).build();
    }
}
