package ca.mcmaster.capstone.program; 
import java.io.IOException; 
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sun.corba.se.impl.ior.ByteBuffer;

import mpi.*;
import ca.mcmaster.capstone.logger.Log;
import ca.mcmaster.capstone.monitoralgorithm.*;
import ca.mcmaster.capstone.monitoralgorithm.Event.EventType;
import ca.mcmaster.capstone.util.*;
public class ProgramNode {

	int rank,size;
	VectorClock vectorClock;
	int heartbeatIntervalInSeconds=3*1000;
	int eventIntervalInSeconds=5*1000;
	Double x=new Double(0);
	final int  maxMessageSize=100000;
	long startTime;
	int simulationTimeInSeconds=120*1000; //2 mins
	public ProgramNode(int rank,int size) throws IOException, MPIException {
		this.rank=rank;
		this.size=size; 
		Map<Integer,Integer> consistentCut=  new HashMap<>();

		for (int i=0;i<size;i++) {
			consistentCut.put(i, 0);
		}
		this.vectorClock=new VectorClock(consistentCut);
		//initialEvent
		this.vectorClock.incrementProcess(rank);
		sendEventToMonitor(MessageTags.Event);

	}
	public void start() throws IOException, MPIException, ClassNotFoundException{
		
		System.out.print("I am process P"+this.rank+"\n");
		startTime=System.currentTimeMillis();
		long endTime=startTime+simulationTimeInSeconds;
		long nextHeartBeat=System.currentTimeMillis()+heartbeatIntervalInSeconds;
		long nextEventUpdate=System.currentTimeMillis()+eventIntervalInSeconds;
		while(true)
		{
			long currentTime=System.currentTimeMillis();
			if(currentTime>=nextHeartBeat)
			{
				broadcast();
			}
			if(currentTime>=nextEventUpdate)
			{
				generateEvent();
				this.vectorClock.incrementProcess(rank);
				sendEventToMonitor(MessageTags.Event);
			}
			receiveHeartbeat();
			boolean finalStateFound=receiveMonitorFinalState();
			if(System.currentTimeMillis()>=endTime || finalStateFound)
			{
				this.vectorClock.incrementProcess(rank);
				sendEventToMonitor(MessageTags.ProgramTerminating);
				break;
			}
			nextHeartBeat=System.currentTimeMillis()+heartbeatIntervalInSeconds;
			nextEventUpdate=System.currentTimeMillis()+eventIntervalInSeconds;

		}
	}
	private void receiveHeartbeat() throws MPIException, ClassNotFoundException, IOException{

		Status status=MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MessageTags.HeartBeat.ordinal());
		if(status!=null && !status.isCancelled())
		{

			//byte[] message = new byte [maxMessageSize] ;
			java.nio.ByteBuffer message = MPI.newByteBuffer(maxMessageSize);

			MPI.COMM_WORLD.recv(message, maxMessageSize, MPI.BYTE,MPI.ANY_SOURCE,MessageTags.HeartBeat.ordinal());

			Object o =CollectionUtils.deserializeObject(message.array());
			VectorClock otherVC = (VectorClock)o;
			this.vectorClock.incrementProcess(rank);
			this.vectorClock.merge(otherVC);
			sendEventToMonitor(MessageTags.Event);
		}
	}

	private boolean receiveMonitorFinalState () throws MPIException, ClassNotFoundException, IOException{

		boolean finalStateFound=false;
		Status status=MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MessageTags.MonitorFinalState.ordinal());
		if(status!=null && !status.isCancelled())
		{

			byte[] message = new byte [1] ;

			MPI.COMM_WORLD.recv(message, 1, MPI.BYTE,MPI.ANY_SOURCE,MessageTags.HeartBeat.ordinal());

			Object o =CollectionUtils.deserializeObject(message);
			char []verdict = (char[])o;
			if(verdict[0]=='S')
			{
				// satisfaction state
				Log.d("SATISFACTION", "");
				finalStateFound=true;
			}
			if(verdict[0]=='V')
			{
				// violation state
				Log.d("VIOLATION", "");
				finalStateFound=true;
			}
		}
		return finalStateFound;
	}
	private void broadcast() throws MPIException, IOException
	{
		for(int i=0;i<size;i++)
		{
			if( i == rank)
			{
				continue;
			}
			byte[] vectorClockBytes=CollectionUtils.serializeObject(this.vectorClock);
			java.nio.ByteBuffer out = MPI.newByteBuffer(vectorClockBytes.length);

			for(int j = 0; j < vectorClockBytes.length; j++){
				out.put(j, vectorClockBytes[i]);
			}
			MPI.COMM_WORLD.iSend(MPI.slice(out,0), maxMessageSize, MPI.BYTE, i, MessageTags.HeartBeat.ordinal());
		} 
	}
	// implement here logic for program
	private void generateEvent(){
		if(rank ==2 || rank==3) //p3.x=1 and p4.x=1
		{
			long currentTime= System.currentTimeMillis();
			if(currentTime-startTime>60*1000)
			{
				this.x=1.0;
			}
		}
	}
	private int getMonitorId(){
		return this.rank+this.size;
	}





	private void sendEventToMonitor( MessageTags tag) throws IOException, MPIException{
		Map<String,Double> valuation=  new HashMap<>();
		valuation.put("x"+(this.rank+1), x);
		Event e=new Event(this.vectorClock.process(rank), rank, EventType.SEND, new Valuation(valuation), this.vectorClock);
		byte[] eventBytes=CollectionUtils.serializeObject(e);
		java.nio.ByteBuffer out = MPI.newByteBuffer(eventBytes.length);

		for(int i = 0; i < eventBytes.length; i++){
			out.put(i, eventBytes[i]);
		}
		MPI.COMM_WORLD.iSend(out, maxMessageSize, MPI.BYTE, getMonitorId(), tag.ordinal());

	}


}
