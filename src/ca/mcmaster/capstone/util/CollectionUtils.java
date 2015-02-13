package ca.mcmaster.capstone.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import lombok.NonNull;

public class CollectionUtils {

    public static <T> Collection<T> filter(@NonNull final Iterable<? extends T> iterable, @NonNull final Predicate<T> predicate) {
        final List<T> results = new ArrayList<>();
        for (final T t : iterable) {
            if (predicate.apply(t)) {
                results.add(t);
            }
        }
        return results;
    }

    public static <T> void each(@NonNull final Collection<? extends T> collection, @NonNull final Consumer<T> consumer) {
        for (final T t : collection) {
            consumer.consume(t);
        }
    }

    //TODO: implement map

}
