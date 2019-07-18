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

package oracle.kv.impl.topo;

/** The type of a datacenter. */
public enum DatacenterType {

    /** Contains electable nodes. */
    PRIMARY() {
        @Override
        public boolean isPrimary() {
            return true;
        }
    },

    /** Contains secondary nodes. */
    SECONDARY() {
        @Override
        public boolean isSecondary() {
            return true;
        }
    };

    /**
     * Returns whether this is the {@link #PRIMARY} type.
     *
     * @return whether this is {@code PRIMARY}
     */
    public boolean isPrimary() {
        return false;
    }

    /**
     * Returns whether this is the {@link #SECONDARY} type.
     *
     * @return whether this is {@code SECONDARY}
     */
    public boolean isSecondary() {
        return false;
    }
}
