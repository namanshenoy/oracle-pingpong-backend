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

package oracle.kv.impl.api.table;

/**
 *
 */
class DropIndex extends TableChange {
    private static final long serialVersionUID = 1L;

    private final String indexName;
    private final String tableName;

    DropIndex(String indexName,
              String tableName,
              int seqNum) {
        super(seqNum);
        this.indexName = indexName;
        this.tableName = tableName;
    }

    @Override
    public boolean apply(TableMetadata md) {
        md.removeIndex(indexName, tableName);
        return true;
    }
}
