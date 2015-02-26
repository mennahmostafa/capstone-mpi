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

	int rank,size,processID;
	VectorClock vectorClock;
	int heartbeatIntervalInSeconds=3;
	int eventIntervalInSeconds=5;
	Double x=new Double(0);
	final int  maxMessageSize=1024*5*1000;
	long startTime;
	int simulationTimeInSeconds=120*1000; //2 mins
	public ProgramNode(int rank,int size) throws IOException, MPIException {
		this.rank=rank;
		this.size=size; 
		this.processID=rank+1;
		Map<Integer,Integer> consistentCut=  new HashMap<>();

		for (int i=1;i<=size;i++) {
			consistentCut.put(i, 0);
		}
		this.vectorClock=new VectorClock(consistentCut);
		//initialEvent
		this.vectorClock.incrementProcess(processID);
		sendEventToMonitor(MessageTags.Event);
		Log.fileName="P"+rank;
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
				Log.d("program", "\n\nBROADCASTINGGGGGGGGGGGGG\n");
				broadcast();
			}
			if(currentTime>=nextEventUpdate)
			{
				generateEvent();
				this.vectorClock.incrementProcess(processID);
				sendEventToMonitor(MessageTags.Event);
			}
			receiveHeartbeat();
			boolean finalStateFound=receiveMonitorFinalState();
			if(System.currentTimeMillis()>=endTime || finalStateFound)
			{
				this.vectorClock.incrementProcess(processID);
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

			Log.d("program", "Entering receiveHeartbeat");
			//java.nio.ByteBuffer  message= MPI.newByteBuffer(maxMessageSize);
			 byte[] message=new byte[maxMessageSize];

			MPI.COMM_WORLD.recv(message, maxMessageSize, MPI.BYTE,status.getSource(),MessageTags.HeartBeat.ordinal());
			//System.out.print("\n\n receiving a heartbeat\n\n" + message.toString());
			Object o =CollectionUtils.deserializeObject(message);
			System.out.print("\n\n deserializing object\n\n");
			VectorClock otherVC = (VectorClock)o;
			this.vectorClock.incrementProcess(processID);
			this.vectorClock.merge(otherVC);
			sendEventToMonitor(MessageTags.Event);
			Log.d("program", "Exiting receiveHeartbeat");
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
		Log.d("program", "Entering broadcast");
		for(int i=0;i<size;i++)
		{
			if( i == rank)
			{
				continue;
			}
			byte[] vectorClockBytes=CollectionUtils.serializeObject(this.vectorClock);
			java.nio.ByteBuffer out = MPI.newByteBuffer(vectorClockBytes.length);

			for(int j = 0; j < vectorClockBytes.length; j++){
				out.put(j, vectorClockBytes[j]);
			}
			MPI.COMM_WORLD.iSend(MPI.slice(out,0), vectorClockBytes.length, MPI.BYTE, i, MessageTags.HeartBeat.ordinal());
		} 
		Log.d("program", "Entering broadcast");
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
		Log.d("program", "Entering sendEventToMonitor");
		Map<String,Double> valuation=  new HashMap<>();
		valuation.put("x"+(this.rank+1), x);
		Event e=new Event(this.vectorClock.process(rank+1), rank+1, EventType.SEND, new Valuation(valuation), this.vectorClock);
		byte[] eventBytes=CollectionUtils.serializeObject(e);
		java.nio.ByteBuffer out = MPI.newByteBuffer(eventBytes.length);

		for(int i = 0; i < eventBytes.length; i++){
			out.put(i, eventBytes[i]);
		}
		MPI.COMM_WORLD.iSend(out, maxMessageSize, MPI.BYTE, getMonitorId(), tag.ordinal());
		Log.d("program", "Exiting sendEventToMonitor");
	}


}
