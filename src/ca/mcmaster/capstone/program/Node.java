package ca.mcmaster.capstone.program;
import java.io.IOException;

import mpi.*;
import ca.mcmaster.capstone.logger.Log;
import ca.mcmaster.capstone.monitoralgorithm.*;
public class Node {

	public enum NodeType{monitor,program};
	public static int rank,size;
	public static NodeType type;
	/**
	 * @param args
	 * @throws MPIException 
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws MPIException, ClassNotFoundException, IOException, InterruptedException {
	 
		// 1- initialise mpi 
		initializeMPI(args);
		// 2- initialise node (monitor or program)
		if(rank<(size/2.0)){ //program node
		
			
			type=NodeType.program;
			ProgramNode program=new ProgramNode(rank, size/2);
			program.start();
		}
		else{ //monitor node

			type=NodeType.monitor;
			Monitor monitor= new Monitor(rank-(size/2),size/2); //pass rank,size params, mpi already static
			 
			monitor.monitorLoop();
		}
			
		// 3- start producing events and waiting to receive messages from other nodes
		MPI.COMM_WORLD.barrier();
		MPI.Finalize();
		Log.d("NODE", "MPI FINALIZED!!");
	}
	
	public static void initializeMPI(String[] args) throws MPIException {
		MPI.Init(args);

		rank = MPI.COMM_WORLD.getRank();
		size = MPI.COMM_WORLD.getSize();
	}

}
