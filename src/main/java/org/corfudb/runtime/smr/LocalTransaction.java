package org.corfudb.runtime.smr;

import org.corfudb.runtime.CorfuDBRuntime;
import org.corfudb.runtime.stream.IStream;
import org.corfudb.runtime.stream.ITimestamp;
import org.corfudb.runtime.stream.SimpleStream;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by mwei on 6/1/15.
 */
public class LocalTransaction implements ITransaction{

    ISMREngine executingEngine;
    ITimestamp timestamp;
    CorfuDBRuntime runtime;

    public LocalTransaction(ISMREngine executingEngine, ITimestamp timestamp, CorfuDBRuntime runtime)
    {
        this.executingEngine = executingEngine;
        this.timestamp = timestamp;
        this.runtime = runtime;
    }

    /**
     * Returns an SMR engine for a transactional context.
     *
     * @param streamID The streamID the SMR engine should run on.
     * @param objClass The class that the SMR engine runs against.
     * @return The SMR engine to be used for a transactional context.
     */
    @Override
    @SuppressWarnings("unchecked")
    public ISMREngine getEngine(UUID streamID, Class<?> objClass) {
        if (streamID.equals(executingEngine.getStreamID()))
        {
            return new PassThroughSMREngine(executingEngine.getObject(), timestamp);
        }
        else
        {
            IStream sTemp = runtime.openStream(streamID, SimpleStream.class);
            ISMREngine engine = new OneShotSMREngine(sTemp, objClass, timestamp);
            engine.sync(timestamp);
            return engine;
        }
    }

    /**
     * Registers a stream to be part of a transactional context.
     *
     * @param stream A stream that will be joined into this transaction.
     */
    @Override
    public void registerStream(UUID stream) {

    }

    /**
     * Sets the CorfuDB runtime for this transaction. Used when deserializing
     * the transaction.
     *
     * @param runtime The runtime to use for this transaction.
     */
    @Override
    public void setCorfuDBRuntime(CorfuDBRuntime runtime) {

    }

    /**
     * return a pointer to the runtime managing this transaction.
     * this is needed for transactions on objects which may create
     * new objects during that transaction.
     *
     * @return the runtime
     */
    @Override
    public CorfuDBRuntime getRuntime() {
        return null;
    }

    /**
     * Set the command to be executed for this transaction.
     *
     * @param transaction The command(s) to be executed for this transaction.
     */
    @Override
    public void setTransaction(ITransactionCommand transaction) {

    }

    /**
     * Execute this command on a specific SMR engine.
     *
     * @param engine The SMR engine to run this command on.
     */
    @Override
    public void executeTransaction(ISMREngine engine) {

    }

    /**
     * Returns the transaction command.
     *
     * @return The command(s) to be executed for this transaction.
     */
    @Override
    public ITransactionCommand getTransaction() {
        return null;
    }

    /**
     * Propose to the SMR engine(s) for the transaction to be executed.
     *
     * @return The timestamp that the transaction was proposed at.
     * This timestamp should be a valid timestamp for all streams
     * that the transaction belongs to, otherwise, the transaction
     * will abort.
     */
    @Override
    public ITimestamp propose() throws IOException {
        throw new UnsupportedOperationException("Local transactions can never be proposed to the log");
    }
}
