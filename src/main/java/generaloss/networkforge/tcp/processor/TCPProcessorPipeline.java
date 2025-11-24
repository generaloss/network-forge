package generaloss.networkforge.tcp.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class TCPProcessorPipeline {

    private final List<TCPConnectionProcessor> processorLayersList;

    public TCPProcessorPipeline() {
        this.processorLayersList = new ArrayList<>();
    }
    
    public Collection<TCPConnectionProcessor> getProcessors() {
        return processorLayersList;
    }

    
    public TCPConnectionProcessor getProcessor(int index) {
        return processorLayersList.get(index);
    }

    public TCPProcessorPipeline addProcessor(TCPConnectionProcessor processor) {
        processorLayersList.add(processor);
        return this;
    }

    public TCPProcessorPipeline addProcessor(int index, TCPConnectionProcessor processor) {
        processorLayersList.add(index, processor);
        return this;
    }

    public TCPProcessorPipeline removeProcessor(TCPConnectionProcessor processor) {
        processorLayersList.remove(processor);
        return this;
    }

    public TCPProcessorPipeline removeProcessor(int index) {
        processorLayersList.remove(index);
        return this;
    }


    public boolean processLayerByLayer(Function<TCPConnectionProcessor, Boolean> callbackFunc, Consumer<Throwable> errorConsumer) {
        for(TCPConnectionProcessor processor : processorLayersList) {
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
