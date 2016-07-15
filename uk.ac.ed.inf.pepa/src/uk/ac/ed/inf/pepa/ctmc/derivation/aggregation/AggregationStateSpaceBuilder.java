/**
 * 
 */
package uk.ac.ed.inf.pepa.ctmc.derivation.aggregation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import uk.ac.ed.inf.pepa.DoNothingMonitor;
import uk.ac.ed.inf.pepa.IProgressMonitor;
import uk.ac.ed.inf.pepa.ctmc.derivation.DerivationException;
import uk.ac.ed.inf.pepa.ctmc.derivation.IStateSpace;
import uk.ac.ed.inf.pepa.ctmc.derivation.IStateSpaceBuilder;
import uk.ac.ed.inf.pepa.ctmc.derivation.MeasurementData;
import uk.ac.ed.inf.pepa.ctmc.derivation.aggregation.internal.ArraysLtsModel;
import uk.ac.ed.inf.pepa.ctmc.derivation.aggregation.internal.LtsModel;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.DoubleArray;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.IStateExplorer;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.ISymbolGenerator;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.IntegerArray;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.MemoryCallback;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.OptimisedHashMap;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.State;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.Transition;
import uk.ac.ed.inf.pepa.ctmc.derivation.internal.hbf.MemoryStateSpace;
import uk.ac.ed.inf.pepa.ctmc.derivation.common.OptimisedHashMap.InsertionResult;
import uk.ac.ed.inf.pepa.model.NamedRate;
import uk.ac.ed.inf.pepa.model.RateMath;
import uk.ac.ed.inf.pepa.model.internal.NamedRateImpl;
import uk.ac.ed.inf.pepa.parsing.AggregationNode;
import uk.ac.ed.inf.pepa.parsing.ConstantProcessNode;
import uk.ac.ed.inf.pepa.parsing.CooperationNode;
import uk.ac.ed.inf.pepa.parsing.DefaultVisitor;
import uk.ac.ed.inf.pepa.parsing.HidingNode;
import uk.ac.ed.inf.pepa.parsing.ModelNode;
import uk.ac.ed.inf.pepa.parsing.PrefixNode;
import uk.ac.ed.inf.pepa.parsing.ProcessNode;
import uk.ac.ed.inf.pepa.parsing.RateDoubleNode;
import uk.ac.ed.inf.pepa.parsing.WildcardCooperationNode;

/**
 * Computes the aggregated state space.
 * 
 * Note: in order to use this builder you must first create a StateSpaceBuilder
 * and pass the explorer and symbol generator from that builder to this one.
 * @author Giacomo Alzetta
 *
 */
public class AggregationStateSpaceBuilder implements IStateSpaceBuilder {
	
	private final ISymbolGenerator generator;
	private IStateExplorer explorer;
	
	private AggregationAlgorithm<Integer> algorithm;
	
	private final static int REFRESH_MONITOR = 20000;
	
	private final boolean useArraysModel = false;
	

	public AggregationStateSpaceBuilder(
			IStateExplorer explorer,
			ISymbolGenerator generator,
			AggregationAlgorithm<Integer> algorithm) {
		this.explorer = explorer;
		this.generator = generator;
		this.algorithm = algorithm;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ed.inf.pepa.ctmc.derivation.IStateSpaceBuilder#derive(boolean, uk.ac.ed.inf.pepa.IProgressMonitor)
	 */
	@Override
	public IStateSpace derive(boolean allowPassiveRates, IProgressMonitor monitor)
			throws DerivationException {
		
		if (monitor == null) 
			monitor = new DoNothingMonitor();
		
		
		monitor.beginTask(IProgressMonitor.UNKNOWN);
		ArrayList<State> states = new ArrayList<>(1000);
		LabelledTransitionSystem<Integer> lts;
		
		{
			// TODO: we should use a custom callback here...
			MemoryCallback callback = new MemoryCallback();
			
			
			if (!exploreStateSpace(monitor, callback, states)) {
				return null;
			}

			IntegerArray row = callback.getRow();
			IntegerArray col = callback.getColumn();
			DoubleArray rates = callback.getRates();
			IntegerArray actionIds = callback.getActions();
			
			lts = deriveLts(states, row, col, rates, actionIds);
		}
		
		System.out.println("Derived an initial LTS with: " + (lts.size()) + " states and "
						   + lts.numberOfTransitions() + " transitions.");
		
		// Aggregate the LTS here
		LabelledTransitionSystem<Aggregated<Integer>> aggrLts = algorithm.aggregate(lts);
		
		System.out.println("Obtained an aggregated LTS with: " + aggrLts.size() + " states and "
						   + aggrLts.numberOfTransitions() + " transitions");
		/*
		for (Aggregated<Integer> aggrS: aggrLts) {
			System.out.println("One aggregated state contains: ");
			for (Integer x : aggrS) {
				System.out.println(x.toString());
			}
		} */
		
		IStateSpace result = createStateSpace(states, aggrLts);
		monitor.done();
		
		return result;
	}

	/**
	 * @param states
	 * @param aggrLts
	 * @return
	 */
	private IStateSpace createStateSpace(ArrayList<State> states,
			LabelledTransitionSystem<Aggregated<Integer>> aggrLts) {
		ArrayList<Aggregated<Integer>> newStatesToRepr = new ArrayList<>(aggrLts.size());
		ArrayList<Integer> reprToNewStates = new ArrayList<>(states.size());
		
		for (int i=0; i < states.size(); i++) {
			reprToNewStates.add(-1);
		}
		
		int i=0;
		for (Aggregated<Integer> s: aggrLts) {
			newStatesToRepr.add(s);
			reprToNewStates.set(s.getRepresentative(), i);
			++i;
			
		}
		
		IntegerArray newRow = new IntegerArray(aggrLts.numberOfStates());
		IntegerArray newCol = new IntegerArray(2*aggrLts.numberOfTransitions());
		IntegerArray newActions = new IntegerArray(aggrLts.numberOfTransitions());
		DoubleArray newRates = new DoubleArray(aggrLts.numberOfTransitions());
		
		int maxSize = 0;
		boolean hasVariableSize = false;
		
		
		for (Aggregated<Integer> s: newStatesToRepr) {
			for (Aggregated<Integer> target: aggrLts.getImage(s)) {
				for(short actionId: aggrLts.getActions(s, target)) {
					double rate = aggrLts.getApparentRate(s, target, actionId);
					newCol.add(reprToNewStates.get(target.getRepresentative()));
					newCol.add(newActions.size());
					newActions.add(actionId);
					newRates.add(rate);
				}
			}
		}
		

		ArrayList<State> newStates = new ArrayList<>(aggrLts.size());
		
		// FIXME: this is checked only on representatives.
		// it may be enough, but we have to check that.
		
		for (Aggregated<Integer> state: newStatesToRepr) {
			State s = states.get(state.getRepresentative());
			newStates.add(s);
			if (s.fState.length > maxSize) {
				int oldMaxSize = maxSize;
				maxSize = s.fState.length;
				if (oldMaxSize != 0 && maxSize != oldMaxSize) {
					hasVariableSize = true;
				}
			}
		}
		
		// Derive the CTMC here
		IStateSpace result = new MemoryStateSpace(
				generator,
				newStates,
				newRow,
				newCol,
				newActions,
				newRates,
				hasVariableSize,
				maxSize);
		return result;
	}

	/**
	 * @param monitor
	 * @param callback
	 * @param states
	 * @throws DerivationException
	 */
	private boolean exploreStateSpace(IProgressMonitor monitor, MemoryCallback callback, ArrayList<State> states)
			throws DerivationException {
		OptimisedHashMap map = new OptimisedHashMap();
		Queue<State> queue = new LinkedList<State>();
		
		short[] initialState = generator.getInitialState();
		int hashCode = Arrays.hashCode(initialState);
		
		State initState = map.putIfNotPresentUnsync(initialState, hashCode).state;
		queue.add(initState);
		Transition[] found;
		
		while (!queue.isEmpty()) {
			if (monitor.isCanceled()) {
				monitor.done();
				return false;
			}
			
			State s = queue.remove();
			
			if (s.stateNumber % REFRESH_MONITOR == 0) {
				monitor.worked(REFRESH_MONITOR);
			}
			
			try {
				found = explorer.exploreState(s.fState);
				
			} catch (DerivationException e) {
				throw createException(s, e.getMessage());
			}
			
			if (found.length == 0) {
				monitor.done();
				throw createException(s, "Deadlock found.");
			}
			
			for (Transition t: found) {
				if (t.fRate <= 0) {
					throw createException(s,
							"Incomplete model with respect to action: "
									+ generator.getActionLabel(t.fActionId)
									+ ". ");
				}
				
				hashCode = Arrays.hashCode(t.fTargetProcess);
				InsertionResult result = map.putIfNotPresentUnsync(
						t.fTargetProcess,
						hashCode
				);
				//tocAdded += System.nanoTime() - ticMap;
				
				t.fState = result.state;
				
				if (!result.wasPresent) {
					queue.add(result.state);
					//result.state.stateNumber = rowNumber++;
				}
			}
			
			callback.foundDerivatives(s, found);
			states.add(s);
		}
		
		explorer.dispose();
		states.trimToSize();
		callback.done(generator, states);
		
		return true;
	}

	/**
	 * Derive the LTS model from the lists of states, and transitions produced
	 * by the StateExplorerBuilder and the MemoryCallback.
	 * 
	 * @param states
	 * @param row
	 * @param col
	 * @param rates
	 * @param actionIds
	 * @return
	 */
	private LabelledTransitionSystem<Integer> deriveLts(ArrayList<State> states,
			IntegerArray row, IntegerArray col, DoubleArray rates,
			IntegerArray actionIds) {
		
		int numActIds = 0;
		{
			HashSet<Integer> actIds = new HashSet<>();
			for (int i=0; i < actionIds.size(); ++i) {
				actIds.add(actionIds.get(i));
			}
			
			numActIds = actIds.size();
			
		}

		LabelledTransitionSystem<Integer> lts;
		
		if (useArraysModel) {
			lts = new ArraysLtsModel(numActIds, row, col, actionIds, rates);
		} else {
			lts = new LtsModel<>(numActIds);
		
			// Add all states into the LTS.
			for (State s: states) {
				lts.addState(s.stateNumber);
			}
			
			int i = 0;
			for (State s: states) {
				// The i-th position in row contains the index t inside the
				// col array that contains the transitions from the state s.
				int rangeStart = row.get(i);
				int rangeEnd = (i == row.size()-1 ? col.size() : row.get(i+1));
				for (int j=rangeStart; j < rangeEnd; j += 2) {
					// The j-th position inside col contains the state number
					// of the target node in the transition. The index j+1
					// contains the starting index in the rates and actionIds
					// arrays that refer to transitions between state s
					// and state target.
					int targetId = col.get(j);
					int colRangeStart = col.get(j+1);
					int colRangeEnd  = (j < col.size() - 3 ? col.get(j+3): rates.size());
					
					// For each these transitions from state s to target
					// and for each label, we add these to the Lts.
					for (int k=colRangeStart; k < colRangeEnd; k++) {
						double rate = rates.get(k);
						short actionId = (short)actionIds.get(k);
						lts.addTransition(s.stateNumber, targetId, rate, actionId);
					}
				}
				
				
				++i;
			}
		}
		
		return lts;
	}
	
	private DerivationException createException(State state, String message) {
		StringBuilder buf = new StringBuilder();
		buf.append(message);
		buf.append(" State number: ");
		buf.append(String.valueOf(state.stateNumber));
		buf.append(". ");
		buf.append("State: ");
		for (int i = 0; i < state.fState.length; i++) {
			buf.append(generator.getProcessLabel(state.fState[i]));
			if (i != state.fState.length - 1)
				buf.append(",");
		}
		return new DerivationException(buf.toString());
	}

	/* (non-Javadoc)
	 * @see uk.ac.ed.inf.pepa.ctmc.derivation.IStateSpaceBuilder#getMeasurementData()
	 */
	@Override
	public MeasurementData getMeasurementData() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
