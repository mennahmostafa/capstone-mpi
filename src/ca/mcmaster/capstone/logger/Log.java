package ca.mcmaster.capstone.logger;
import java.util.logging.*;

import sun.util.logging.PlatformLogger;

import com.sun.javafx.binding.Logging;

public class Log {

	public Log() {
		// TODO Auto-generated constructor stub
	}
	public static void d(String tag,String message){
	PlatformLogger logger=Logging.getLogger();
	logger.severe(tag+" : "+message);
		
	}
public static void v(String tag,String message){
	PlatformLogger logger=Logging.getLogger();
	logger.severe(tag+" : "+message);
		
	}
}
