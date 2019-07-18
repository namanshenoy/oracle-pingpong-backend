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

package oracle.kv.impl.mgmt;

import java.rmi.Remote;
import java.rmi.RemoteException;

import oracle.kv.impl.measurement.ServiceStatusChange;
import oracle.kv.impl.param.ParameterMap;
import oracle.kv.impl.rep.monitor.StatsPacket;

import com.sleepycat.je.rep.StateChangeEvent;

/**
 * These methods are called by the RepNode to inform the MgmtAgent of the
 * RepNode's status.  This remote interface does not need to be versioned,
 * because the two participants will always be local, and always be the from
 * same release.
 */
public interface RepNodeStatusReceiver extends Remote {

    public void updateRnStatus(ServiceStatusChange newStatus)
        throws RemoteException;

    public void receiveStats(StatsPacket packet)
        throws RemoteException;

    public void receiveNewParams(ParameterMap newMap)
        throws RemoteException;

    public void updateReplicationState (StateChangeEvent sce)
        throws RemoteException;
}
