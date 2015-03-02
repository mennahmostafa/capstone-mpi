package ca.mcmaster.capstone.monitoralgorithm.tree;

import java.io.Serializable;

import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@AllArgsConstructor @ToString @Getter @EqualsAndHashCode
public abstract class LeafNode<V, T> implements Node<T>,Serializable {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@NonNull @Getter V value;
}
