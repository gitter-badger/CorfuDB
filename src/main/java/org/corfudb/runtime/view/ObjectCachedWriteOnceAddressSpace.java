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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.io.IOException;

import java.util.function.Supplier;

import java.util.UUID;
/**
 * This view implements a cached write once address space
 *
 * @author Michael Wei <mwei@cs.ucsd.edu>
 */

public class ObjectCachedWriteOnceAddressSpace implements IWriteOnceAddressSpace {

    private CorfuDBRuntime client;
    private UUID logID;
    private CorfuDBView view;
    private Supplier<CorfuDBView> getView;

	private final Logger log = LoggerFactory.getLogger(org.corfudb.runtime.view.CachedWriteOnceAddressSpace.class);

    public ObjectCachedWriteOnceAddressSpace(CorfuDBRuntime client)
    {
        this.client = client;
        this.getView = () ->  {
            return this.client.getView();
        };
        this.logID = getView.get().getUUID();
    }

    public ObjectCachedWriteOnceAddressSpace(CorfuDBRuntime client, UUID logID)
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

    public ObjectCachedWriteOnceAddressSpace(CorfuDBView view)
    {
        this.view = view;
        this.getView = () -> {
            return this.view;
        };
        this.logID = getView.get().getUUID();
    }

    public void write(long address, Serializable s)
        throws IOException, OverwriteException, TrimmedException
    {
        write(address, Serializer.serialize_compressed(s));

        /*
        try (ByteArrayOutputStream bs = new ByteArrayOutputStream())
        {
            try (DeflaterOutputStream dos = new DeflaterOutputStream(bs))
            {
                Kryo k = Serializer.kryos.get();
                Output o = new Output(dos, 16384);
                k.writeClassAndObject(o, s);
                o.flush();
                o.close();
                dos.flush();
                dos.finish();
                write(address, bs.toByteArray());
            }
        }*/
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
                    return;
                }
            }
            catch (NetworkException e)
            {
                log.warn("Unable to write, requesting new view.", e);
                client.invalidateViewAndWait(e);
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
                byte[] data = null;
             //   data = AddressSpaceCache.get(logID, address);
//                if (data != null) {
  //                  stream.debug("ObjCache hit @ {}", address);
    //                return data;
      //          }

                //TODO: handle multiple segments
                CorfuDBViewSegment segments =  getView.get().getSegments().get(0);
                int mod = segments.getGroups().size();
                int groupnum =(int) (address % mod);
                long mappedAddress = address/mod;

                List<IServerProtocol> chain = segments.getGroups().get(groupnum);
                //reads have to come from last unit in chain
                IWriteOnceLogUnit wolu = (IWriteOnceLogUnit) chain.get(chain.size() - 1);
                data = wolu.read(mappedAddress);
            //    stream.debug("Objcache MISS @ {}", address);
             //   AddressSpaceCache.put(logID, address, data);
                return data;
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
         Object o = AddressSpaceObjectCache.get(logID, address);
         if (o != null) {
             return o; }

         byte[] data = read(address);
         o = Serializer.deserialize_compressed(data);
        AddressSpaceObjectCache.put(logID, address ,o);
        return o;
         /*
         Kryo k = Serializer.kryos.get();
        stream.debug("ObjCache MISS @ {}", address);

         byte[] data = read(address);
         try (ByteArrayInputStream bais = new ByteArrayInputStream(data))
         {
            try (InflaterInputStream dis = new InflaterInputStream(bais))
            {
                try (Input input = new Input(dis, 16384))
                {
                    o = k.readClassAndObject(input);
                    AddressSpaceObjectCache.put(logID, address ,o);
                    return o;
                }
            }
         }
         */
    }
}


