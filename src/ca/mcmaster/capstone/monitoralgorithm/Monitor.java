package ca.mcmaster.capstone.monitoralgorithm;
import mpi.*;
//import android.app.Service;
//import android.content.Intent;
//import android.os.IBinder;
//import android.util.Log;
import ca.mcmaster.capstone.logger.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService; 
import java.util.concurrent.Future;

import ca.mcmaster.capstone.initializer.InitialState; 
import ca.mcmaster.capstone.initializer.AutomatonInitializer;
import ca.mcmaster.capstone.util.CollectionUtils;
import ca.mcmaster.capstone.util.MessageTags;
//import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.NonNull;

/* Class to hold the main algorithm code.*/
// TODO: refactor Service components out into MonitorService to separate from actual Monitor logic
public class Monitor// extends Service 
{

	public final static String LOG_TAG = "monitor";


	final int  maxMessageSize=1024*5;
	final int maxTokenArraySize=1024*20;
	private final Map<Integer, Event> history = new HashMap<>();
	private final Set<Token> waitingTokens = new LinkedHashSet<>();
	private Integer monitorID = null;
	private Integer rank= null;
	private static Integer size = null;
	private final Set<GlobalView> GV = new HashSet<>();
	//	private ExecutorService workQueue;
	//	private Future<?> monitorJob = null;
	//	private Future<?> tokenPollJob = null;
	//	private Future<?> eventPollJob = null;
	private final Automaton automaton = Automaton.INSTANCE;

	/* Class to abstract the bulk sending of tokens. */
	private static class TokenSender {
		private static final List<Token> tokensToSendOut = new ArrayList<>();

		public static  List<Token> getTokensToSendOut() {
			return new ArrayList<>(tokensToSendOut);
		}

		private static  void bulkTokenSendOut(final List<Token> tokens) {
			Log.d(LOG_TAG, "Queued the following tokens to send out: " + tokens.toString());
			tokensToSendOut.addAll(tokens);
		}

		private static  void sendTokenOut(final Token token) {
			Log.d(LOG_TAG, "Queued the following token to send out." + token.toString());
			tokensToSendOut.add(token);
		}

		private static  void sendTokenHome(final Token token) {
			Log.d(LOG_TAG, "Queued the following token to send home: " + token.toString());
			token.setDestination(token.getOwner());
			tokensToSendOut.add(token);
		}

		private static  void bulkSendTokens() throws ClassNotFoundException, IOException, MPIException {
			while(tokensToSendOut.size()!=0)
			{
				ArrayList<Token> tokensToSameDestination=new ArrayList<Token>();
				int destination=tokensToSendOut.get(0).getDestination();
				for (Iterator<Token> it = tokensToSendOut.iterator(); it.hasNext(); ) 
				{

					final Token token = it.next();
					if (token.getDestination()==destination)
					{
						tokensToSameDestination.add(token);
						//it.remove();
					}
				}
				for(Token t :tokensToSameDestination)
				{
					tokensToSendOut.remove(t);
				}
				sendTokens(tokensToSameDestination, destination);
			}
		}
	}

	public Monitor(int rank,int processes_count) {

		this.rank=rank;
		this.monitorID=this.rank+1;
		size=processes_count;
		System.out.print("I am monitor M"+this.rank+"\n");
		Log.fileName="M"+rank+"_";
	}


	/*
	 * Perform some basic initialization.
	 *
	 * @param initialStates The initial state of each known process.
	 */
	private void init() {
		Log.d(LOG_TAG, "Initializing monitor");
		Map<String, Integer> virtualIdentifiers=new HashMap<String, Integer>();
		for(int i=1;i<=size;i++)
		{
			virtualIdentifiers.put("x"+i, i);
		}
		AutomatonInitializer automatonInitializer=new  ca.mcmaster.capstone.initializer.AutomatonInitializer();
		automaton.processAutomatonFile(automatonInitializer.getAutomatonFile(),
				automatonInitializer.getConjunctMap(),
				virtualIdentifiers);

		//        //FIXME: This is pretty messy. We can probably do better given some time to think.
		List<InitialState.ValuationDummy> valuationDummies=automatonInitializer.getInitialState().getValuations();

		Map<Integer, Integer> initialVectorClock = new HashMap<>();
		Map<Integer, Valuation> valuations = new HashMap<>();
		int idx=1;
		for (InitialState.ValuationDummy valuation : valuationDummies) {
			Map<String, Double> val = new HashMap<>();
			for (InitialState.Variable variable : valuation.getVariables()) {
				val.put(variable.getVariable(), Double.parseDouble(variable.getValue()));
			} 

			initialVectorClock.put(idx, 0);
			valuations.put(idx, new Valuation(val));
			idx+=1;

		}

		VectorClock vectorClock = new VectorClock(initialVectorClock);
		final Map<Integer, ProcessState> initialStates = new HashMap<>();
		for (Map.Entry<Integer, Valuation> entry : valuations.entrySet()) {
			initialStates.put(entry.getKey(), new ProcessState(entry.getKey(), entry.getValue(), vectorClock));
		}

		final GlobalView initialGV = new GlobalView();
		initialGV.setCurrentState(automaton.getInitialState());
		initialGV.setStates(initialStates);

		initialGV.setCurrentState(automaton.advance(initialGV));
		initialGV.setCut(vectorClock);
		synchronized (GV) {
			GV.add(initialGV);
		}
		Log.d(LOG_TAG, "Finished initializing monitor");
	}

	/*
	 * The main loop of the algorithm.
	 *
	 * @param initialStates The initial state of each known process.
	 */
	public void monitorLoop() throws ClassNotFoundException, MPIException, IOException, InterruptedException {
		init();

		while(true){
			pollEvents();
			pollTokens();
			boolean lastEvent=pollTerminatingProgram();
			if(lastEvent)
			{
				Log.v(LOG_TAG,"Terminating, last event");
				finalizeMonitor();
				break;
			}
			//Thread.sleep(10);
		}
	}
	private void finalizeMonitor()
	{
		//TODO: cleanup.

	}
	private boolean pollTerminatingProgram() throws MPIException, ClassNotFoundException, IOException {
		Status status=MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MessageTags.ProgramTerminating.ordinal());
		if(status!=null && !status.isCancelled())
		{

			Log.d(LOG_TAG, "pollTerminatingProgram: attempting read event");
			Event lastEvent=receiveProgramTerminatingMessage();
			if(lastEvent!=null)
			{
				receiveEvent(lastEvent);
				return true;
			}
		}
		return false;
	} 
	private Event receiveProgramTerminatingMessage() throws MPIException, IOException, ClassNotFoundException
	{

		try
		{

			Log.d(LOG_TAG, "Entering : receiveProgramTerminatingMessage");
			byte[] message = new byte[maxMessageSize];
			MPI.COMM_WORLD.recv(message, maxMessageSize, MPI.BYTE,MPI.ANY_SOURCE,MessageTags.ProgramTerminating.ordinal());
			Object o = CollectionUtils.deserializeObject(message);
			Event event= (Event)o;
			Log.d(LOG_TAG, "Exiting : receiveProgramTerminatingMessage");
			return event;
		}
		catch(Exception e)
		{
			e.printStackTrace();


		}
		return null;


	}
	private void pollTokens() throws MPIException, ClassNotFoundException, IOException {
		Status status=MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MessageTags.Token.ordinal());
		if(status!=null && !status.isCancelled())
		{

			Log.d(LOG_TAG, "pollTokens: attempting receiveTokenMessage");
			final ArrayList<Token> tokens = receiveTokenMessage();
			if (tokens != null) {
				Log.v(LOG_TAG, "pollTokens: receiveTokenMessage returned :"+tokens.size()+" tokens");
				for(Token token : tokens)
				{
					receiveToken(token);
				}
			}
		}
		TokenSender.bulkSendTokens();

	}
	private ArrayList<Token> receiveTokenMessage() throws MPIException, IOException, ClassNotFoundException
	{

		Log.d(LOG_TAG, "Entering: receiveTokenMessage");
		byte[] message = new byte[maxTokenArraySize];

		MPI.COMM_WORLD.recv(message, maxTokenArraySize, MPI.BYTE,MPI.ANY_SOURCE,MessageTags.Token.ordinal());


		Object o = CollectionUtils.deserializeObject(message);
		ArrayList<Token> tokens = (ArrayList<Token>)o;
		return tokens;



	}
	private void pollEvents() throws MPIException, ClassNotFoundException, IOException {
		Status status=MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MessageTags.Event.ordinal());
		if(status!=null && !status.isCancelled())
		{

			Log.v(LOG_TAG, "pollEvents: attempting read event");
			final Event event = read();
			if (event != null) {
				Log.d(LOG_TAG, "pollEvents: read returned an event ");
				receiveEvent(event);
			}
		}
	} 


	private Event read() throws ClassNotFoundException, IOException, MPIException {


		byte[] message = new byte[maxMessageSize];
		Log.v(LOG_TAG, "Entering read event");

		Status status=MPI.COMM_WORLD.recv(message, maxMessageSize, MPI.BYTE,MPI.ANY_SOURCE,MessageTags.Event.ordinal());

		Log.v(LOG_TAG, "status.message size: "+ status.getCount(MPI.BYTE));
		Object o = CollectionUtils.deserializeObject(message);
		Event event = (Event)o;
		Log.v(LOG_TAG, "exiting read event");
		return event;


	}

	/*
	 * Process local events for global views who have no pending events waiting to be processed.
	 *
	 * @param event The event to be processed.
	 */
	public void receiveEvent(@NonNull final Event event) throws ClassNotFoundException, IOException, MPIException {
		Log.v(LOG_TAG, "Entering receiveEvent. Event: " + event.toString());
		synchronized (history) {
			history.put(event.getEid(), event);
		}
		processWaitingTokens(event);
		synchronized (GV) {
			final Set<GlobalView> copyGV = new HashSet<>(GV);
			GV.clear();
			GV.addAll(mergeSimilarGlobalViews(copyGV));
			for (final GlobalView gv : GV) {
				Log.d(LOG_TAG, "globalView inside receiveEvent: " + gv.toString());
				gv.getPendingEvents().add(event);
				if (gv.getTokens().isEmpty()) {
					processEvent(gv, gv.getPendingEvents().remove());
				}
			}
		}
		TokenSender.bulkSendTokens();
		Log.v(LOG_TAG, "Exiting receiveEvent.");
	}
	private void processWaitingTokens(@NonNull final Event event)
	{
		synchronized (waitingTokens) {
			final Set<Token> tokensToProcess = Collections.unmodifiableSet(new HashSet<>(waitingTokens));
			waitingTokens.clear();
			for (final Token token : tokensToProcess) {
				if (token.getTargetEventId() == event.getEid()) {
					processToken(token, event);
				}
			}
		}
	}
	private static int getProcessMonitorID(int processID)
	{
		return ((processID-1)+size);
	}
	private static void sendToken(@NonNull final Token token, @NonNull final Integer pid)throws ClassNotFoundException, IOException, MPIException {

		byte[] tokenBytes = CollectionUtils.serializeObject(token);
		java.nio.ByteBuffer out = MPI.newByteBuffer(tokenBytes.length);

		for(int i = 0; i < tokenBytes.length; i++){
			out.put(i, tokenBytes[i]);
		}
		if (pid.equals(token.getOwner())) {
			Log.d(LOG_TAG, "Sending a token back home. To: " + pid.toString());
		} else {
			Log.d(LOG_TAG, "Sending a token to: P" + pid.toString());
		}
		MPI.COMM_WORLD.iSend(MPI.slice(out,0), tokenBytes.length, MPI.BYTE, getProcessMonitorID(pid.intValue()), MessageTags.Token.ordinal());


	}
	private static void sendTokens(@NonNull final ArrayList<Token> tokens, @NonNull final Integer pid)throws ClassNotFoundException, IOException, MPIException {

		byte[] tokenBytes = CollectionUtils.serializeObject(tokens);
		java.nio.ByteBuffer out = MPI.newByteBuffer(tokenBytes.length);

		for(int i = 0; i < tokenBytes.length; i++){
			out.put(i, tokenBytes[i]);
		}

		Request request=MPI.COMM_WORLD.iSend(MPI.slice(out,0), tokenBytes.length, MPI.BYTE, getProcessMonitorID(pid.intValue()), MessageTags.Token.ordinal());
		request.waitFor();

	}
	private Set<GlobalView> mergeSimilarGlobalViews(@NonNull final Collection<GlobalView> gv) {
		Log.d(LOG_TAG, "Entering mergeSimilarGlobalViews with " + gv.size() + " globalViews.");
		final Set<GlobalView> merged = new HashSet<>();
		for (GlobalView gv1 : gv) {
			for (GlobalView gv2 : gv) {
				if (!gv1.equals(gv2)) {
					final GlobalView newGV = gv1.merge(gv2);
					if (newGV != null) {	
						merged.add(newGV);
					}
				}
			}
			merged.add(gv1);
		}
		Log.d(LOG_TAG, "Leaving mergeSimilarGlobalViews with " + merged.size() + " globalViews.");
		return merged;
	}

	/*
	 * Compute the next state of the monitor automaton. Depending on the information needed to
	 * evaluate the transitions of the monitor automaton this may be done locally or there may be a
	 * need to consult with another process.
	 *
	 * @param gv The global view to compute the next monitor state for.
	 * @param event The event to be evaluated.
	 */
	public void processEvent(@NonNull final GlobalView gv, @NonNull final Event event) throws MPIException, ClassNotFoundException, IOException {
		Log.d(LOG_TAG, "Entering processEvent, Event: " + event.toString() +"gv.cut: "+gv.getCut());
		//processWaitingTokens(event);
		gv.updateWithEvent(event);
		gv.setCurrentState(automaton.advance(gv));
		handleMonitorStateChange(gv);
		checkOutgoingTransitions(gv, event);
		Log.d(LOG_TAG, "Exiting processEvent");
	}

	private void handleMonitorStateChange(@NonNull final GlobalView gv) throws MPIException, ClassNotFoundException, IOException {
		final Automaton.Evaluation state = gv.getCurrentState().getStateType();
		char[] msg;
		switch (state) {
		case SATISFIED:
			msg=new char[1];
			msg[0]='S';
			MPI.COMM_WORLD.send(msg, 1, MPI.CHAR, this.rank, MessageTags.ProgramTerminating.ordinal());
			break;
		case VIOLATED:
			msg=new char[1];
			msg[0]='V';
			MPI.COMM_WORLD.send(msg, 1, MPI.CHAR, this.rank, MessageTags.ProgramTerminating.ordinal());
			break;
		default:
			return;
		}
		Log.d(LOG_TAG, "Monitor state changed! " + state + " in state " + gv.getCurrentState().getStateName());

		// Send all waiting tokens home with the local state
		synchronized (waitingTokens) {
			for (Token token : waitingTokens) {
				TokenSender.sendTokenHome(new Token.Builder(token).cut(token.getCut().merge(gv.getCut()))
						.targetProcessState(gv.getStates().get(monitorID)).build());
			}
			waitingTokens.clear();
		}
		TokenSender.bulkSendTokens();

		//		workQueue.shutdownNow();
		//		monitorJob.cancel(false);
		//		tokenPollJob.cancel(false);
		//		eventPollJob.cancel(false);
	}

	/*
	 * Identifies events in gv that are concurrent with event that can enable out going transitions
	 * from the current state of the monitor automaton.
	 *
	 * @param gv The global view to use for finding concurrent events.
	 * @param event The event to find concurrent events for.
	 */
	private void checkOutgoingTransitions(@NonNull final GlobalView gv, @NonNull final Event event) {
		Log.d(LOG_TAG, "Entering checkOutgoingTransitions");
		final Map<Integer, Set<AutomatonTransition>> consult = new HashMap<>();
		for (final AutomatonTransition trans : automaton.getTransitions()) {
			final Set<Conjunct> forbiddingConjuncts = new HashSet<>();

			final AutomatonState current = gv.getCurrentState();
			if (trans.getFrom().equals(current) && !trans.getTo().equals(current)) {
				final Set<Integer> participating = trans.getParticipatingProcesses();
				forbiddingConjuncts.addAll(trans.getForbiddingConjuncts(gv));
				final Set<Integer> forbidding = new HashSet<>();
				// Extract Integers for forbidding processes from the set of forbidding conjuncts
				for (final Conjunct conjunct : forbiddingConjuncts) {
					forbidding.add(conjunct.getOwnerProcess());
				}
				if (!forbidding.contains(monitorID)) {
					final Set<Integer> inconsistent = gv.getInconsistentProcesses(monitorID);
					// intersection
					participating.retainAll(inconsistent);
					// union
					forbidding.addAll(participating);
					for (final Integer process : forbidding) {
						gv.addPendingTransition(trans);
						if (consult.get(process) == null) {
							consult.put(process, new HashSet<AutomatonTransition>());
						}
						consult.get(process).add(trans);
					}
				}
			}
		}

		//final List<Token> pendingSend = TokenSender.getTokensToSendOut();
		final List<Token> pendingSend = new ArrayList<Token>();
		for (final Map.Entry<Integer, Set<AutomatonTransition>> entry : consult.entrySet()) 
		{
			final Integer destination = entry.getKey();
			Log.d(LOG_TAG, "Need to send a token to: " + destination + "\n    to gather information about these transitions: " + entry.getValue());
			Token.Builder builder = new Token.Builder(monitorID, destination);
//			for (Iterator<Token> it = pendingSend.iterator(); it.hasNext(); )
//			{
//				final Token token = it.next();
//				VectorClock.Comparison comparison = token.getCut().compareToClock(event.getVC());
//				if (new Integer(token.getDestination()).equals(destination)
//						&& comparison == VectorClock.Comparison.EQUAL
//						&& token.getTargetEventId() == gv.getCut().process(destination) + 1) {
//					//Modify one of the pending tokens
//					builder = new Token.Builder(token);
//					it.remove();
//					break;
//				}
//			}

			// Get all the conjuncts for process j
			final Set<Conjunct> conjuncts = new HashSet<>();
			for (AutomatonTransition transition : entry.getValue()) {
				for (Conjunct conjunct : transition.getConjuncts()) {
					if (conjunct.getOwnerProcess().equals(destination)) {
						conjuncts.add(conjunct);
					}
				}
			}

			//Build map to add to token
			final Map<Conjunct, Conjunct.Evaluation> forToken = new HashMap<>();
			for (final Conjunct conjunct : conjuncts) {
				forToken.put(conjunct, Conjunct.Evaluation.NONE);
			}
			final Token token = builder.targetEventId(gv.getCut().process(destination) + 1)
					.cut(event.getVC()).conjuncts(forToken).automatonTransitions(entry.getValue())
					.build();
			pendingSend.add(token);
		}
		gv.addTokens(new ArrayList<Token>(pendingSend));
		TokenSender.bulkTokenSendOut(pendingSend);
		Log.d(LOG_TAG, "Exiting checkOutgoingTransitions");
	}

	/*
	 * The method does two things:
	 *     1) enable or disable automaton transitions based on the received token
	 *     2) evaluate the transition that the received token is requesting
	 *
	 * @param token The token being received.
	 */
	public void receiveToken(@NonNull final Token token) throws MPIException, ClassNotFoundException, IOException {
		Log.d(LOG_TAG, "Entering receiveToken. Token from: " + token.getOwner());
		if (new Integer(token.getOwner()).equals(monitorID)) {
			final List<GlobalView> globalViews = getGlobalView(token);
			for (final GlobalView globalView : globalViews) {
				globalView.updateWithToken(token);
				boolean hasEnabled = false;
				for (final AutomatonTransition trans : token.getAutomatonTransitions()) {
					Log.d(LOG_TAG, "Checking if transition is enabled: " + trans.toString());
					// Get other tokens for same transition
					final List<Token> tokens = globalView.getTokensForTransition(trans);
					boolean transitionCompleted=true;
					for (Token tok : tokens) {
						if (!tok.isReturned()) {
							Log.d(LOG_TAG, "Not all tokens for this transition have been returned. Could not find: " + tok);
							transitionCompleted=false;
							break;
						}
					}
					if(!transitionCompleted)
					{
						continue;
					}
					if (trans.enabled(globalView, tokens) && globalView.consistent(trans)) 
					{
						hasEnabled |= true;
						Log.v(LOG_TAG, "The transition is enabled and the global view is consistent.");
						globalView.reduceStateFromTokens(tokens);
						globalView.removePendingTransition(trans);
						final GlobalView gvn1 = new GlobalView(globalView);
						final GlobalView gvn2 = new GlobalView(globalView);
						gvn1.setCurrentState(trans.getTo());
						gvn2.setCurrentState(trans.getTo());
						gvn1.setTokens(new ArrayList<Token>());
						gvn2.setTokens(new ArrayList<Token>());
						synchronized (GV) {
							GV.add(gvn1);
							Log.d(LOG_TAG, "gvn1: " + gvn1.toString());
							GV.add(gvn2);
							Log.d(LOG_TAG, "gvn2: " + gvn1.toString());
						}
						handleMonitorStateChange(gvn1);
						if(gvn1.getPendingEvents().size()!=0)
							processEvent(gvn1, gvn1.getPendingEvents().remove());
						synchronized (history) {
							processEvent(gvn2, history.get(gvn2.getCut().process(monitorID)));
						}
					} 
					else 
					{
						Log.d("moonitor", "Removing a pending transition from the global view.");
						globalView.removePendingTransition(trans);
					}
				}
				if (globalView.getPendingTransitions().isEmpty()) {
					if (hasEnabled) {
						synchronized (GV) {
							Log.d(LOG_TAG, "Removing a global view.");
							GV.remove(globalView);
						}
					} else {
						globalView.setTokens(new ArrayList<Token>());
						while (!globalView.getPendingEvents().isEmpty()) {
							Log.d(LOG_TAG, "Processing pending event");
							processEvent(globalView, globalView.getPendingEvents().remove());
						}
					}
				} else {
					final Token maxConjuncts = globalView.getTokenWithMostConjuncts();
					if (maxConjuncts != null) {
						TokenSender.sendTokenOut(maxConjuncts);
					}
				}
			}
		} else {
			boolean hasTarget = false;
			synchronized (history) {
				for (final Event event : history.values()) {
					if (event.getEid() == token.getTargetEventId()) {
						processToken(token, event);
						hasTarget = true;
						break;
					}
				}
			}
			if (!hasTarget) {
				synchronized (waitingTokens) {
					Log.d(LOG_TAG, "Adding a token to waitingTokens.");
					waitingTokens.add(token);
				}
			}
		}
		
		Log.d(LOG_TAG, "Exiting receiveToken.");
	}
	/*
	 * Checks if the VectorClock of each ProcessState in gv is consistent with that of all other processes
	 * which are taking part in the transitoin. If there is a more up to date VectorClock for that
	 * process in on of the returned tokens, use that for the comparisson.
	 *
	 * @param gv     The GlobalView to check for consistency.
	 * @param trans  The AutomatonTransition which the considered processes must take part in.
	 * @return   true if all vector clock comparisons return EQUAL or CONCURRENT
	 */
	private boolean consistent(@NonNull final GlobalView gv, @NonNull final AutomatonTransition trans) {
		Set<Integer> participatingProcesses = trans.getParticipatingProcesses();
		Set<ProcessState> statesToCheck = new HashSet<>();

		// Filter the states for the ones needed for this transition and use the state from any tokens
		// that have returned from the processes in question instead of the old state.
		for (ProcessState state : gv.getStates().values()) {
			if (participatingProcesses.contains(state.getId())) {
				boolean useTokenState = false;
				for (Token token : gv.getTokens()) {
					if (token.isReturned() && new Integer(token.getDestination()).equals(state.getId())) {
						useTokenState = true;
						statesToCheck.add(token.getTargetProcessState());
					}
				}
				if (!useTokenState) {
					statesToCheck.add(state);
				}
			}
		}

		// Compare the vector clock of each state
		for (ProcessState state1 : statesToCheck) {
			for (ProcessState state2 : statesToCheck) {
				if (!state1.equals(state2)) {
					VectorClock.Comparison comp = state1.getVC().compareToClock(state2.getVC());
					if (comp != VectorClock.Comparison.CONCURRENT
							&& comp != VectorClock.Comparison.EQUAL) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/*
	 * Decide whether token should be returned to its owner. Token is updated with event.
	 *
	 * @param token The token to process.
	 * @param event The event to update token with.
	 */
	public void processToken(@NonNull final Token token, @NonNull final Event event) {
		Log.d(LOG_TAG, "Entering processToken.\n    token: " + token.toString() + "\n    event: " + event.toString());
		final VectorClock.Comparison comp = token.getCut().compareToClock(event.getVC());
		if (comp == VectorClock.Comparison.CONCURRENT || comp == VectorClock.Comparison.EQUAL) {
			evaluateToken(token, event);
		} else if (comp == VectorClock.Comparison.BIGGER) {
			synchronized (waitingTokens) {
				Log.d(LOG_TAG, "Waiting for next event.");
				waitingTokens.add(token.waitForNextEvent());
			}
		} else {
			final Map<Conjunct, Conjunct.Evaluation> conjunctsMap = token.getConjunctsMap();
			for (final Conjunct conjunct : conjunctsMap.keySet()) {
				conjunctsMap.put(conjunct, Conjunct.Evaluation.FALSE);
			}
			synchronized (history) {
				final Event targetEvent = history.get(token.getTargetEventId());
				final Token newToken = new Token.Builder(token).cut(targetEvent.getVC()).conjuncts(conjunctsMap).targetProcessState(targetEvent.getState()).build();
				TokenSender.sendTokenHome(newToken);
			}
		}
		Log.d(LOG_TAG, "Exiting processToken");
	}

	/*
	 * Evaluates each of token's predicates.
	 *
	 * @param token The token whose predicates will be evaluated.
	 * @param event The event to use to evaluate the token.
	 */
	public void evaluateToken(@NonNull final Token token, @NonNull final Event event) {
		Log.d(LOG_TAG, "Entering evaluateToken.\n    token: " + token.toString() + "\n    event: " + event.toString());
		token.evaluateConjuncts(event);
		if (token.anyConjunctSatisfied()) {
			final Token newToken = new Token.Builder(token).cut(event.getVC()).targetProcessState(event.getState()).build();
			TokenSender.sendTokenHome(newToken);
		} else {
			synchronized (history) {
				int nextEvent = token.getTargetEventId() + 1;
				if (history.containsKey(nextEvent)) {
					Log.d(LOG_TAG, "Processing token with next event.");
					processToken(token.waitForNextEvent(), history.get(nextEvent));
				} else {
					Log.d(LOG_TAG, "Adding a token to waitingTokens.");
					synchronized (waitingTokens) {
						waitingTokens.add(token.waitForNextEvent());
					}
				}
			}
		}
		Log.d(LOG_TAG, "Exiting evaluateToken");
	}

	/*
	 * Returns a list of global views that contain a copy of token.
	 *
	 * @param token The token to search for.
	 * @return A list of GlobalViews that have a copy of token.
	 */
	private List<GlobalView> getGlobalView(@NonNull final Token token) {
		final List<GlobalView> ret = new ArrayList<>();
		synchronized (GV) {
			for (final GlobalView gv : GV) {
				for (final Token t : gv.getTokens()) {
					if (token.getUniqueLocalIdentifier() == t.getUniqueLocalIdentifier()) {
						ret.add(gv);
						break;
					}
				}
			}
		}
		return ret;
	}
}
