package generaloss.networkforge;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Objects;

@FunctionalInterface
public interface SelectionKeyConsumer {

    void accept(SelectionKey key) throws IOException;

    default SelectionKeyConsumer andThen(SelectionKeyConsumer after) {
        Objects.requireNonNull(after);
        return (t) -> {
            this.accept(t);
            after.accept(t);
        };
    }

}
