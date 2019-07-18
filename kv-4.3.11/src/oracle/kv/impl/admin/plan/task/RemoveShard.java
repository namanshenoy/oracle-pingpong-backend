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

import oracle.kv.impl.admin.plan.AbstractPlan;
import oracle.kv.impl.topo.Partition;
import oracle.kv.impl.topo.RepGroup;
import oracle.kv.impl.topo.RepGroupId;
import oracle.kv.impl.topo.Topology;

/**
 * A task for removing a empty shard.
 */
public class RemoveShard extends SingleJobTask {

    private static final long serialVersionUID = 1L;

    private final AbstractPlan plan;
    private final RepGroupId victim;

    public RemoveShard(AbstractPlan plan,
                       RepGroupId victim) {
        this.plan = plan;
        this.victim = victim;
    }

    @Override
    public State doWork()
        throws Exception {

        Topology sourceTopo = plan.getAdmin().getCurrentTopology();
        RepGroup rg = sourceTopo.get(victim);
        if (rg == null) {
            /* This would happen if the plan was interrupted and re-executed. */
            plan.getLogger().fine(rg + " does no exist");
            return State.SUCCEEDED;
        }

        /* Check whether the to-be-removed shard has partitions. */
        for (Partition partition : sourceTopo.getPartitionMap().getAll()) {
            if (partition.getRepGroupId().equals(victim)) {
                throw new IllegalStateException
                        ("Error removing " + victim +
                         ", shard is not empty of user data (partitions)");
            }
        }

        if (!rg.getRepNodes().isEmpty()) {
            throw new IllegalStateException(
                    "Should not be removing non-empty shard: " +
                    rg.getRepNodes());
        }

        try {
            /* Remove shard and save the changed topology. */
            sourceTopo.remove(victim);
            plan.getAdmin().saveTopo(sourceTopo, plan.getDeployedInfo(), plan);
        } catch (IllegalArgumentException iae) {
            /* This would happen if the plan was interrupted and re-executed. */
            plan.getLogger().info("The shard " + victim + " was not found.");
        }
        return State.SUCCEEDED;
    }

    @Override
    public boolean continuePastError() {
        return false;
    }

    @Override
    public String toString() {
       return super.toString() +
           " Remove Shard " + victim;
    }
}
