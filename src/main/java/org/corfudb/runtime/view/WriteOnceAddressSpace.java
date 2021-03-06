/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.corfudb.runtime.view;

import org.corfudb.runtime.*;
import org.corfudb.runtime.CorfuDBRuntime;
import org.corfudb.runtime.protocols.IServerProtocol;
import org.corfudb.runtime.protocols.logunits.IWriteOnceLogUnit;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.IOException;

import java.util.function.Supplier;

import java.util.UUID;
/**
 * This view implements a simple write once address space
 *
 * @author Michael Wei <mwei@cs.ucsd.edu>
 */

public class WriteOnceAddressSpace implements IWriteOnceAddressSpace {

    private CorfuDBRuntime client;
    private UUID logID;
    private CorfuDBView view;
    private Supplier<CorfuDBView> getView;

	private final Logger log = LoggerFactory.getLogger(WriteOnceAddressSpace.class);

    public WriteOnceAddressSpace(CorfuDBRuntime client)
    {
        this.client = client;
        this.getView = this.client::getView;
    }

    public WriteOnceAddressSpace(CorfuDBRuntime client, UUID logID)
    {
        this.client = client;
        this.logID = logID;
        this.getView = () -> {
            try {
            return this.client.getView(this.logID);
            }
            catch (RemoteException re)
            {
                log.warn("Error getting remote view", re);
                return null;
            }
        };
    }

    public WriteOnceAddressSpace(CorfuDBView view)
    {
        this.view = view;
        this.getView = () -> {
            return this.view;
        };
    }

    public void write(long address, Serializable s)
        throws IOException, OverwriteException, TrimmedException
    {
        try (ByteArrayOutputStream bs = new ByteArrayOutputStream())
        {
            try (ObjectOutput out = new ObjectOutputStream(bs))
            {
                out.writeObject(s);
                write(address, bs.toByteArray());
            }
        }

    }
    public void write(long address, byte[] data)
        throws OverwriteException, TrimmedException
    {
        while (true)
        {
            try {
                //TODO: handle multiple segments
                CorfuDBViewSegment segments =  getView.get().getSegments().get(0);
                int mod = segments.getGroups().size();
                int groupnum =(int) (address % mod);
                List<IServerProtocol> chain = segments.getGroups().get(groupnum);
                //writes have to go to chain in order
                long mappedAddress = address/mod;
                for (IServerProtocol unit : chain)
                {
                    ((IWriteOnceLogUnit)unit).write(mappedAddress,data);
                }
                return;
            }
            catch (NetworkException e)
            {
                log.warn("Unable to write, requesting new view.", e);
                client.invalidateViewAndWait(e);
                //okay so, if we read on the same address, is it now successful?
                try {
                    if (Arrays.equals(read(address), data)) {
                        return;
                    }
                }
                catch (Exception ex)
                {
                    log.warn("View refreshed, but write was not successful: retrying.", ex);
                }
            }
        }
    }

    public byte[] read(long address)
        throws UnwrittenException, TrimmedException
    {
        //TODO: cache the layout so we don't have to determine it on every write.

        while (true)
        {
            try {
                //TODO: handle multiple segments
                CorfuDBViewSegment segments =  getView.get().getSegments().get(0);
                int mod = segments.getGroups().size();
                int groupnum =(int) (address % mod);
                List<IServerProtocol> chain = segments.getGroups().get(groupnum);
                //reads have to come from last unit in chain
                IWriteOnceLogUnit wolu = (IWriteOnceLogUnit) chain.get(chain.size() - 1);
                long mappedAddress = address/mod;
                return wolu.read(mappedAddress);
            }
            catch (NetworkException e)
            {
                log.warn("Unable to read, requesting new view.", e);
                client.invalidateViewAndWait(e);
            }
        }
    }

    public Object readObject(long address)
    throws UnwrittenException, TrimmedException, ClassNotFoundException, IOException
    {
        byte[] payload = read(address);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(payload))
        {
            try (ObjectInputStream ois = new ObjectInputStream(bis))
            {
                return ois.readObject();
            }
        }
    }

}


