package generaloss.networkforge.tcp.processor;

import generaloss.networkforge.tcp.event.EventDispatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class TCPProcessorPipeline {

    private final EventDispatcher eventDispatcherOf;
    private final List<TCPProcessor> processors;

    public TCPProcessorPipeline(EventDispatcher eventDispatcherOf) {
        this.processors = new ArrayList<>();
        this.eventDispatcherOf = eventDispatcherOf;
    }
    
    public Collection<TCPProcessor> getProcessors() {
        return processors;
    }

    
    public TCPProcessor getProcessor(int index) {
        return processors.get(index);
    }

    public TCPProcessorPipeline addProcessor(TCPProcessor processor) {
        processors.add(processor);
        processor.onAdded(eventDispatcherOf);
        return this;
    }

    public TCPProcessorPipeline addProcessor(int index, TCPProcessor processor) {
        processors.add(index, processor);
        return this;
    }

    public TCPProcessorPipeline removeProcessor(TCPProcessor processor) {
        processors.remove(processor);
        return this;
    }

    public TCPProcessorPipeline removeProcessor(int index) {
        processors.remove(index);
        return this;
    }


    public boolean processLayerByLayer(Function<TCPProcessor, Boolean> callbackFunc, Consumer<Throwable> errorConsumer) {
        for(TCPProcessor processor : processors) {
            try {
                final boolean cancelled = !callbackFunc.apply(processor);
                if(cancelled)
                    return false;

            } catch (Throwable t) {
                errorConsumer.accept(t);
            }
        }
        return true;
    }

}
