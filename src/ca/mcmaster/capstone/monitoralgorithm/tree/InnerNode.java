package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Operator;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/* An inner node */
@AllArgsConstructor @ToString @Getter @EqualsAndHashCode
public abstract class InnerNode<V, T> implements Node<V> {
    @NonNull Node<T> left;
    @NonNull Node<T> right;
    @NonNull Operator<T, V> op;
}
