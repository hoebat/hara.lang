package hara.lang.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Request implements I.IRequest, I.IRequestTransact {

    private Object invokeClient(I.OFn fn, Object command) {
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
        if (client instanceof I.OFn) {
            return invokeClient((I.OFn) client, command);
        }
        throw new Ex.Unsupported("Client is not a function.");
    }

    @Override
    public Object processSingle(Object client, Object output, Object opts) {
        return output;
    }

    @Override
    public Object requestBulk(Object client, Object commands, Object opts) {
        if (client instanceof I.OFn) {
            I.OFn fn = (I.OFn) client;
            if (commands instanceof Iterable) {
                List<Object> results = new ArrayList<>();
                for (Object command : (Iterable<?>) commands) {
                    results.add(invokeClient(fn, command));
                }
                return results;
            }
        }
        throw new Ex.Unsupported("Client is not a function or commands not iterable.");
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
