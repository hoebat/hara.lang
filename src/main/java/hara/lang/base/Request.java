package hara.lang.base;

import java.util.Collection;
import hara.lang.data.Vector;

public class Request implements I.IRequest, I.IRequestTransact {

    private Object invokeClient(I.IClient fn, Object command) {
        if (command instanceof Object[]) {
            return fn.invoke((Object[]) command);
        } else if (command instanceof Collection) {
            return fn.invoke(((Collection<?>) command).toArray());
        } else {
            return fn.invoke(command);
        }
    }

    @Override
    public Object requestSingle(Object client, Object command, Object opts) {
        if (!(client instanceof I.IClient)) {
            throw new Ex.Unsupported("Client does not implement I.IClient.");
        }
        return invokeClient((I.IClient) client, command);
    }

    @Override
    public Object processSingle(Object client, Object output, Object opts) {
        return output;
    }

    @Override
    public Object requestBulk(Object client, Object commands, Object opts) {
        if (!(client instanceof I.IClient)) {
            throw new Ex.Unsupported("Client does not implement I.IClient.");
        }
        if (!(commands instanceof Iterable)) {
            throw new Ex.Unsupported("Commands are not iterable.");
        }

        I.IClient fn = (I.IClient) client;
        Vector.Mutable<Object> results = Vector.Mutable.empty(null);
        for (Object command : (Iterable<?>) commands) {
            results.pushLast(invokeClient(fn, command));
        }
        return results.toPersistent();
    }

    @Override
    public Object processBulk(Object client, Object inputs, Object outputs, Object opts) {
        return outputs;
    }

    @Override
    public Object transactStart(Object client) {
        return null;
    }

    @Override
    public Object transactEnd(Object client) {
        return null;
    }

    @Override
    public Object transactCombine(Object client, Object commands) {
        return null;
    }
}
