/**
 * 
 */
package uk.ac.ed.inf.pepa.aggregation.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import uk.ac.ed.inf.pepa.aggregation.PartitionBlock;
import uk.ac.ed.inf.pepa.aggregation.StateIsMarkedException;
import uk.ac.ed.inf.pepa.aggregation.StateNotFoundException;
import uk.ac.ed.inf.pepa.model.Rate;
import uk.ac.ed.inf.pepa.model.RateMath;

/**
 * @author Giacomo Alzetta
 * @param <T> The type of the states in the block.
 * @param <B> The type of the values associated with states in the block.
 *
 */
public class LinkedPartitionBlock<T, V extends Rate> implements PartitionBlock<T, V> {

	private LinkedList<T> nonMarkedStates;
	private LinkedList<T> markedStates;
	
	private HashMap<T, V> mapToValues;
	
	public LinkedPartitionBlock() {
		nonMarkedStates = new LinkedList<>();
	}
	
	public LinkedPartitionBlock(LinkedList<T> states) {
		nonMarkedStates = states;
	}
	
	@Override
	public void addState(T state) {
		nonMarkedStates.add(state);
		
	}

	@Override
	public boolean isEmpty() {
		return nonMarkedStates.isEmpty() && markedStates.isEmpty();
	}

	@Override
	public Iterator<T> getStates() {
		Iterator<T> iterator = new Iterator<T>() {
			private Iterator<T> it1 = nonMarkedStates.iterator();
			private Iterator<T> it2 = markedStates.iterator();
			
			public boolean hasNext() {
				return it1.hasNext() || it2.hasNext();
			}
			
			public T next() {
				if (it1.hasNext()) {
					return it1.next();
				} else {
					return it2.next();
				}
			}
			
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
		
		return iterator;
		
	}

	@Override
	public Iterator<T> getMarkedStates() {
		return new Iterator<T>() {
			Iterator<T> it = markedStates.iterator();
			
			public boolean hasNext() {
				return it.hasNext();
			}
			
			public T next() {
				return it.next();
			}
			
			public void remove() {
				throw new UnsupportedOperationException();
			}		
		};
	}

	@Override
	public PartitionBlock<T, V> splitMarkedStates() {
		PartitionBlock<T, V> marked = new LinkedPartitionBlock<>(markedStates);
		this.markedStates = new LinkedList<>();
		return marked;
	}

	@Override
	public Iterator<PartitionBlock<T, V>> splitBlock() {
		ArrayList<V> values = new ArrayList<>(mapToValues.values());
		V pmc = PartitioningUtils.pmc(values);
		HashMap<T, V> mappingNotPmc = new HashMap<>(mapToValues);
		HashMap<T, V> mappingOfPmc = PartitioningUtils.splitMapOnValue(mappingNotPmc, pmc);
		
		PartitionBlock<T, V> pmcBlock = new LinkedPartitionBlock<T, V>();
		for (Map.Entry<T, V> entry: mappingOfPmc.entrySet()) {
			pmcBlock.addState(entry.getKey());
		}
		
		ArrayList<Map.Entry<T, V>> entries = new ArrayList<>(mappingNotPmc.entrySet());
		entries.sort(new Comparator<Map.Entry<T, V>>() {
			
			@Override
			public int compare(Map.Entry<T, V> e1, Map.Entry<T, V> e2) {
				V v1 = e1.getValue();
				V v2 = e2.getValue();
				
				// TODO: check that this comparison is sound.
				if (e1.getValue().equals(e2.getValue())) {
					return 0;
				} else {
					// in particular here
					return RateMath.min(v1, v2).equals(v1) ? -1 : 1;
				}
			}
		});
		
		HashMap<V, PartitionBlock<T, V>> blocks = new HashMap<>();
		blocks.put(pmc, pmcBlock);
		
		for (Map.Entry<T, V> entry : entries) {
			V val = entry.getValue();
			if (!blocks.containsKey(val)) {
				blocks.put(val, new LinkedPartitionBlock<>());
			}
			blocks.get(val).addState(entry.getKey());
		}
		
		// TODO: We do not want to allow modifications...
		return blocks.values().iterator();
	}

	@Override
	public void markState(T state) throws StateNotFoundException {
		boolean found = false;
		for (int i=0; i < nonMarkedStates.size(); i++) {
			if (nonMarkedStates.get(i).equals(state)) {
				nonMarkedStates.remove(i);
				markedStates.add(state);
				found = true;
				break;
			}
		}
		
		if (!found) {
			throw new StateNotFoundException("Could not find the state: " + state.toString() + " in block.");
		}
		
	}

	@Override
	public void setValue(T state, V value) throws StateNotFoundException, StateIsMarkedException {
		if (value == null) {
			throw new NullPointerException("You cannot assign a null value to a state!");
		}
		
		if (nonMarkedStates.contains(state)) {
			mapToValues.put(state, value);
		} else if (markedStates.contains(state)) {
			throw new StateIsMarkedException("State: " + state.toString() + " is marked.");
		} else {
			throw new StateNotFoundException("State: " + state.toString() + " could not be found in the block.");
		}
	}

	@Override
	public V getValue(T state) throws StateNotFoundException, StateIsMarkedException {
		V val = mapToValues.get(state);
		if (val == null) {
			if (mapToValues.containsKey(state)) {
				throw new RuntimeException("Impossible has happened: a state mapped to null.");
			} else if (nonMarkedStates.contains(state)) {
				throw new StateIsMarkedException("State: " + state.toString() + " is marked.");
			} else {
				throw new StateNotFoundException("State: " + state.toString() + " could not be found in the block.");
			}
		}
		return val;
	}

	@Override
	public boolean isMarked(T state) throws StateNotFoundException {
		for (T s: markedStates) {
			if (state.equals(s)) {
				return true;
			}
		}
		
		for (T s: nonMarkedStates) {
			if (state.equals(s)) {
				return false;
			}
		}
		
		throw new StateNotFoundException("The state: " + state.toString() + " could not be found.");
	}

}
