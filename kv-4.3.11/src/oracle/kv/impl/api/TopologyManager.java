/*-
 * Copyright (C) 2011, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This file was distributed by Oracle as part of a version of Oracle NoSQL
 * Database made available at:
 *
 * http://www.oracle.com/technetwork/database/database-technologies/nosqldb/downloads/index.html
 *
 * Please see the LICENSE file included in the top-level directory of the
 * appropriate version of Oracle NoSQL Database for a copy of the license and
 * additional information.
 */

package oracle.kv.impl.api;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kv.StaleStoreHandleException;
import oracle.kv.impl.fault.OperationFaultException;
import oracle.kv.impl.fault.WrappedClientException;
import oracle.kv.impl.security.InvalidSignatureException;
import oracle.kv.impl.topo.Partition;
import oracle.kv.impl.topo.PartitionId;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.topo.change.TopologyChange;

/**
 * Coordinates access to the in-memory copy of the Topology. Saving the
 * Topology in an environment is done, if needed, by the RepNode itself.
 * <p>
 * It makes provisions for registering pre and post update listeners that are
 * invoked whenever the topology is changed. It's worth noting that there are
 * three sets of callbacks that are executed in the following sequence:
 * PreUpdateListener callbacks, Localizer callbacks, PostUpdateListener
 * callbacks.
 * <p>
 * Note that some of the methods relating to the persistent management of
 * topology are in RepNode rather than in this class where they would appear to
 * belong logically. This is to ensure that this shared class which is used
 * both by KV clients and RNs does not contain references to JE classes.
 */
public class TopologyManager {

    /**
     * The name of the kvstore
     */
    private final String kvsName;

    /* The current in-memory copy of the Topology. */
    private volatile Topology topology;

    /**
     * The local topology. The local topology can only ever differ from the
     * in-memory copy when the manager is running on the RepNode. In this case
     * the local topology may contain modifications to the "official"
     * topology due to partition migration activity. The local topology must
     * only be used to direct client operations and must NEVER be sent to
     * another node.
     */
    private Topology localTopology;

    private Localizer localizer = null;

    /**
     * The listeners to be invoked before proceeding with a Topology update.
     * Access must be synchronized on the manager instance.
     */
    private final List<PreUpdateListener> preUpdateListeners =
        new LinkedList<PreUpdateListener>();

    /**
     * The listeners to be invoked after a Topology update. Access must be
     * synchronized on the manager instance. If the listener is held weakly
     * then value is null, which allows the reference to be gc'ed, otherwise
     * value is the listener keeping a strong reference on the listener.
     * WeakHashMap key references are weak, while the value references are
     * strong.
     */
    private final Map<PostUpdateListener, PostUpdateListener>
                    postUpdateListeners = new WeakHashMap<PostUpdateListener,
                                                          PostUpdateListener>();

    /**
     *  The number of topology changes to be retained when managing the
     *  topology.
     */
    private final int maxTopoChanges;

    private final Logger logger;

    /**
     * The constructor. Note that the manager starts out with a null Topology.
     * It's first initialized with a call to {@link #update}
     *
     * @param kvsName the name of the store
     * @param maxTopoChanges the max number of changes to be retained
     * @param logger a logger
     */
    public TopologyManager(String kvsName,
                           int maxTopoChanges,
                           Logger logger) {
        this.kvsName = kvsName;
        this.maxTopoChanges = maxTopoChanges;
        this.logger = logger;
    }

    /**
     * Adds a pre update listener to help track Topology changes. The primary
     * purpose of the pre listener is to permit topology validation before
     * updating to a new topology.
     *
     * @param listener the new listener
     */
    public synchronized void addPreUpdateListener(PreUpdateListener listener) {
        if (!preUpdateListeners.contains(listener)) {
            preUpdateListeners.add(listener);
        }
    }

    /**
     * Adds a post update listener to help track Topology changes. All
     * components that are dependent upon the Topology should register a
     * listener, so they can be kept informed whenever the Topology changes.
     *
     * @param listener the new listener
     */
    public void addPostUpdateListener(PostUpdateListener listener) {
        addPostUpdateListener(listener, false);
    }

    /**
     * Adds a post update listener to help track Topology changes. All
     * components that are dependent upon the Topology should register a
     * listener, so they can be kept informed whenever the Topology changes.
     * If weak is true the listener is maintained with a weak reference allowing
     * it to get GCed when the caller is done with it.
     *
     * @param listener the new listener
     */
    public synchronized void addPostUpdateListener(PostUpdateListener listener,
                                                    boolean weak) {
        if (!postUpdateListeners.containsKey(listener)) {

            /*
             * If weak, set the value to null so that the listner can be gc'ed.
             * Otherwise keep a hard reference to the listener via the value.
             */
            postUpdateListeners.put(listener,  weak ? null : listener);
        }
    }

    /**
     * Removes the specified post update listener. This method should not be
     * invoked from PostUpdateListener.postUpdate().
     *
     * @param listener the listener to remove
     */
    public synchronized void
            removePostUpdateListener(PostUpdateListener listener) {
        postUpdateListeners.remove(listener);
    }

    /**
     * Invoke the registered pre update listeners. These listeners are invoked
     * before the "official" topology is updated.
     */
    private void invokePreUpdateListeners(Topology newTopology) {
        assert Thread.holdsLock(this);
        /* Inform the listeners. */
        for (PreUpdateListener l : preUpdateListeners) {
            l.preUpdate(newTopology);
        }
    }

    /**
     * Invoke the registered post update listeners. These listeners are invoked
     * after either the "official" or local topology has been updated.
     */
    private void invokePostUpdateListeners() {
        assert Thread.holdsLock(this);
        /* Inform the listeners. */
        final Iterator<PostUpdateListener> itr =
                                    postUpdateListeners.keySet().iterator();
        while (itr.hasNext()) {
            if (itr.next().postUpdate(topology)) {
                itr.remove();
            }
        }
    }

    /**
     * Sets the localizer object for this manager. The localizer's
     * localizeTopology() method will be invoked when the topology is
     * updated.
     *
     * @param localizer
     */
    public void setLocalizer(Localizer localizer) {
        this.localizer = localizer;
    }

    public Topology getTopology() {
        return topology;
    }

    /**
     * Returns the local topology for this node. This should only be used to
     * direct client requests. The returned topology must NEVER be sent to
     * another node.
     *
     * @return the local topology
     */
    public Topology getLocalTopology() {
        return (localTopology == null) ? topology : localTopology;
    }

    /**
     * For use by unit tests only.
     */
    public void setLocalTopology(Topology localTopology) {
        this.localTopology = localTopology;
    }

    /**
     * Updates the Topology by replacing the entire Topology with a new
     * instance. This is typically done in response to a request from the SNA.
     * Or if the topology cannot be update incrementally because the
     * necessary sequence of changes is not available in incremental form.
     *
     * The update is only done if the Topology is not current. If the Topology
     * needed to be updated, but the update failed false is returned. Otherwise
     * true is returned.
     *
     * @param newTopology the new Topology
     *
     * @return false if the update failed
     */
    public synchronized boolean update(Topology newTopology) {

        final int currSeqNum;
        if (topology != null) {
            if (!kvsName.equals(topology.getKVStoreName())) {
                throw new IllegalArgumentException
                    ("Update topology associated with KVStore: " +
                      topology.getKVStoreName() + " expected: " + kvsName);
            }
            checkTopologyId(topology.getId(), newTopology.getId());
            currSeqNum = topology.getSequenceNumber();
        } else {
            currSeqNum = 0;
        }

        final int newSequenceNumber = newTopology.getSequenceNumber();

        if (currSeqNum >= newSequenceNumber) {
            logger.log(Level.INFO,
                       "Topology update skipped. " +
                       "Current seq #: {0} Update seq #: {1}",
                       new Object[]{currSeqNum, newSequenceNumber});
            return true;
        }

        checkVersion(logger, newTopology);

        /*
         * Pre-updater may verify the signature of new topology copy. If the
         * verification failed, don't continue with the update;
         */
        try {
            invokePreUpdateListeners(newTopology);
        } catch (InvalidSignatureException ise) {
            logger.log(Level.INFO,
                       "Topology udpate to seq# {0} skipped due to " +
                       "invalid signature.",
                       newSequenceNumber);
            return false;
        }

        /*
         * If updating the local topology fails don't continue with the update.
         */
        if (!updateLocalTopology(newTopology)) {
            return false;
        }

        logger.log(Level.INFO, "Topology updated from seq#: {0} to {1}",
                   new Object[]{currSeqNum, newSequenceNumber});

        topology =
            pruneChanges(newTopology, Integer.MAX_VALUE, maxTopoChanges);

        /*
         * Inform components that are dependent upon the Topology, so they
         * can fix their internal state.
         */
        invokePostUpdateListeners();
        return true;
    }

    /**
     * Ensures that any changes in partition assignment at an RN can be
     * explained by elasticity operations that are in progress. This
     * verification relies on use of an absolutely consistent local topology
     * which is only available at the master, so the check is only done on the
     * master. It's the caller's responsibility to ensure that the method is
     * only invoked on the master. The call is currently accomplished via the
     * PreUpdateListener registered by the RepNode which has access to the
     * replicated environment handle and can determine the HA state and
     * decide whether the call should be made.
     *
     * @param rgId the replication group associated with the checks
     *
     * @param newTopo the new topology that is being checked
     *
     * @throws IllegalStateException if the partition checks fail
     */
    public void checkPartitionChanges(RepGroupId rgId,
                                      Topology newTopo)
        throws IllegalStateException {

        if ((topology == null) ||(topology.getPartitionMap().size() == 0)) {
            return;
        }

        final Set<PartitionId> currentPartitions =
            getRGPartitions(rgId, topology);

        final Set<PartitionId> newPartitions = getRGPartitions(rgId, newTopo);

        for (PartitionId npId : newPartitions) {
            final Partition np = newTopo.get(npId);
            final Partition cp = topology.get(npId);

            if (np.getRepGroupId().equals(cp.getRepGroupId())) {
                /*
                 * Has the same RG in old and new topos. Ignore the local
                 * topology, it can contain entries for partitions that are
                 * still in flight.
                 */
                currentPartitions.remove(npId);
                continue;
            }

            /* Old/new mismatch account for it via local topology. */
            final Partition lp = (localTopology != null) ?
                localTopology.get(npId) : null;
            /*
             * A new partition associated with the RG. It should be compatible
             * with the definition in the local topology.
             */
            if (lp == null) {
                /*
                 * There cannot be a difference if no migration is in
                 * progress.
                 */
                final String msg =
                    String.format("%s in the new topology(seq #: %,d) " +
                                  "is absent from this shard in the current " +
                                  "topology(seq #: %,d) and there is no " +
                                  "partition migration in progress.",
                                  np, newTopo.getSequenceNumber(),
                                  topology.getSequenceNumber());

                throw new IllegalStateException(msg);
            }

            /*
             * Check whether the partition has been migrated and is therefore
             * in the local topology.
             */
            if (lp.getRepGroupId().equals(np.getRepGroupId())) {

                /*
                 * If it's in the new topology, its RG must exist with that RG
                 * in the local topology
                 */
                continue;
            }

            /* Disagreement on which RG the partition should be in. */
            final String msg =
                String.format("%s in the new topology(seq #: %,d) and %s " +
                              "in the local topology(internal seq#: %,d) " +
                              "are associated with different shards",
                              np, newTopo.getSequenceNumber(),
                              lp, localTopology.getSequenceNumber());
            throw new IllegalStateException(msg);
        }

        /*
         * Any residual current partitions (after the removal of matching
         * partitions above) should represent partitions that were migrated
         * away from this migration source. They must be in the local topology,
         * their definition in the localTopology may not agree with the
         * definition in the new topology, since they may be in the process of
         * being migrated.
         */
        for (PartitionId cpId : currentPartitions) {
            final Partition cp = topology.get(cpId);

            final Partition lp = (localTopology != null) ?
                localTopology.get(cpId) : null;

            if (lp == null) {
                /*
                 * There cannot be a difference if no migration is in
                 * progress.
                 */
                final String msg =
                    String.format("%s is in the current topology(seq #: %,d)" +
                                  " but is absent from the new topology" +
                                  "(seq #: %,d) and there is no " +
                                  "partition migration in progress.",
                                  cp, topology.getSequenceNumber(),
                                  newTopo.getSequenceNumber());

                throw new IllegalStateException(msg);
            }


            /*
             * Check whether the partition has been migrated away and is
             * therefore in the local topology associated with a different RG
             */

            if (!lp.getRepGroupId().equals(cp.getRepGroupId())) {
                /*
                 * A partition that was migrated out of this group. Note that
                 * we are not actually checking whether the migration has
                 * completed to keep things simple.
                 */
                continue;
            }

            /*
             * Partition is present in the local topology with the same RG as
             * the current topo. Disagreement on RGs between current and new
             * topo that cannot be justified by the local topo.
             */
            final String msg =
                String.format("%s is associated with the same shard in both " +
                          "the current(seq #: %,d) and local topologies " +
                              "but is associated with a different shard %s " +
                              "in the new topology(seq#: %,d). ",
                              cp,
                              topology.getSequenceNumber(),
                              newTopo.get(cpId).getRepGroupId(),
                              newTopo.getSequenceNumber());

            throw new IllegalStateException(msg);
        }
    }

    /**
     * A utility method to retrieve all the partitions associated with an RG
     *
     * @param rgId identifies the filtering RG
     *
     * @param topo the topology containing the partitions
     *
     * @return the partition ids of the partitions hosted by the RG
     */
    private Set<PartitionId> getRGPartitions(RepGroupId rgId,
                                             Topology topo) {

        final Set<PartitionId> hostedPartitions =
            new HashSet<PartitionId>(100);

        for (Partition p : topo.getPartitionMap().getAll()) {
            if (!p.getRepGroupId().equals(rgId)) {
                continue;
            }

            hostedPartitions.add(p.getResourceId());
        }

        return hostedPartitions;
    }

    /*
     * Checks the topology version to make sure its acceptable. The version
     * should typically have been upgraded to the current version as a
     * consequence of deserialization.
     */
    public static void checkVersion(Logger logger,
                                    Topology topology) {

        final int topoVersion = topology.getVersion();

        if (topoVersion == Topology.CURRENT_VERSION) {
            return; /* All's well, keep going. */
        }

        if (topoVersion == 0) {

            /*
             * r1 topology, inconsistent distribution of RNs across DCs. Warn
             * and keep going.
             */
            logger.warning("Using r1 topology, it was not upgraded.");
        } else {
            /* Should not happen. */
            throw new OperationFaultException
                ("Encountered topology with version: " + topoVersion +
                 " Current topology version: " + Topology.CURRENT_VERSION);
        }
    }

    /**
     * Performs an incremental update to the Topology.
     * <p>
     * The update is sometimes done in the request/response loop, but it would
     * be better if the update was done asynchronously so as not to impact
     * request latency. We need an async version of the update operation for
     * this purpose. Not a pressing issue, since Topology updates are
     * infrequent.
     * <p>
     * An update may result in the topology changes being pruned so that only
     * the configured number of changes are retained.
     * <p>
     * This method has package access for unit test
     * <p>
     * A contract of this method is that, the topology Id, the change
     * information and the signature should come from the same topology
     * instance.
     *
     * @param topologyId the topology id associated with the changes
     * @param changes the changes to be made to the current copy of the
     * Topology
     * @param topoSignature the signature of the topology where the changes
     * originated.
     */
    synchronized void update(long topologyId,
                             List<TopologyChange> changes,
                             byte[] topoSignature) {

        /*
         * The topology can be null if the node was were waiting for a topo
         * push from another node, e.g. during replica start up.
         */
        final Topology workingCopy = (topology == null) ?
            new Topology(kvsName, topologyId) :  topology.getCopy();

        checkTopologyId(workingCopy.getId(), topologyId);

        final int prevSequenceNumber = workingCopy.getSequenceNumber();

        if (!workingCopy.apply(changes)) {
            /* Topology not changed */
            return;
        }

        workingCopy.updateSignature(topoSignature);

        /*
         * Pre-updater may verify the signature of new topology copy. If the
         * verification fails, don't continue with the update;
         */
        try {
            invokePreUpdateListeners(workingCopy);
        } catch (InvalidSignatureException ise) {
            logger.log(Level.INFO,
                       "Topology incremental update to seq# {0} skipped " +
                       "due to invalid signature.",
                       workingCopy.getSequenceNumber());
            return;
        }

        if (!updateLocalTopology(workingCopy)) {
            return;
        }

        /* Make an atomic change. */
        topology = pruneChanges(workingCopy,
                                changes.get(0).getSequenceNumber(),
                                maxTopoChanges);
        logger.log(Level.INFO,
                   "Topology incrementally updated from seq#: {0} to {1}",
                   new Object[]{prevSequenceNumber,
                                topology.getSequenceNumber()});
        invokePostUpdateListeners();
    }

    public synchronized void update(TopologyInfo topoInfo) {
        update(topoInfo.getTopoId(), topoInfo.getChanges(),
               topoInfo.getTopoSignature());
    }

    /**
     * Verifies that the remote topology being used to update the local
     * topology is compatible with it. All pre r2 topologies or r2 topologies
     * that are communicated by r1 clients have the topology id zero. They
     * are assumed to match non-zero r2 topologies for compatibility.
     *
     * @param localTopoId the local topology id
     * @param remoteTopoId the remote topology id
     */
    private void checkTopologyId(long localTopoId,
                                 long remoteTopoId) {
        if (localTopoId == remoteTopoId) {
            return;
        }

        // TODO: Remove if we decide not to support r1 clients with r2 RNs
        if ((localTopoId == Topology.NOCHECK_TOPOLOGY_ID) ||
            (remoteTopoId == Topology.NOCHECK_TOPOLOGY_ID)) {
            return;
        }

        final String msg = "Inconsistent use of Topology. " +
            "An attempt was made to update this topology created on " +
            new Date(localTopoId) +
            " with changes originating from a different topology created on " +
            new Date(remoteTopoId) +
            ". This exception indicates an application configuration issue." +
            " Check if this store handle belongs to an older, now defunct " +
            "store.";

        /*
         * Note that we intentionally throw an operation fault exception,
         * rather than IllegalStateException. The latter is a catastrophic
         * exception that shuts down the RN process. This led to the bug
         * described in SR [#24693], where connection attempts from old clients
         * make an RN repeatedly throw IllegalStateException, which ultimately
         * brings the RN down! Clients should never be able to create
         * server side failure like that, so this has been changed to
         * StoreStaleHandleException, so the client knows it has to close and
         * reopen its handle.
         */
        throw new WrappedClientException(new StaleStoreHandleException(msg));
    }

    /**
     *  Prune changes from the Topology.
     *
     * @param topo the topology to be pruned; it's modified as a result of the
     * pruning
     * @param limitSeqNum changes <= this seq num must be retained if present
     * @param maxTopoChanges the maximum trailing changes to be retained
     *
     * @return the pruned topology
     */
    public static Topology pruneChanges(Topology topo,
                                         int limitSeqNum,
                                         int maxTopoChanges) {

        final int firstChangeSeqNum =
            topo.getChangeTracker().getFirstChangeSeqNum();

        if (firstChangeSeqNum == -1) {
            /* No changes to prune. */
            return topo;
        }

        final int firstRetainedChangeSeqNum =
            Math.min(topo.getSequenceNumber() - maxTopoChanges  + 1,
                     limitSeqNum);

        if (firstRetainedChangeSeqNum <= firstChangeSeqNum) {
            /* Nothing to prune. */
            return topo;
        }

        topo.discardChanges(firstRetainedChangeSeqNum - 1);

        return topo;
    }

    /**
     * Updates the local topology if possible. Returns true if the local
     * topology was updated otherwise false. If the local topology is updated
     * the listeners are invoked.
     *
     * @return true if local topology was updated
     */
    public synchronized boolean updateLocalTopology() {
        /*
         * Special case of the topology not yet being initialized. In this case
         * report that things are OK, but don't invoke the listeners.
         */
        if (topology == null) {
            return true;
        }
        if (!updateLocalTopology(topology)) {
            return false;
        }
        invokePostUpdateListeners();
        return true;
    }

    /**
     * Updates the local topology if possible. Returns true if the local
     * topology was updated otherwise false.
     *
     * @param newTopology the topology to localize
     * @return true if localTopology was updated
     */
    private boolean updateLocalTopology(Topology newTopology) {
        if (localizer == null) {
            return true;
        }

        Topology local = localizer.localizeTopology(newTopology);

        if (local == null) {
            logger.log(Level.INFO, "Topology update to {0} skipped. " +
                       "Unable to update local topology.",
                       newTopology.getSequenceNumber());
            return false;
        }
        localTopology = local;
        return true;
    }

    /**
     * Returns true if the partition is in the process
     * of moving (changing groups) or has moved.
     */
    public boolean inTransit(PartitionId partitionId) {

        if (partitionId.isNull()) {
            return false;
        }
        final RepGroupId localGroupId =
                                getLocalTopology().getRepGroupId(partitionId);
        final RepGroupId currentGroupId =
                                getTopology().getRepGroupId(partitionId);

        if ((localGroupId == null) || (currentGroupId == null)) {
            return false;
        }

        /*
         * If the local group has changed, then the partition is in transit.
         */
        return localGroupId.getGroupId() != currentGroupId.getGroupId();
    }

    public interface PostUpdateListener {

        /**
         * The update method is invoked after either the "official" or "local"
         * topology has been updated. Implementations must take care to avoid
         * deadlocks as the topology manager instance will be locked at the
         * time of the call to postUpdate().
         *
         * @return true if the listener is no longer needed and can be removed
         * from the list
         */
        boolean postUpdate(Topology topology);
    }

    public interface PreUpdateListener {

        /**
         * The update method is invoked before the "official" topology is
         * updated. Exceptions resulting from the listener will abort the
         * topology update operation. Implementations must take care to avoid
         * deadlocks as the topology manager instance will be locked at the
         * time of the call to preUpdate().
         */
        void preUpdate(Topology topology);
    }

    public interface Localizer {

        /**
         * Localizes the specified topology. The localized topology is returned.
         * The return value may be the input topology if no changes are made.
         * Null is returned if it was not possible to localize the topology.
         *
         * @param topology the topology to localize
         * @return a localized topology or null
         */
        Topology localizeTopology(Topology topology);
    }
}
