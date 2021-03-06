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

package org.corfudb.runtime.stream;

import org.corfudb.runtime.*;
import org.corfudb.runtime.entries.CorfuDBStreamEntry;
import org.corfudb.runtime.entries.IStreamEntry;
import org.corfudb.runtime.view.Serializer;

import java.lang.ClassNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 *  A stream interface.
 *
 *  Streams are slightly more restrictive than logs:
 *  Random reads are only possible when given a timestamp, and they must be read starting
 *  from the head of the stream.
 */
public interface IStream extends AutoCloseable {

    /**
     * Append an object to the stream. This operation may or may not be successful. For example,
     * a move operation may occur, and the append will not be part of the stream.
     *
     * @param data      A serializable object to append to the stream.
     *
     * @return          A timestamp, which reflects the physical position and the epoch the data was written in.
     */
    ITimestamp append(Serializable data)
        throws OutOfSpaceException, IOException;

    /**
     * Append an object to the stream, along with the set of other objects to which
     * the payload is relevant.  This method is deprecated because it represents a way to
     * support multi-object operations (in support of txns) that is too tightly coupled
     * with the stream interface. To wit: what sense does it make for a stream to append
     * things for more than one stream.
     *
     * TODO: FIXME: This will be refactored into a transaction layer
     * at some point in the future.
     *
     * @param data      A serializable object to append to the stream.
     *
     * @param streams   A list of streams ids to which the given payload applies.
     *
     * @return          A timestamp, which reflects the physical position and the epoch the data was written in.
     */
    default ITimestamp append(Serializable payload, Set<UUID> streams)
            throws OutOfSpaceException, IOException {
        throw new RuntimeException("multi-stream append not supported by this IStream implementation.");
    }

   /**
     * Read the next entry in the stream as a IStreamEntry. This function
     * retrieves the next entry in the stream, or null, if there are no more entries in the stream.
     *
     * @return      A CorfuDBStreamEntry containing the payload of the next entry in the stream.
     */
    IStreamEntry readNextEntry()
    throws HoleEncounteredException, TrimmedException, IOException;

    /**
     * Read the next entry in the stream as an Object. This convenience function
     * retrieves the next entry in the stream, blocking if necessary.
     *
     * @return      A deserialized object containing the payload of the next entry in the stream.
     */
    default Object readNextObject()
    throws HoleEncounteredException, TrimmedException, IOException, InterruptedException, ClassNotFoundException
    {
        IStreamEntry next = readNextEntry();
        return next == null ? null : next.getPayload();
    }

    /**
     * Given a timestamp, reads the entry at the timestamp
     * @param timestamp     The timestamp to read from.
     * @return              The entry located at that timestamp.
     */
    IStreamEntry readEntry(ITimestamp timestamp)
    throws HoleEncounteredException, TrimmedException, IOException;

    /**
     * Read a entry in the stream as an Object. This convenience function
     * retrieves an entry in the stream.
     * @param timestamp     The timestamp to read from.
     * @return              The entry located at that timestamp.
     */
    default Object readObject(ITimestamp timestamp)
    throws HoleEncounteredException, TrimmedException, IOException, InterruptedException, ClassNotFoundException
    {
        IStreamEntry entry = readEntry(timestamp);
        return entry == null ? null : entry.getPayload();
    }

    /**
     * Given a timestamp, get the timestamp in the stream
     * @param ts            The timestamp to increment.
     * @return              The next timestamp in the stream, or null, if there are no next timestamps in the stream.
     */
    ITimestamp getNextTimestamp(ITimestamp ts);

    /**
     * Given a timestamp, get a proceeding timestamp in the stream.
     * @param ts            The timestamp to decrement.
     * @return              The previous timestamp in the stream, or null, if there are no previous timestamps in the stream.
     */
    ITimestamp getPreviousTimestamp(ITimestamp ts);

    /**
     * Returns a fresh timestamp, which can serve as a linearization point. This function
     * may return a non-linearizable (invalid) timestamp which may never occur in the ordering
     * due to a move/epoch change.
     *
     * @return      A timestamp, which reflects the most recently allocated timestamp in the stream.
     */
    default ITimestamp check() {
        return check(false);
    }

    /**
     * Returns a fresh or cached timestamp, which can serve as a linearization point. This function
     * may return a non-linearizable (invalid) timestamp which may never occur in the ordering
     * due to a move/epoch change.
     *
     * @param       cached      Whether or not the timestamp returned is cached.
     * @return                  A timestamp, which reflects the most recently allocated timestamp in the stream,
     *                          or currently read, depending on whether cached is set or not.
     */
    ITimestamp check(boolean cached);

    /**
     * Gets the current position the stream has read to (which may not point to an entry in the
     * stream).
     *
     * @return                  A timestamp, which reflects the most recently read address in the stream.
     */
    ITimestamp getCurrentPosition();

    /** Requests a trim on this stream. This function informs the configuration master that the
     * position on this stream is trimmable, and moves the start position of this stream to the
     * new position.
     */
    void trim(ITimestamp address);

    /**
     * Close the stream. This method must be called to free resources.
     */
    void close();

    /**
     * Get the ID of the stream.
     * @return                  The ID of the stream.
     */
    UUID getStreamID();

    /**
     * Get the runtime that this stream belongs to.
     * @return                  The runtime the stream belongs to.
     */
    CorfuDBRuntime getRuntime();
}
