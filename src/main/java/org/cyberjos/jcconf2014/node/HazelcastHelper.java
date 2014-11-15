/*
 * @(#)HazelcastHelper.java 2014/09/01
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

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IAtomicReference;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ITopic;

/**
 * Hazelcast helper.
 *
 * @author Joseph S. Kuo
 * @since 0.0., 2014/09/01
 */
@Component
public class HazelcastHelper {
    /**
     * The key to access all active nodes.
     */
    private static final String ACTIVE_NODES = "ACTIVE_NODES";

    /**
     * The key to access master node.
     */
    private static final String MASTER_NODE = "MASTER_NODE";

    /**
     * The key to access task queue.
     */
    private static final String TASK_QUEUE = "TASK_QUEUE";

    /**
     * The key to access task serial number.
     */
    private static final String TASK_NUMBER = "TASK_NUMBER";

    /**
     * The logger.
     */
    static final Logger logger = LoggerFactory.getLogger(HazelcastHelper.class);

    /**
     * The working mode.
     */
    private static final boolean WORKING_MODE = true;

    /**
     * The {@code HazelcastInstance} holder is a singleton enumeration for the
     * usage of Hazelcast node.
     *
     * @author Joseph S. Kuo
     * @since 0.0., 2014/09/28
     */
    private enum Holder {
        /**
         * The enumeration for singleton instance.
         */
        INSTANCE;

        /**
         * The singleton instance
         */
        private final HazelcastInstance hazelcastInstance;

        /**
         * Default constructor.
         */
        private Holder() {
            this.hazelcastInstance = Hazelcast.newHazelcastInstance(new ClasspathXmlConfig("hazelcast.xml"));
            logger.info("Hazelcast instance has been launched, member ID: {}", this.getMemberId());
        }

        /**
         * Returns the singleton instance of {@code HazelcastInstance}.
         *
         * @return the singleton instance of {@code HazelcastInstance}
         */
        public HazelcastInstance getInstance() {
            return this.hazelcastInstance;
        }

        /**
         * Returns the member ID.
         *
         * @return the member ID
         */
        public String getMemberId() {
            return this.hazelcastInstance.getCluster().getLocalMember().getUuid();
        }
    }

    /**
     * Registers the given node to the active node set.
     *
     * @param cloudNode the node to be registered
     * @throws NullPointerException if the given node is {@code null}
     */
    public void registerNode(final CloudNode cloudNode) {
        Objects.requireNonNull(cloudNode, "The given cloud node must not be null.");

        final String nodeName = cloudNode.getName();
        final NodeRecord record = new NodeRecord(nodeName, Holder.INSTANCE.getMemberId());
        HazelcastHelper.getMap(ACTIVE_NODES).put(nodeName, record);
        HazelcastHelper.<NodeMessage>getTopic(nodeName).addMessageListener(cloudNode);
        Holder.INSTANCE.getInstance().getCluster().addMembershipListener(cloudNode);
        logger.info("The given node registered: {}", record);

        final Optional<NodeRecord> optional = this.getMasterNodeRecord();
        if (!optional.isPresent()) {
            this.setMaster(cloudNode);
            return;
        }

        logger.info("Found the master node: {}", optional.get());

        if (WORKING_MODE) {
            final Thread thread = new Thread(this.createConsumer(cloudNode));
            thread.start();
        }
    }

    /**
     * Removes the node with the given name from the active node set.
     *
     * @param nodeName the node to be removed
     * @throws NullPointerException if the given node name is {@code null}
     */
    public void unregisterNode(final String nodeName) {
        Objects.requireNonNull(nodeName, "The given node name must not be null.");

        final NodeRecord record = HazelcastHelper.<String, NodeRecord>getMap(ACTIVE_NODES).remove(nodeName);
        HazelcastHelper.getTopic(nodeName).destroy();
//        Holder.INSTANCE.getInstance().getCluster().removeMembershipListener(nodeName);
        logger.info("The given node is un-registered: {}", record);
    }

    /**
     * Sets the given node to be the master node.
     *
     * @param cloudNode the node to become master
     * @return {@code true} if the given node becomes the master node
     *         successfully
     * @throws NullPointerException if the given node is {@code null}
     */
    public synchronized boolean setMaster(final CloudNode cloudNode) {
        Objects.requireNonNull(cloudNode, "The given cloud node must not be null.");

        final Lock lock = Holder.INSTANCE.getInstance().getLock("my-distributed-lock");
        lock.lock();

        try {
            final NodeRecord masterRecord = HazelcastHelper.<NodeRecord>getAtomicReference(MASTER_NODE).get();

            if (masterRecord != null) {
                final long count = Holder.INSTANCE.getInstance().getCluster().getMembers()
                        .stream()
                        .filter(member -> StringUtils.equals(masterRecord.getMemberId(), member.getUuid()))
                        .count();

                if (count != 0) {
                    logger.warn("The master node has already existed: {}", masterRecord);
                    return false;
                }

                this.unregisterNode(masterRecord.getNodeName());
            }

            final NodeRecord newMasterRecord = HazelcastHelper.<String, NodeRecord>getMap(ACTIVE_NODES).get(cloudNode.getName());
            HazelcastHelper.getAtomicReference(MASTER_NODE).set(newMasterRecord);
            logger.info("This node has already become the new master node: {}", newMasterRecord);

            if (WORKING_MODE) {
                final Thread thread = new Thread(this.createProducer(cloudNode));
                thread.start();
            }
        } finally {
            lock.unlock();
        }

        return true;
    }

    /**
     * Returns {@code true} if the given cloud node is the master node.
     *
     * @param cloudNode the cloud node
     * @return {@code true} if the given cloud node is the master node
     * @throws NullPointerException if the given node is {@code null}
     */
    public boolean isMaster(final CloudNode cloudNode) {
        Objects.requireNonNull(cloudNode, "The given node must not be null.");
        final Optional<NodeRecord> optional = this.getMasterNodeRecord();
        return optional.isPresent() && StringUtils.equals(cloudNode.getName(), optional.get().getNodeName());
    }

    /**
     * Returns the name of the master node.
     *
     * @return the name of the master node
     */
    public Optional<NodeRecord> getMasterNodeRecord() {
        return Optional.ofNullable(HazelcastHelper.<NodeRecord>getAtomicReference(MASTER_NODE).get());
    }

    /**
     * Sends the given message to the specified node.
     *
     * @param nodeMessage the node message
     * @throws NullPointerException if the given node is {@code null}
     */
    public void send(final NodeMessage nodeMessage) {
        Objects.requireNonNull(nodeMessage, "The given cloud message must not be null.");

        HazelcastHelper.getTopic(nodeMessage.getTo()).publish(nodeMessage);
    }

    /**
     * Returns the active node set.
     *
     * @return the active node set
     */
    public Set<String> getActiveNodes() {
        return HazelcastHelper.<String, NodeRecord>getMap(ACTIVE_NODES).keySet();
    }

    /**
     * Returns the task queue.
     *
     * @return the task queue
     */
    public BlockingQueue<String> getTaskQueue() {
        return HazelcastHelper.getQueue(TASK_QUEUE);
    }

    /**
     * Returns the distributed map related with the given name.
     *
     * @param mapName the distributed map
     * @return the name of map
     * @throws NullPointerException if the given name is {@code null}
     */
    private static <K, V> IMap<K, V> getMap(final String mapName) {
        Objects.requireNonNull(mapName, "The given map name must not be null.");
        return Holder.INSTANCE.getInstance().getMap(mapName);
    }

    /**
     * Returns the distributed blocking queue related with the given name.
     *
     * @param queueName the distributed blocking queue
     * @return the name of queue
     * @throws NullPointerException if the given name is {@code null}
     */
    private static <T> IQueue<T> getQueue(final String queueName) {
        Objects.requireNonNull(queueName, "The given queue name must not be null.");
        return Holder.INSTANCE.getInstance().getQueue(queueName);
    }

    /**
     * Returns the topic related with the given name.
     *
     * @param topicName the topic
     * @return the name of topic
     * @throws NullPointerException if the given name is {@code null}
     */
    private static <T> ITopic<T> getTopic(final String topicName) {
        Objects.requireNonNull(topicName, "The given topic name must not be null.");
        return Holder.INSTANCE.getInstance().getTopic(topicName);
    }

    /**
     * Returns the atomic reference related with the given name.
     *
     * @param referenceName the atomic reference
     * @return the name of atomic reference
     * @throws NullPointerException if the given name is {@code null}
     */
    private static <T> IAtomicReference<T> getAtomicReference(final String referenceName) {
        Objects.requireNonNull(referenceName, "The given atomic reference name must not be null.");
        return Holder.INSTANCE.getInstance().getAtomicReference(referenceName);
    }

    /**
     * Returns the atomic long integer related with the given name.
     *
     * @param numberName the atomic long integer
     * @return the name of atomic long integer
     * @throws NullPointerException if the given name is {@code null}
     */
    private static IAtomicLong getAtomicLong(final String numberName) {
        Objects.requireNonNull(numberName, "The given atomic integer name must not be null.");
        return Holder.INSTANCE.getInstance().getAtomicLong(numberName);
    }

    /**
     * Creates and returns a new consumer.
     *
     * @param cloudNode the cloud node which runs this new consumer
     * @return a new consumer
     */
    private Runnable createConsumer(final CloudNode cloudNode) {
        return () -> {
            logger.info("Consumer thread started.");
            while (!this.isMaster(cloudNode)) {
                try {
                    final String task = HazelcastHelper.this.getTaskQueue().take();
                    logger.info("Retrieved task: {}", task);
                    Thread.sleep(RandomUtils.nextInt(2500, 7000));
                    logger.info("Finished task: {}", task);
                } catch (final Exception ex) {
                    logger.error("Exception occurred!", ex);
                }
            }
        };
    }

    /**
     * Creates and returns a new producer.
     *
     * @param cloudNode the cloud node which runs this new producer
     * @return a new producer
     */
    private Runnable createProducer(final CloudNode cloudNode) {
        return () -> {
            logger.info("Producer thread started.");
            while (this.isMaster(cloudNode)) {
                try {
                    final Set<String> nodes = new HashSet<>(HazelcastHelper.this.getActiveNodes());
                    nodes.remove(cloudNode.getName());
                    if (nodes.size() > 0) {
                        final IAtomicLong serialNumber = getAtomicLong(TASK_NUMBER);
                        final String taskName = String.format("TASK-%d-%d", serialNumber.incrementAndGet(), System.currentTimeMillis());
                        HazelcastHelper.this.getTaskQueue().put(taskName);
                        logger.info("Added task {}", taskName);
                    }
                    Thread.sleep(RandomUtils.nextInt(1500, 4000));
                } catch (final Exception ex) {
                    logger.error("Exception occurred!", ex);
                }
            }
        };
    }
}
