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
	final int heartbeatIntervalInSeconds=10*1000;
	final int eventIntervalInSeconds=20*1000;
	Double x=new Double(0);
	final int  maxMessageSize=1024*5;
	long startTime;
	int simulationTimeInSeconds=120*1000; //2 mins
	public ProgramNode(int rank,int size) throws IOException, MPIException {
		this.rank=rank;
		this.size=size; 
		this.processID=rank+1;
		Log.fileName="P"+rank+"_";
		Map<Integer,Integer> consistentCut=  new HashMap<>();

		for (int i=1;i<=size;i++) {
			consistentCut.put(i, 0);
		}
		this.vectorClock=new VectorClock(consistentCut);
		//initialEvent
		this.vectorClock.incrementProcess(processID);
		sendEventToMonitor(MessageTags.Event);
		
	}
	public void start() throws IOException, MPIException, ClassNotFoundException, InterruptedException{
		
		Log.v("program","I am process P"+this.rank+"\n");
		startTime=System.currentTimeMillis();
		long endTime=startTime+simulationTimeInSeconds;
		long nextHeartBeat=System.currentTimeMillis()+heartbeatIntervalInSeconds;
		long nextEventUpdate=System.currentTimeMillis()+eventIntervalInSeconds;
		broadcast();
		while(true)
		{
			long currentTime=System.currentTimeMillis();
			if(currentTime>=nextHeartBeat)
			{
				
				broadcast();
				nextHeartBeat=System.currentTimeMillis()+heartbeatIntervalInSeconds;
				 
			}
			if(currentTime>=nextEventUpdate)
			{
				generateEvent();
				nextEventUpdate=System.currentTimeMillis()+eventIntervalInSeconds;
				
			}
			receiveHeartbeat();
			boolean finalStateFound=receiveMonitorFinalState();
			if(System.currentTimeMillis()>=endTime || finalStateFound)
			{
				Log.v("program", "Terminating program");
				this.vectorClock.incrementProcess(processID);
				sendEventToMonitor(MessageTags.ProgramTerminating);
				break;
			}
			
			Thread.sleep(5000);
		}
	}
	private void receiveHeartbeat() throws MPIException, ClassNotFoundException, IOException{

		Status status=MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MessageTags.HeartBeat.ordinal());
		if(status!=null && !status.isCancelled())
		{

			
			//java.nio.ByteBuffer  message= MPI.newByteBuffer(maxMessageSize);
			 byte[] message=new byte[maxMessageSize];

			MPI.COMM_WORLD.recv(message, maxMessageSize, MPI.BYTE,status.getSource(),MessageTags.HeartBeat.ordinal());
			
			Object o =CollectionUtils.deserializeObject(message);
			VectorClock otherVC = (VectorClock)o;
			this.vectorClock.incrementProcess(processID);
			this.vectorClock=this.vectorClock.merge(otherVC);
			Log.v("program", "Sending receive Event to monitor");
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
				Log.v("program","SATISFACTION");
				finalStateFound=true;
			}
			if(verdict[0]=='V')
			{
				// violation state
				Log.v("program","VIOLATION");
				finalStateFound=true;
			}
		}
		return finalStateFound;
	}
	private void broadcast() throws MPIException, IOException
	{
		Log.v("program", "Entering broadcast");
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
	private void generateEvent() throws IOException, MPIException{
		if(rank ==2 || rank==3) //p3.x=1 and p4.x=1
		{
				long currentTime= System.currentTimeMillis();
			if(currentTime-startTime>60*1000)
			{

				Log.v("program", "generating Event");
				 
				this.vectorClock.incrementProcess(processID);
				sendEventToMonitor(MessageTags.Event);
				Log.v("program", "flipping x");
				this.x=1.0;
			}
		}
	}
	private int getMonitorId(){
		return this.rank+this.size;
	}





	private void sendEventToMonitor( MessageTags tag) throws IOException, MPIException{
		Log.d("program", "Entering sendEventToMonitor: M"+  getMonitorId());
		Map<String,Double> valuation=  new HashMap<>();
		valuation.put("x"+(processID), x);
		Event e=new Event(this.vectorClock.process(processID), processID, EventType.SEND, new Valuation(valuation), this.vectorClock);
		byte[] eventBytes=CollectionUtils.serializeObject(e);
		java.nio.ByteBuffer out = MPI.newByteBuffer(eventBytes.length);
		Log.v("program", "SENDING sendEventToMonitor: message size: "+ eventBytes.length);
		for(int i = 0; i < eventBytes.length; i++){
			out.put(i, eventBytes[i]);
		}
		Request request=MPI.COMM_WORLD.iSend(out, eventBytes.length, MPI.BYTE, getMonitorId(), tag.ordinal());
		//request.waitFor();
		
		Log.d("program", "Exiting sendEventToMonitor: message size: "+ eventBytes.length);
	}


}
