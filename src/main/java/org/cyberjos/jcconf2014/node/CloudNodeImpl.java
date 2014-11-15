/*
 * @(#)CloudNodeImpl.java 2014/09/01
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.Member;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.Message;

/**
 * The implementation of {@link CloudNode}.
 *
 * @author Joseph S. Kuo
 * @since 0.0.1, 2014/09/01
 */
@Component
public class CloudNodeImpl implements CloudNode {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(CloudNodeImpl.class);

    /**
     * The node name.
     */
    private final String nodeName;

    /**
     * The Hazelcast facade.
     */
    @Autowired
    private HazelcastHelper hazelcastHelper;

    /**
     * Constructor.
     */
    public CloudNodeImpl() {
        this.nodeName = "Node-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook is invoked.");
            this.deactivate();
            try {
                Thread.sleep(1000);
                Thread.currentThread().join();
            } catch (final Exception ex) {
                logger.warn("Error!", ex);
            }
            Hazelcast.shutdownAll();
        }));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PostConstruct
    public void activate() {
        this.hazelcastHelper.registerNode(this);

        logger.info("Activated successfully: {}", this.nodeName);

        if (logger.isDebugEnabled()) {
            logger.debug("Online active node list: ");
            this.hazelcastHelper.getActiveNodes()
                    .stream()
                    .forEach(name -> logger.debug("==> Found: {}", name));
            this.hazelcastHelper.getActiveNodes()
                    .stream()
                    .filter(name -> !this.nodeName.equals(name))
                    .forEach(name -> this.send(name, "Hi, I am " + this.nodeName));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreDestroy
    public void deactivate() {
        this.hazelcastHelper.unregisterNode(this.nodeName);

        // Release resources here if necessary.

        logger.info("Deactivated successfully: {}", this.nodeName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(final String toNode, final String content) {
        final NodeMessage message = NodeMessage.newMessage(content)
                .from(this.nodeName)
                .to(toNode)
                .build();

        this.hazelcastHelper.send(message);
        logger.info("Sent message to {}: {}", toNode, content);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMessage(final Message<NodeMessage> message) {
        final NodeMessage nodeMessage = message.getMessageObject();

        logger.info("Incoming message sent from {}: \"{}\"", nodeMessage.getFrom(), nodeMessage.getContent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return this.nodeName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void memberAdded(final MembershipEvent membershipEvent) {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void memberRemoved(final MembershipEvent membershipEvent) {
        final Member removedMember = membershipEvent.getMember();

        logger.info("Found there is one node removed from this cluster: {}", removedMember.getUuid());

        final NodeRecord masterNode = this.hazelcastHelper.getMasterNodeRecord();
        if ((masterNode == null) || masterNode.getMemberId().equals(removedMember.getUuid())) {
            logger.info("The removed node is a master node. Trying to vote a new one");
            this.hazelcastHelper.setMaster(this);
        } else {
            logger.info("The master node: {}", masterNode);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void memberAttributeChanged(final MemberAttributeEvent memberAttributeEvent) {
        // Do nothing.
    }
}
