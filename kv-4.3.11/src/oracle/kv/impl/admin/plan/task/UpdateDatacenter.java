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

package oracle.kv.impl.admin.plan.task;

import oracle.kv.KVVersion;
import oracle.kv.impl.admin.Admin;
import oracle.kv.impl.admin.AdminFaultException;
import oracle.kv.impl.admin.CommandResult;
import oracle.kv.impl.admin.IllegalCommandException;
import oracle.kv.impl.admin.plan.DeployDatacenterPlan;
import oracle.kv.impl.admin.plan.DeployTopoPlan;
import oracle.kv.impl.topo.Datacenter;
import oracle.kv.impl.topo.DatacenterId;
import oracle.kv.impl.topo.DatacenterType;
import oracle.kv.impl.topo.Topology;
import oracle.kv.impl.util.VersionUtil;
import oracle.kv.util.ErrorMessage;

import com.sleepycat.persist.model.Persistent;

/**
 * A task for updating the replication factor of a datacenter
 */
@Persistent(version=1)
public class UpdateDatacenter extends SingleJobTask {

    private static final long serialVersionUID = 1L;

    protected DatacenterId dcId;
    protected int newRepFactor;
    protected DeployTopoPlan plan;

    protected UpdateDatacenter(DeployTopoPlan plan, DatacenterId dcId,
                               int newRepFactor) {
        super();
        this.plan = plan;
        this.dcId = dcId;
        this.newRepFactor = newRepFactor;
    }

    /*
     * No-arg ctor for use by DPL.
     */
    @SuppressWarnings("unused")
    private UpdateDatacenter() {
    }

    /*
     * Update the repfactor for this datacenter.
     */
    @Override
    public State doWork()
        throws Exception {

        Topology current = plan.getAdmin().getCurrentTopology();
        Datacenter currentdc = current.get(dcId);

        if (checkRF(currentdc)) {
            current.update(dcId,
                       Datacenter.newInstance(currentdc.getName(),
                                              newRepFactor,
                                              currentdc.getDatacenterType(),
                                              currentdc.getAllowArbiters()));
            plan.getAdmin().saveTopo(current, plan.getDeployedInfo(), plan);
        }
        return Task.State.SUCCEEDED;
    }

    /**
     * Returns true if there is a change in RF.
     *
     * @throws IllegalCommandException if the new RF is less than the current
     * RF
     */
    protected boolean checkRF(Datacenter currentdc) {

        if (currentdc.getRepFactor() == newRepFactor) {
            /* Nothing to do */
            return false;
        }
        if (currentdc.getRepFactor() > newRepFactor) {
            throw new IllegalCommandException
                ("The proposed replication factor of " + newRepFactor +
                 "is less than the current replication factor of " +
                 currentdc.getRepFactor() +" for " + currentdc +
                 ". Oracle NoSQL Database doesn't yet " +
                 " support the ability to reduce replication factor",
                 ErrorMessage.NOSQL_5200, CommandResult.NO_CLEANUP_JOBS);
        }
        return true;
    }

    @Override
    public String toString() {
        return super.toString() +  " zone=" + dcId;
    }

    @Override
    public boolean continuePastError() {
        return false;
    }

    public static class UpdateDatacenterV2 extends UpdateDatacenter {
        private static final long serialVersionUID = 1L;

        protected final DatacenterType newType;
        private final boolean newAllowArbiters;

        public UpdateDatacenterV2(DeployTopoPlan plan, DatacenterId dcId,
                                  int newRepFactor, DatacenterType newType,
                                  boolean newAllowArbiters) {
            super(plan, dcId, newRepFactor);
            this.newType = newType;
            this.newAllowArbiters = newAllowArbiters;
            final Topology current = plan.getAdmin().getCurrentTopology();
            final Datacenter currentdc = current.get(dcId);
            if (newAllowArbiters &&
                !currentdc.getAllowArbiters()) {
                /*
                 * Updating the datacenter to support arbiters. Check that
                 * all nodes in the store are the at a version that supports
                 * arbiters. This check requires all SNs in the topo to be
                 * available.
                 */
                checkVersionRequirements();
            }
        }

        /*
         * Update the repfactor, type and/or allowArbiters of this datacenter.
         */
        @Override
        public State doWork()
            throws Exception {

            final Admin admin = plan.getAdmin();
            final Topology current = admin.getCurrentTopology();
            final Datacenter currentdc = current.get(dcId);

             if (checkRF(currentdc) ||
                 !currentdc.getDatacenterType().equals(newType) |
                 currentdc.getAllowArbiters() != newAllowArbiters) {
                 Datacenter newdc =
                     Datacenter.newInstance(currentdc.getName(),
                                            newRepFactor,
                                            newType,
                                            newAllowArbiters);
                current.update(dcId, newdc);
                admin.saveTopo(current, plan.getDeployedInfo(), plan);
            }
            return Task.State.SUCCEEDED;
        }

        /**
         * Check that the attempt change a datacenter attribute is
         * supported by the store version.
         */
        private void checkVersionRequirements() {
            final Admin admin = plan.getAdmin();
            Topology current = admin.getCurrentTopology();
            Datacenter currentdc = current.get(dcId);
            if (!currentdc.getAllowArbiters()) {
                return;
            }

            final KVVersion minANVersion =
                DeployDatacenterPlan.ARBITER_DC_VERSION;
            final KVVersion storeVersion;
            try {
                storeVersion = admin.getStoreVersion();
            } catch (AdminFaultException e) {
                throw new IllegalCommandException(
                    "Cannot change" + currentdc.getName() +
                    " zone allowing Arbiters when unable to confirm" +
                    " that all nodes in the store support" +
                    " zones allowing Arbiters, which require version " +
                    minANVersion.getNumericVersionString() +
                    " or later.",
                    e);
            }

            if (VersionUtil.compareMinorVersion(storeVersion,
                                                minANVersion) < 0) {
               throw new IllegalCommandException(
                   "Cannot change " + currentdc.getName() +
                   " zone allowing Arbiters when not all nodes in the" +
                   " store support zones allowing Arbiters." +
                   " The highest version supported by all nodes is " +
                   storeVersion.getNumericVersionString() +
                   ", but zones allowing Arbiters require version " +
                   minANVersion.getNumericVersionString() +
                   " or later.");
           }
        }
    }
}
